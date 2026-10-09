package xiaozhi.modules.star.enums;

/**
 * 星星消费场景枚举。流水 biz_type 存 code 字符串。
 * 本模块只管资金变动；TRAVEL 等场景的履约语义由调用方业务对象承载（ADR 0009）。
 */
public enum StarConsumeBizType {

    /** 兑换 */
    EXCHANGE("exchange"),
    /** 抽奖 */
    LOTTERY("lottery");

    private final String code;

    StarConsumeBizType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
