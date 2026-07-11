package xiaozhi.modules.wechat.util;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.wechat.dto.WechatProfileUpdateDTO;

/**
 * 用户资料轻量校验工具
 */
public final class ProfileValidator {

    private static final Set<String> MBTI_SET = Set.of(
            "INFP", "INFJ", "INTJ", "INTP", "ENFP", "ENFJ", "ENTJ", "ENTP",
            "ISFP", "ISFJ", "ISTJ", "ISTP", "ESFP", "ESFJ", "ESTJ", "ESTP");
    private static final Set<String> GENDER_SET = Set.of("MALE", "FEMALE", "OTHER");
    private static final Set<String> SENSITIVE = Set.of("违法", "诈骗", "赌博", "色情", "暴力");

    private ProfileValidator() {
    }

    public static void validate(WechatProfileUpdateDTO dto) {
        if (dto == null) {
            throw new RenException(ErrorCode.NOT_NULL);
        }
        if (StringUtils.isNotBlank(dto.getNickname())) {
            validateNickname(dto.getNickname());
        }
        if (StringUtils.isNotBlank(dto.getGender())) {
            validateGender(dto.getGender());
        }
        if (StringUtils.isNotBlank(dto.getMbti())) {
            validateMbti(dto.getMbti());
        }
        if (StringUtils.isNotBlank(dto.getCity())) {
            validateCity(dto.getCity());
        }
    }

    private static void validateNickname(String nickname) {
        if (nickname.length() > 16) {
            throw new RenException(ErrorCode.NICKNAME_TOO_LONG);
        }
        for (String word : SENSITIVE) {
            if (nickname.contains(word)) {
                throw new RenException(ErrorCode.NICKNAME_SENSITIVE);
            }
        }
    }

    private static void validateGender(String gender) {
        if (!GENDER_SET.contains(gender)) {
            throw new RenException(ErrorCode.INVALID_GENDER);
        }
    }

    private static void validateMbti(String mbti) {
        if (!MBTI_SET.contains(mbti)) {
            throw new RenException(ErrorCode.INVALID_MBTI);
        }
    }

    private static void validateCity(String city) {
        if (city.length() > 32) {
            throw new RenException(ErrorCode.CITY_TOO_LONG);
        }
    }
}
