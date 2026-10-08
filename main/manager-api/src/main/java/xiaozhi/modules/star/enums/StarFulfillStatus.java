package xiaozhi.modules.star.enums;

/**
 * 流水履约状态。即时场景写入即 FULFILLED；旅行预订写入 PENDING，
 * 故事引擎履约(生成旅行日记)后转 FULFILLED 并回填履约产物ID。
 */
public enum StarFulfillStatus {

    /** 待履约(仅 travel 预订) */
    PENDING("pending"),
    /** 已履约 */
    FULFILLED("fulfilled");

    private final String code;

    StarFulfillStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
