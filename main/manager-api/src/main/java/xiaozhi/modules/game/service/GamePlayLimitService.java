package xiaozhi.modules.game.service;

/** 游戏用户级每日局次门槛；写入方须在同一事务先锁用户再检查并记录局次。 */
public interface GamePlayLimitService {
    /** 按游戏管理当前配置检查北京时间今日额度，0 表示不限。 */
    void checkDailyLimit(Long userId, String gameType);
}
