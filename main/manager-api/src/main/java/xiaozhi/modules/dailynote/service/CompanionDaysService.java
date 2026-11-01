package xiaozhi.modules.dailynote.service;

/**
 * 连续陪伴天数查询（应用端）。
 * 按「小纸条已打开」记录现算、不落库：用户级归属，与「小纸条连续奖励」共享数据源但定义独立。
 * 断档语义锚定"截至昨天"：今天已打开则从今天往回数连续日期；今天未打开则从昨天往回数，
 * 昨天无记录即为 0——断档在当天结束才生效，白天未打开仍显示昨天的连续数。
 */
public interface CompanionDaysService {

    /**
     * 计算用户当前的连续陪伴天数。
     *
     * @param userId 用户 ID（服务端可信身份）
     * @return 连续天数；无任何打开记录或最近连续段已在昨天之前断掉时为 0
     */
    int companionDays(Long userId);
}
