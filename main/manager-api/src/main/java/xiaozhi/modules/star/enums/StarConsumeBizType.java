package xiaozhi.modules.star.enums;

/**
 * 星星消费场景枚举。流水 biz_type 存 code 字符串。
 * TRAVEL 特殊：购买的是「旅行日记获取权」而非旅行本身——宠物是否旅行由故事引擎按原型自主驱动；
 * travel 消费写入 pending 履约状态，旅行结束时由引擎消耗预订并回填日记ID。
 */
public enum StarConsumeBizType {

    /** 兑换 */
    EXCHANGE("exchange", false),
    /** 抽奖 */
    LOTTERY("lottery", false),
    /** 旅行日记预订(异步履约,需指定宠物原型) */
    TRAVEL("travel", true);

    private final String code;

    /** 是否需要异步履约(写入时为 pending) */
    private final boolean asyncFulfill;

    StarConsumeBizType(String code, boolean asyncFulfill) {
        this.code = code;
        this.asyncFulfill = asyncFulfill;
    }

    public String getCode() {
        return code;
    }

    public boolean isAsyncFulfill() {
        return asyncFulfill;
    }
}
