package xiaozhi.modules.pet.service.impl;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.aliyun.oss.model.CannedAccessControlList;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.oss.OssService;
import xiaozhi.modules.pet.config.SeedreamProperties;
import xiaozhi.modules.pet.constant.ImageGenTaskStatus;
import xiaozhi.modules.pet.dao.ImageGenTaskDao;
import xiaozhi.modules.pet.entity.ImageGenTaskEntity;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.wechat.service.WechatMediaCheckService;

/**
 * AI生图异步执行器。
 *
 * <p>由 {@link ImageGenTaskServiceImpl} 在照片审核通过后触发（RUNNING 态）：
 * Seedream 多图参考生成 → 结果图下载上传 OSS → 提交结果图内容审核（REVIEWING 态）。
 * 独立成 Bean 是为了让 {@code @Async} 经代理生效（自调用不会异步）。
 */
@Slf4j
@Component
public class ImageGenExecutor {

    /**
     * prompt 模板：照片仅作场景/氛围参考，不保留真实人物面部（方舟防深伪护栏要求，已实测验证）；
     * IP 形象一致性 + 预置文案图内渲染。Spike 样张见 docs/image-gen/implementation-plan.md。
     */
    private static final String PROMPT_TEMPLATE = """
            以第二张图片中的卡通宠物IP形象为绝对主角，形象特征必须与参考图保持完全一致；\
            将第一张用户随手拍的照片作为场景与氛围参考，把IP角色自然融入该场景中，画面温馨可爱、构图精美；\
            画面中艺术化地渲染中文文字「%s」。\
            注意：不保留照片中任何真实人物的面部或身份特征，人物若存在则虚化为背景氛围。""";

    private final ImageGenTaskDao imageGenTaskDao;
    private final WechatUserDao wechatUserDao;
    private final SeedreamProperties seedreamProperties;
    private final ObjectProvider<ArkService> arkServiceProvider;
    private final RestTemplate restTemplate;
    private final OssService ossService;
    private final WechatMediaCheckService mediaCheckService;

    public ImageGenExecutor(ImageGenTaskDao imageGenTaskDao,
            WechatUserDao wechatUserDao,
            SeedreamProperties seedreamProperties,
            ObjectProvider<ArkService> arkServiceProvider,
            RestTemplate restTemplate,
            OssService ossService,
            WechatMediaCheckService mediaCheckService) {
        this.imageGenTaskDao = imageGenTaskDao;
        this.wechatUserDao = wechatUserDao;
        this.seedreamProperties = seedreamProperties;
        this.arkServiceProvider = arkServiceProvider;
        this.restTemplate = restTemplate;
        this.ossService = ossService;
        this.mediaCheckService = mediaCheckService;
    }

    @Async("taskExecutor")
    public void generateAsync(Long taskId) {
        generate(taskId);
    }

    /**
     * 执行生成。任何失败都把任务推进 FAILED（fail_reason 用户可读），不抛异常。
     */
    void generate(Long taskId) {
        ImageGenTaskEntity task = imageGenTaskDao.selectById(taskId);
        if (task == null || !ImageGenTaskStatus.RUNNING.name().equals(task.getStatus())) {
            log.info("AI生图任务不在生成态，跳过 taskId={}", taskId);
            return;
        }

        ArkService arkService = arkServiceProvider.getIfAvailable();
        if (arkService == null || !seedreamProperties.isConfigured() || !ossService.isEnabled()) {
            failTask(taskId, "生成服务暂不可用，请稍后再试");
            return;
        }

        try {
            String generatedUrl = callSeedream(arkService, task);
            if (StringUtils.isBlank(generatedUrl)) {
                failTask(taskId, "生成失败，请重试");
                return;
            }
            byte[] imageBytes = downloadImage(generatedUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                failTask(taskId, "生成失败，请重试");
                return;
            }

            String ossKey = "ai-gen/" + task.getUserId() + "/" + task.getId() + ".png";
            ossService.upload(ossKey, imageBytes, CannedAccessControlList.PublicRead);
            String resultUrl = ossService.buildPublicUrl(ossKey);

            String openid = openidOf(task.getUserId());
            if (openid == null) {
                failTask(taskId, "生成失败，请重试");
                return;
            }
            String resultTraceId = mediaCheckService.mediaCheckAsync(resultUrl, openid);

            // 条件更新：清理任务可能已将其置 FAILED，仅在仍 RUNNING 时推进
            int rows = imageGenTaskDao.update(null, new UpdateWrapper<ImageGenTaskEntity>()
                    .eq("id", taskId)
                    .eq("status", ImageGenTaskStatus.RUNNING.name())
                    .set("status", ImageGenTaskStatus.REVIEWING.name())
                    .set("result_url", resultUrl)
                    .set("result_trace_id", resultTraceId));
            if (rows == 0) {
                log.info("AI生图任务状态已迁移，跳过推进 taskId={}", taskId);
                return;
            }
            log.info("AI生图生成完成待审核 taskId={}, resultUrl={}", taskId, resultUrl);
        } catch (Exception e) {
            log.error("AI生图执行异常 taskId={}", taskId, e);
            failTask(taskId, "生成失败，请重试");
        }
    }

    private String callSeedream(ArkService arkService, ImageGenTaskEntity task) {
        String prompt = String.format(PROMPT_TEMPLATE, task.getCaption());
        GenerateImagesRequest request = GenerateImagesRequest.builder()
                .model(seedreamProperties.getModel())
                .prompt(prompt)
                // 多图参考：第一张用户照片（场景/氛围），第二张 IP 参考图（形象一致性）
                .image(List.of(task.getPhotoUrl(), task.getIpImageUrl()))
                .responseFormat(ResponseFormat.Url)
                .size(seedreamProperties.getSize())
                .watermark(seedreamProperties.isWatermark())
                .build();
        ImagesResponse response = arkService.generateImages(request);
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            log.warn("Seedream 响应为空 taskId={}", task.getId());
            return null;
        }
        return response.getData().get(0).getUrl();
    }

    private byte[] downloadImage(String imageUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.APPLICATION_OCTET_STREAM));
        ResponseEntity<byte[]> response = restTemplate.exchange(
                URI.create(imageUrl), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Seedream 图片下载失败 status={}", response.getStatusCode());
            return null;
        }
        return response.getBody();
    }

    private String openidOf(Long userId) {
        WechatUserEntity wechatUser = wechatUserDao.selectOne(
                new QueryWrapper<WechatUserEntity>().eq("user_id", userId).last("limit 1"));
        if (wechatUser == null || StringUtils.isBlank(wechatUser.getOpenid())) {
            log.warn("AI生图用户无openid userId={}", userId);
            return null;
        }
        return wechatUser.getOpenid();
    }

    /** 条件更新为 FAILED：仅在非终态时生效，避免覆盖并发回调的结果 */
    private void failTask(Long taskId, String failReason) {
        int rows = imageGenTaskDao.update(null, new UpdateWrapper<ImageGenTaskEntity>()
                .eq("id", taskId)
                .in("status", ImageGenTaskStatus.RUNNING.name(), ImageGenTaskStatus.REVIEWING.name())
                .set("status", ImageGenTaskStatus.FAILED.name())
                .set("fail_reason", failReason));
        if (rows > 0) {
            log.info("AI生图任务失败 taskId={}, reason={}", taskId, failReason);
        }
    }
}
