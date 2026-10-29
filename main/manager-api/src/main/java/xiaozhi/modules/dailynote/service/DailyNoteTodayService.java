package xiaozhi.modules.dailynote.service;

import xiaozhi.modules.dailynote.vo.DailyNoteTodayVO;

/**
 * 当日小纸条查询 Service（应用端）。
 * 日期由服务器按 Asia/Shanghai 决定，客户端不传日期；
 * 当日小纸条与合格游戏（启用且入口含 NOTE）任一缺失时返回 null，
 * 由上层转为 code=0、data=null 的成功空响应。
 */
public interface DailyNoteTodayService {

    /**
     * 查询服务器"当天"的小纸条与一个随机合格游戏。
     *
     * @param userId 当前认证用户 ID（来自 Token）；
     *               已打开状态按用户+完整日期过滤，已打开当日内容的用户返回 null
     * @return 当日内容与随机游戏；当日无小纸条、用户已打开或无合格游戏时返回 null
     */
    DailyNoteTodayVO today(Long userId);
}
