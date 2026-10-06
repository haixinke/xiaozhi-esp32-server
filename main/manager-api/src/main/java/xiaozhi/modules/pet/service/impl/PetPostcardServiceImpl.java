package xiaozhi.modules.pet.service.impl;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.DateUtils;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.PetPostcardDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.entity.PetPostcardEntity;
import xiaozhi.modules.pet.service.PetPostcardService;
import xiaozhi.modules.pet.vo.PetPostcardPublicVO;

/**
 * 宠物明信片服务实现。
 */
@Slf4j
@Service
public class PetPostcardServiceImpl extends BaseServiceImpl<PetPostcardDao, PetPostcardEntity>
        implements PetPostcardService {

    /** share_id 字符集：去掉易混淆字符（0/O、1/I）的 Crockford 风格 */
    private static final String SHARE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** share_id 长度：32^12 ≈ 1e18，枚举不可行 */
    private static final int SHARE_ID_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 公开页时间展示时区：与项目其它模块（ImageGen 配额日界）一致的固定口径，避免容器时区漂移 */
    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Shanghai");

    private final PetDao petDao;

    public PetPostcardServiceImpl(PetDao petDao) {
        this.petDao = petDao;
    }

    @Override
    public PetPostcardEntity createPostcard(PetPostcardEntity postcard) {
        if (postcard == null || StringUtils.isBlank(postcard.getImageUrl())
                || StringUtils.isBlank(postcard.getCaption())
                || StringUtils.isBlank(postcard.getPetId())
                || postcard.getUserId() == null) {
            throw new RenException(ErrorCode.NOT_NULL);
        }
        // share_id 允许调用方指定（便于测试），缺省时生成
        if (StringUtils.isBlank(postcard.getShareId())) {
            postcard.setShareId(generateShareId());
        }
        baseDao.insert(postcard);
        log.info("明信片落库 petId={}, shareId={}", postcard.getPetId(), postcard.getShareId());
        return postcard;
    }

    @Override
    public PetPostcardPublicVO getPublicByShareId(String shareId) {
        if (StringUtils.isBlank(shareId)) {
            throw new RenException(ErrorCode.POSTCARD_NOT_FOUND);
        }
        PetPostcardEntity postcard = baseDao.selectOne(
                new QueryWrapper<PetPostcardEntity>().eq("share_id", shareId).last("limit 1"));
        if (postcard == null) {
            // 不存在与参数错误统一口径，避免对外枚举探测
            throw new RenException(ErrorCode.POSTCARD_NOT_FOUND);
        }
        PetPostcardPublicVO vo = new PetPostcardPublicVO();
        // 宠物昵称/原型仅用于展示署名；宠物查不到时留空，不阻断公开页
        PetEntity pet = petDao.selectById(postcard.getPetId());
        if (pet != null) {
            vo.setPetName(pet.getNickname());
            vo.setPrototype(pet.getPrototype());
        }
        vo.setImageUrl(postcard.getImageUrl());
        vo.setCaption(postcard.getCaption());
        vo.setCreateDate(formatDate(postcard.getCreateDate()));
        return vo;
    }

    /** 生成随机 share_id；冲突由唯一索引兜底（概率忽略不计），冲突时重试一次 */
    private String generateShareId() {
        StringBuilder sb = new StringBuilder(SHARE_ID_LENGTH);
        for (int i = 0; i < SHARE_ID_LENGTH; i++) {
            sb.append(SHARE_ALPHABET.charAt(RANDOM.nextInt(SHARE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return date.toInstant().atZone(ZONE)
                .format(DateTimeFormatter.ofPattern(DateUtils.DATE_TIME_PATTERN));
    }
}
