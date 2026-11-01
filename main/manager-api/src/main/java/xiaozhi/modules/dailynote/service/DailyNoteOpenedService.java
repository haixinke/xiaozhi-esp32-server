package xiaozhi.modules.dailynote.service;

import xiaozhi.modules.star.vo.DailyRewardResult;

/**
 * 用户已打开记录 Service（应用端）。
 * "当天"由服务器按 Asia/Shanghai 决定，用户 ID 只来自认证 Token；
 * 已打开记录按用户+完整日期隔离，永久保留，内容维护（覆盖/删除/重建）不影响。
 */
public interface DailyNoteOpenedService {

    /**
     * 标记当前用户已打开当日小纸条。
     * 仅当当日小纸条存在时写入记录（此时不要求游戏可用）；
     * 当日无小纸条且无记录时不写入，但接口语义仍为成功（提前标记不隐藏后续补录内容）。
     * 重复或并发上报幂等：成功且不改变首次打开时间，唯一键冲突视为成功。
     *
     * @param userId 当前认证用户 ID（来自 Token）
     * @return 本次奖励结算结果：首次有效打开发奖时 awarded=true 且 amount 为实发星星数；
     *         当天已打开、当日无内容、并发冲突三个非发奖分支统一返回 awarded=false、amount=0
     *         （不多查历史金额快照，调用方不得据此补发展示）
     */
    DailyRewardResult markOpened(Long userId);
}
