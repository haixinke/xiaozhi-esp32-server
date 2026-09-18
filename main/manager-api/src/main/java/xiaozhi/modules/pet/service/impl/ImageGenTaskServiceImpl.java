package xiaozhi.modules.pet.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.upload.UploadScene;
import xiaozhi.modules.pet.constant.ImageGenTaskStatus;
import xiaozhi.modules.pet.dao.ImageGenTaskDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.ImageGenTaskEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.ImageGenTaskService;
import xiaozhi.modules.pet.vo.ImageGenTaskVO;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.wechat.service.WechatMediaCheckService;

/**
 * AI生图任务服务实现。
 */
@Slf4j
@Service
public class ImageGenTaskServiceImpl extends BaseServiceImpl<ImageGenTaskDao, ImageGenTaskEntity>
        implements ImageGenTaskService {

    private static final String PROTOTYPE_KOI = "锦鲤";
    private static final String PROTOTYPE_RABBIT = "玉兔";

    /** IP 参考图（按原型固定单图，与头像池是两套用途，见 CONTEXT.md「IP 参考图」） */
    private static final Map<String, String> IP_REFERENCE_IMAGE = Map.of(
            PROTOTYPE_KOI, "https://oss.eggbabe.com/default-ip/fish/fish.png",
            PROTOTYPE_RABBIT, "https://oss.eggbabe.com/default-ip/rabbit/rabbit.png");

    /** 预置文案池：渲染进图片内，同时用作分享卡片标题；用户不可编辑 */
    private static final Map<String, List<String>> CAPTION_POOL = Map.of(
            PROTOTYPE_KOI, List.of("好运连连", "锦鲤附体 诸事顺利", "摸鱼也能赢"),
            PROTOTYPE_RABBIT, List.of("玉兔呈祥", "月宫来的小可爱", "温柔有光"));

    private static final String DEFAULT_OSS_PUBLIC_URL = "https://oss.eggbabe.com";

    /** 与 OSS 默认域名一致的兜底，仅在配置缺失时使用 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PetDao petDao;
    private final WechatUserDao wechatUserDao;
    private final WechatMediaCheckService mediaCheckService;
    private final AliyunOssProperties ossProperties;
    private final ImageGenExecutor imageGenExecutor;

    /** 每日成功次数上限（失败/驳回不计次），默认 3 */
    @Value("${pet.image-gen.daily-limit:3}")
    private int dailyLimit;

    public ImageGenTaskServiceImpl(PetDao petDao,
            WechatUserDao wechatUserDao,
            WechatMediaCheckService mediaCheckService,
            AliyunOssProperties ossProperties,
            ImageGenExecutor imageGenExecutor) {
        this.petDao = petDao;
        this.wechatUserDao = wechatUserDao;
        this.mediaCheckService = mediaCheckService;
        this.ossProperties = ossProperties;
        this.imageGenExecutor = imageGenExecutor;
    }

    @Override
    public ImageGenTaskVO createTask(Long userId, String photoUrl) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        if (StringUtils.isBlank(photoUrl) || !photoUrl.startsWith(photoUrlPrefix())) {
            throw new RenException(ErrorCode.IMAGE_GEN_PHOTO_URL_INVALID);
        }

        PetEntity pet = petDao.selectOne(
                new QueryWrapper<PetEntity>().eq("user_id", userId).last("limit 1"));
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (todayCountedCount(userId) >= dailyLimit) {
            throw new RenException(ErrorCode.IMAGE_GEN_QUOTA_EXCEEDED);
        }
        String openid = openidOf(userId);

        ImageGenTaskEntity task = new ImageGenTaskEntity();
        task.setUserId(userId);
        task.setPetId(pet.getId());
        task.setPhotoUrl(photoUrl);
        task.setIpImageUrl(resolveIpImageUrl(pet.getPrototype()));
        task.setCaption(drawCaption(pet.getPrototype()));
        task.setStatus(ImageGenTaskStatus.PENDING.name());
        task.setCounted(0);

        // 先提交照片审核拿到 trace_id 再落库，提交失败则整个请求失败、不留半成品任务
        String traceId = mediaCheckService.mediaCheckAsync(photoUrl, openid);
        task.setPhotoTraceId(traceId);

        baseDao.insert(task);
        log.info("AI生图任务创建成功 userId={}, taskId={}, prototype={}", userId, task.getId(), pet.getPrototype());
        return toVO(task);
    }

    @Override
    public ImageGenTaskVO getTask(Long userId, Long taskId) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        ImageGenTaskEntity task = baseDao.selectOne(
                new QueryWrapper<ImageGenTaskEntity>()
                        .eq("id", taskId)
                        .eq("user_id", userId)
                        .last("limit 1"));
        if (task == null) {
            throw new RenException(ErrorCode.IMAGE_GEN_TASK_NOT_FOUND);
        }
        return toVO(task);
    }

    @Override
    public void handleMediaCheckResult(String traceId, boolean pass) {
        if (StringUtils.isBlank(traceId)) {
            return;
        }
        // 先按照片审核 trace 路由
        ImageGenTaskEntity byPhoto = baseDao.selectOne(
                new QueryWrapper<ImageGenTaskEntity>().eq("photo_trace_id", traceId).last("limit 1"));
        if (byPhoto != null) {
            handlePhotoCheckResult(byPhoto, pass);
            return;
        }
        // 再按结果图审核 trace 路由
        ImageGenTaskEntity byResult = baseDao.selectOne(
                new QueryWrapper<ImageGenTaskEntity>().eq("result_trace_id", traceId).last("limit 1"));
        if (byResult != null) {
            handleResultCheckResult(byResult, pass);
            return;
        }
        log.warn("mediaCheck回调未匹配到任务 traceId={}", traceId);
    }

    /**
     * 照片审核结论：通过→RUNNING 并触发生成；驳回→FAILED（不计配额）。
     * 条件更新保证幂等：微信重推或状态已迁移时受影响行数为 0，直接跳过。
     */
    private void handlePhotoCheckResult(ImageGenTaskEntity task, boolean pass) {
        UpdateWrapper<ImageGenTaskEntity> wrapper = new UpdateWrapper<ImageGenTaskEntity>()
                .eq("id", task.getId())
                .eq("status", ImageGenTaskStatus.PENDING.name());
        if (pass) {
            wrapper.set("status", ImageGenTaskStatus.RUNNING.name());
        } else {
            wrapper.set("status", ImageGenTaskStatus.FAILED.name())
                    .set("fail_reason", "图片未通过审核，换一张试试");
        }
        int rows = baseDao.update(null, wrapper);
        if (rows == 0) {
            log.info("照片审核回调幂等跳过 taskId={}, pass={}", task.getId(), pass);
            return;
        }
        if (pass) {
            imageGenExecutor.generateAsync(task.getId());
        } else {
            log.info("AI生图照片审核驳回 taskId={}", task.getId());
        }
    }

    /**
     * 结果图审核结论：通过→SUCCEEDED 且计入配额；驳回→FAILED。
     */
    private void handleResultCheckResult(ImageGenTaskEntity task, boolean pass) {
        UpdateWrapper<ImageGenTaskEntity> wrapper = new UpdateWrapper<ImageGenTaskEntity>()
                .eq("id", task.getId())
                .eq("status", ImageGenTaskStatus.REVIEWING.name());
        if (pass) {
            wrapper.set("status", ImageGenTaskStatus.SUCCEEDED.name())
                    .set("counted", 1);
        } else {
            wrapper.set("status", ImageGenTaskStatus.FAILED.name())
                    .set("fail_reason", "生成内容未通过审核，请重试");
        }
        int rows = baseDao.update(null, wrapper);
        if (rows == 0) {
            log.info("结果图审核回调幂等跳过 taskId={}, pass={}", task.getId(), pass);
            return;
        }
        log.info("AI生图结果图审核{} taskId={}", pass ? "通过" : "驳回", task.getId());
    }

    @Override
    public int cleanupStaleTasks(Duration maxAge) {
        Date cutoff = Date.from(java.time.Instant.now().minus(maxAge));
        // 数据库层单条批量更新，符合定时任务大数据处理铁律
        int rows = baseDao.update(null, new UpdateWrapper<ImageGenTaskEntity>()
                .in("status", ImageGenTaskStatus.PENDING.name(), ImageGenTaskStatus.RUNNING.name(),
                        ImageGenTaskStatus.REVIEWING.name())
                .lt("create_date", cutoff)
                .set("status", ImageGenTaskStatus.FAILED.name())
                .set("fail_reason", "任务超时未完成"));
        if (rows > 0) {
            log.info("AI生图超时任务清理完成 count={}", rows);
        }
        return rows;
    }

    /** 当日已计配额的次数（仅成功任务计次） */
    private long todayCountedCount(Long userId) {
        Date dayStart = Date.from(LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant());
        Long count = baseDao.selectCount(new QueryWrapper<ImageGenTaskEntity>()
                .eq("user_id", userId)
                .eq("counted", 1)
                .ge("create_date", dayStart));
        return count == null ? 0 : count;
    }

    private String openidOf(Long userId) {
        WechatUserEntity wechatUser = wechatUserDao.selectOne(
                new QueryWrapper<WechatUserEntity>().eq("user_id", userId).last("limit 1"));
        if (wechatUser == null || StringUtils.isBlank(wechatUser.getOpenid())) {
            throw new RenException("当前账号不是微信登录账号");
        }
        return wechatUser.getOpenid();
    }

    /** 用户照片 URL 白名单前缀：本 bucket 的 ai-gen 场景目录 */
    private String photoUrlPrefix() {
        String base = ossProperties.getPublicUrl();
        if (StringUtils.isBlank(base)) {
            base = DEFAULT_OSS_PUBLIC_URL;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + UploadScene.AI_GEN.getPathPrefix() + "/";
    }

    private static String resolveIpImageUrl(String prototype) {
        return IP_REFERENCE_IMAGE.getOrDefault(prototype, IP_REFERENCE_IMAGE.get(PROTOTYPE_DEFAULT));
    }

    /** 未知原型兜底用玉兔（与收藏卡参考图未知原型不用图的策略不同：本功能必须有参考图） */
    private static final String PROTOTYPE_DEFAULT = PROTOTYPE_RABBIT;

    private static String drawCaption(String prototype) {
        List<String> pool = CAPTION_POOL.getOrDefault(prototype, CAPTION_POOL.get(PROTOTYPE_DEFAULT));
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static ImageGenTaskVO toVO(ImageGenTaskEntity task) {
        ImageGenTaskVO vo = new ImageGenTaskVO();
        vo.setTaskId(String.valueOf(task.getId()));
        vo.setStatus(task.getStatus());
        // 结果图 URL 仅在审核通过（SUCCEEDED）后下发，避免 REVIEWING 态泄露未过审图片
        if (ImageGenTaskStatus.SUCCEEDED.name().equals(task.getStatus())) {
            vo.setResultUrl(task.getResultUrl());
        }
        vo.setFailReason(task.getFailReason());
        vo.setCaption(task.getCaption());
        return vo;
    }
}
