package xiaozhi.modules.star.enums;

/**
 * 星星赚取渠道枚举。流水 biz_type 存 code 字符串。
 * 本期只建枚举，具体触发器（签到逻辑等）由业务模块后续接入。
 */
public enum StarEarnBizType {

    /** 签到 */
    SIGN_IN("sign_in"),
    /** 广告奖励 */
    AD_REWARD("ad_reward"),
    /** 邀请奖励 */
    INVITE("invite"),
    /** 活动奖励 */
    ACTIVITY("activity"),
    /** 后台补发(运营人工) */
    ADMIN_GRANT("admin_grant");

    private final String code;

    StarEarnBizType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
