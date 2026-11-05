package xiaozhi.modules.game.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.service.GamePlayLimitService;
import xiaozhi.modules.listen.dao.ListenAnswerDao;

/** 统一读取运营配置的游戏每日局次限额。 */
@Service
public class GamePlayLimitServiceImpl implements GamePlayLimitService {
    private final GameDao games;
    private final DrawingSessionDao drawings;
    private final ListenAnswerDao answers;
    private final Clock clock;

    public GamePlayLimitServiceImpl(GameDao games, DrawingSessionDao drawings, ListenAnswerDao answers,
            @Qualifier("dailyNoteClock") Clock clock) {
        this.games = games;
        this.drawings = drawings;
        this.answers = answers;
        this.clock = clock.withZone(ZoneId.of("Asia/Shanghai"));
    }

    @Override
    public void checkDailyLimit(Long userId, String gameType) {
        var game = games.selectByGameType(gameType);
        if (game == null) throw new RenException(ErrorCode.GAME_NOT_FOUND);
        int limit = game.getDailyLimit() == null ? 0 : game.getDailyLimit();
        if (limit == 0) return;
        LocalDate day = LocalDate.now(clock);
        // 只支持已接入的游戏：计数事实来自各自业务记录，不使用每日奖励记录代替。
        long count;
        int errorCode;
        switch (gameType) {
            case "DRAW_TOGETHER" -> {
                count = drawings.countDay(userId, day);
                errorCode = ErrorCode.DRAWING_DAILY_LIMIT;
            }
            case "LISTEN_TO_YOU" -> {
                count = answers.countDay(userId, Date.from(day.atStartOfDay(clock.getZone()).toInstant()),
                        Date.from(day.plusDays(1).atStartOfDay(clock.getZone()).toInstant()));
                errorCode = ErrorCode.LISTEN_DAILY_LIMIT_REACHED;
            }
            default -> throw new IllegalArgumentException("游戏尚未接入每日局次限额");
        }
        if (count >= limit) {
            throw new RenException(errorCode);
        }
    }
}
