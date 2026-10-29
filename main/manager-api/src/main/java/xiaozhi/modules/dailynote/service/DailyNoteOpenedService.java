package xiaozhi.modules.dailynote.service;

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
     */
    void markOpened(Long userId);
}
