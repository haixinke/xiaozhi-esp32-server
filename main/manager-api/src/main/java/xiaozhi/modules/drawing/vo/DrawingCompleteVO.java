package xiaozhi.modules.drawing.vo;

import java.util.Date;

/** 本局结算响应；奖励取实际结算事实，余额实时查询，重试不会再次入账。 */
public record DrawingCompleteVO(String sessionId, long awardedStars, String rewardStatus,
        long balance, Date completedAt) { }
