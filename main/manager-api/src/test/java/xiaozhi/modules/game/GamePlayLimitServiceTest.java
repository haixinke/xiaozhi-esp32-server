package xiaozhi.modules.game;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.game.service.impl.GamePlayLimitServiceImpl;
import xiaozhi.modules.listen.dao.ListenAnswerDao;

/** 通过公共限额接口验证运营配置的可见行为，DAO 为数据边界测试桩。 */
class GamePlayLimitServiceTest {
    private final GameDao games = mock(GameDao.class);
    private final DrawingSessionDao drawings = mock(DrawingSessionDao.class);
    private final ListenAnswerDao answers = mock(ListenAnswerDao.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T18:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void messages() { MessageSourceMockSupport.install(); }

    @Test
    void zeroLimitAllowsPlayingWithoutCounting() {
        var game = new GameEntity();
        game.setDailyLimit(0);
        when(games.selectByGameType("DRAW_TOGETHER")).thenReturn(game);
        var service = new GamePlayLimitServiceImpl(games, drawings, answers, clock);

        assertThatCode(() -> service.checkDailyLimit(123L, "DRAW_TOGETHER")).doesNotThrowAnyException();
    }

    @Test
    void drawingAtConfiguredLimitIsRejectedOnShanghaiDay() {
        var game = new GameEntity();
        game.setDailyLimit(2);
        when(games.selectByGameType("DRAW_TOGETHER")).thenReturn(game);
        when(drawings.countDay(123L, LocalDate.of(2026, 11, 5))).thenReturn(2L);
        var service = new GamePlayLimitServiceImpl(games, drawings, answers, clock);

        assertThatThrownBy(() -> service.checkDailyLimit(123L, "DRAW_TOGETHER"))
                .isInstanceOf(RenException.class).extracting("code").isEqualTo(ErrorCode.DRAWING_DAILY_LIMIT);
    }

    @Test
    void listenAtConfiguredLimitIsRejectedWithinCompleteShanghaiDay() {
        var game = new GameEntity();
        game.setDailyLimit(3);
        when(games.selectByGameType("LISTEN_TO_YOU")).thenReturn(game);
        when(answers.countDay(123L, Date.from(Instant.parse("2026-11-04T16:00:00Z")),
                Date.from(Instant.parse("2026-11-05T16:00:00Z")))).thenReturn(3L);
        var service = new GamePlayLimitServiceImpl(games, drawings, answers, clock);

        assertThatThrownBy(() -> service.checkDailyLimit(123L, "LISTEN_TO_YOU"))
                .isInstanceOf(RenException.class).extracting("code").isEqualTo(ErrorCode.LISTEN_DAILY_LIMIT_REACHED);
    }

    @Test
    void missingGameReturnsBusinessError() {
        var service = new GamePlayLimitServiceImpl(games, drawings, answers, clock);

        assertThatThrownBy(() -> service.checkDailyLimit(123L, "TEST_MISSING"))
                .isInstanceOf(RenException.class).extracting("code").isEqualTo(ErrorCode.GAME_NOT_FOUND);
    }

    @Test
    void drawingBelowPositiveLimitIsAllowed() {
        var game = new GameEntity();
        game.setDailyLimit(3);
        when(games.selectByGameType("DRAW_TOGETHER")).thenReturn(game);
        when(drawings.countDay(123L, LocalDate.of(2026, 11, 5))).thenReturn(2L);
        // 另一游戏已用尽，不应串用它的局次。
        when(answers.countDay(eq(123L), any(), any())).thenReturn(99L);
        var service = new GamePlayLimitServiceImpl(games, drawings, answers, clock);

        assertThatCode(() -> service.checkDailyLimit(123L, "DRAW_TOGETHER")).doesNotThrowAnyException();
    }

    @Test
    void listenBelowPositiveLimitIsAllowedIndependentlyOfDrawing() {
        var game = new GameEntity();
        game.setDailyLimit(2);
        when(games.selectByGameType("LISTEN_TO_YOU")).thenReturn(game);
        when(answers.countDay(eq(123L), any(), any())).thenReturn(1L);
        when(drawings.countDay(123L, LocalDate.of(2026, 11, 5))).thenReturn(99L);
        var service = new GamePlayLimitServiceImpl(games, drawings, answers, clock);

        assertThatCode(() -> service.checkDailyLimit(123L, "LISTEN_TO_YOU")).doesNotThrowAnyException();
    }
}
