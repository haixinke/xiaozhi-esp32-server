package xiaozhi.modules.drawing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.drawing.entity.DrawingSessionEntity;
import xiaozhi.modules.drawing.service.DrawingService;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.service.StarService;
import xiaozhi.modules.star.service.impl.StarDailyRewardServiceImpl;

/** 真实绘画与每日奖励服务共用锁后结算时刻，避免钱包等待跨日造成奖励与完成日期分裂。 */
class DrawingSettlementTimeTest {
    @BeforeAll static void messages() { MessageSourceMockSupport.install(); }

    @Test
    void accountLockWaitAcrossMidnightUsesSameInstantForRewardAndDrawingCompletion() {
        assertSettlement(false);
    }

    @Test
    void walletWorkAfterDecisionCannotChangePersistedDrawingCompletionDay() {
        assertSettlement(true);
    }

    private void assertSettlement(boolean advanceDuringEarn) {
        var clock = new MutableClock();
        var rewards = mock(StarDailyRewardDao.class); var accounts = mock(StarAccountDao.class);
        var stars = mock(StarService.class); var sessions = mock(DrawingSessionDao.class);
        var pets = mock(PetDao.class);
        when(sessions.lockUser(123L)).thenReturn(123L);
        var session = new DrawingSessionEntity(); session.setId("session-test"); session.setUserId(123L);
        session.setStartedAt(Date.from(clock.instant().minusSeconds(20)));
        when(sessions.findOwned(123L, "session-test")).thenReturn(session);
        var pet = new PetEntity(); pet.setUserId(123L); pet.setHatchStatus("HATCHED");
        when(pets.selectById("pet-test")).thenReturn(pet);
        when(accounts.selectOne(any())).thenReturn(new StarAccountEntity());
        Instant settledAt = Instant.parse("2026-11-04T16:00:01Z");
        when(accounts.selectByUserIdForUpdate(123L)).thenAnswer(invocation -> {
            clock.now = settledAt;
            return new StarAccountEntity();
        });
        var txn = new StarTransactionEntity(); txn.setId(10L);
        when(stars.earn(eq(123L), any(), anyString(), eq(7L), anyString())).thenAnswer(invocation -> {
            if (advanceDuringEarn) clock.now = settledAt.plusSeconds(86_400);
            return txn;
        });
        when(stars.balance(123L)).thenReturn(22L);
        var daily = new StarDailyRewardServiceImpl(rewards, accounts, stars, clock);
        var games = mock(GameDao.class);
        var game = new xiaozhi.modules.game.entity.GameEntity(); game.setStatus(1); game.setEarnStars(7);
        when(games.selectByGameType(any())).thenReturn(game);
        var drawing = new DrawingService(sessions, games, pets, rewards, stars, daily,
                new xiaozhi.modules.game.service.impl.GamePlayLimitServiceImpl(games, sessions,
                        mock(xiaozhi.modules.listen.dao.ListenAnswerDao.class), clock), clock);
        var result = drawing.complete(123L, "session-test");
        var savedReward = ArgumentCaptor.forClass(StarDailyRewardEntity.class);
        verify(rewards).insert(savedReward.capture());
        assertThat(((java.sql.Date) savedReward.getValue().getRewardDate()).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(result.completedAt().toInstant()).isEqualTo(settledAt);
        assertThat(result.awardedStars()).isEqualTo(7);
    }

    /** 模拟服务器在行锁等待和钱包写入时跨日，不等待真实时间。 */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T15:59:59Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
