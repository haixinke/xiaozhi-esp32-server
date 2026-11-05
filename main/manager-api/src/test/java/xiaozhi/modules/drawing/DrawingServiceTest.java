package xiaozhi.modules.drawing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.drawing.dao.DrawingSessionDao;
import xiaozhi.modules.drawing.dto.DrawingStartDTO;
import xiaozhi.modules.drawing.entity.DrawingSessionEntity;
import xiaozhi.modules.drawing.service.DrawingService;
import xiaozhi.modules.game.dao.GameDao;
import xiaozhi.modules.game.entity.GameEntity;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.service.StarService;
import xiaozhi.modules.star.vo.DailyRewardResult;

/** 经公开业务入口验证资格、开局幂等和完成时金额；不触碰真实运营游戏配置。 */
class DrawingServiceTest {
    private DrawingSessionDao sessions;
    private GameDao games;
    private PetDao pets;
    private StarDailyRewardDao rewards;
    private StarService stars;
    private StarDailyRewardService dailyRewards;
    private DrawingService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T16:00:01Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeAll static void messages() { MessageSourceMockSupport.install(); }

    @BeforeEach
    void setup() {
        sessions = mock(DrawingSessionDao.class); games = mock(GameDao.class); pets = mock(PetDao.class);
        rewards = mock(StarDailyRewardDao.class); stars = mock(StarService.class);
        dailyRewards = mock(StarDailyRewardService.class);
        service = new DrawingService(sessions, games, pets, rewards, stars, dailyRewards, new xiaozhi.modules.game.service.impl.GamePlayLimitServiceImpl(
                games, sessions, mock(xiaozhi.modules.listen.dao.ListenAnswerDao.class), clock), clock);
        when(sessions.lockUser(123L)).thenReturn(123L);
        var pet = new PetEntity(); pet.setUserId(123L); pet.setHatchStatus("HATCHED");
        when(pets.selectById("pet-a")).thenReturn(pet);
        var game = new GameEntity(); game.setStatus(1); game.setEarnStars(7); game.setConsumeStars(0); game.setDailyLimit(2);
        when(games.selectByGameType(any())).thenReturn(game);
    }

    @Test
    void successfulStartIsCountedAndRequestRetryReturnsOriginalSessionWithoutNewCharge() {
        var result = service.start(123L, request());
        assertThat(result.referenceId()).isNotBlank();
        var captured = org.mockito.ArgumentCaptor.forClass(DrawingSessionEntity.class);
        verify(sessions).insert(captured.capture());
        var session = captured.getValue();
        assertThat(session.getStartedDay().toString()).isEqualTo("2026-11-05");
        when(sessions.findRequest(123L, "request-a")).thenReturn(session);
        var retry = service.start(123L, request());
        assertThat(retry.sessionId()).isEqualTo(result.sessionId());
        verify(sessions, times(1)).insert(any(DrawingSessionEntity.class));
        verifyNoInteractions(stars);
    }

    @Test
    void startNeverChargesEvenWhenCurrentConfigurationHasAConsumptionAmount() {
        var paid = new GameEntity(); paid.setStatus(1); paid.setEarnStars(0); paid.setConsumeStars(3);
        when(games.selectByGameType(any())).thenReturn(paid);
        var dto = request();
        assertThat(service.start(123L, dto).sessionId()).isNotBlank();
        verifyNoInteractions(stars);
    }

    @Test
    void rejectsMissingUnhatchedDeletedOrOtherUsersPetAndExhaustedLimit() {
        when(pets.selectById("pet-a")).thenReturn(null);
        assertCode(() -> service.start(123L, request()), ErrorCode.DRAWING_PET_REQUIRED);
        var pet = new PetEntity(); pet.setUserId(124L); pet.setHatchStatus("HATCHED");
        when(pets.selectById("pet-a")).thenReturn(pet);
        assertCode(() -> service.start(123L, request()), ErrorCode.DRAWING_PET_REQUIRED);
        pet.setUserId(123L); pet.setHatchStatus("EGG");
        assertCode(() -> service.start(123L, request()), ErrorCode.DRAWING_PET_REQUIRED);
        pet.setHatchStatus("HATCHED"); pet.setDeletedAt(1L);
        assertCode(() -> service.start(123L, request()), ErrorCode.DRAWING_PET_REQUIRED);
        pet.setDeletedAt(0L); when(sessions.countDay(eq(123L), any())).thenReturn(2L);
        assertCode(() -> service.start(123L, request()), ErrorCode.DRAWING_DAILY_LIMIT);
        verifyNoInteractions(stars);
    }

    @Test
    void completedSessionReplaysAcrossDaysWithoutRerewardingOrCheckingNewConfiguration() {
        var session = started(); session.setCompletedAt(Date.from(clock.instant().minusSeconds(86_400)));
        session.setRewardStatus("NEW_REWARD");
        when(sessions.findOwned(123L, "session-a")).thenReturn(session);
        when(stars.balance(123L)).thenReturn(35L);
        var prior = new xiaozhi.modules.star.entity.StarDailyRewardEntity(); prior.setAmount(7L);
        when(rewards.selectOne(any())).thenReturn(prior);
        var result = service.complete(123L, "session-a");
        assertThat(result.awardedStars()).isEqualTo(7);
        assertThat(result.balance()).isEqualTo(35);
        verifyNoInteractions(games, dailyRewards);
    }

    @Test
    void completesUsingCurrentAwardInsteadOfStartConfiguration() {
        var session = started(); when(sessions.findOwned(123L, "session-a")).thenReturn(session);
        var changed = new GameEntity(); changed.setStatus(1); changed.setEarnStars(9); changed.setConsumeStars(0);
        when(games.selectByGameType(any())).thenReturn(changed);
        when(dailyRewards.claimWithDecision(eq(123L), any(), any(), any())).thenAnswer(invocation -> {
            StarDailyRewardService.ClaimDecider decider = invocation.getArgument(3);
            assertThat(decider.decide(123L, "GAME", "test", java.time.LocalDate.now(clock)).amount()).isEqualTo(9);
            var reward = DailyRewardResult.awarded(9, 10L, 11L, java.time.LocalDate.now(clock));
            reward.setSettledAt(clock.instant());
            return reward;
        });
        when(stars.balance(123L)).thenReturn(27L);
        var result = service.complete(123L, "session-a");
        assertThat(result.awardedStars()).isEqualTo(9);
        verify(sessions).updateById(any(DrawingSessionEntity.class));
    }

    @Test
    void showsSharedAllowanceAndDailyRewardAvailabilityAndUnlimitedSentinel() {
        when(sessions.countDay(eq(123L), any())).thenReturn(1L);
        assertThat(service.config(123L, "pet-a").remainingPlays()).isEqualTo(1);
        assertThat(service.config(123L, "pet-a").rewardAvailable()).isTrue();
        when(rewards.selectCount(any())).thenReturn(1L);
        assertThat(service.config(123L, "pet-a").rewardAvailable()).isFalse();
        var game = new GameEntity(); game.setStatus(1); game.setDailyLimit(0);
        when(games.selectByGameType(any())).thenReturn(game);
        assertThat(service.config(123L, "pet-a").remainingPlays()).isEqualTo(-1);
    }

    @Test
    void rejectsInvalidRequestAndUntrustedUserButReplaysRequestAcrossPets() {
        assertCode(() -> service.start(123L, null), ErrorCode.DRAWING_REQUEST_INVALID);
        var invalid = request(); invalid.setRequestId("short");
        assertCode(() -> service.start(123L, invalid), ErrorCode.DRAWING_REQUEST_INVALID);
        when(sessions.lockUser(999L)).thenReturn(null);
        assertCode(() -> service.start(999L, request()), ErrorCode.DRAWING_REQUEST_INVALID);
        var session = started();
        when(sessions.findRequest(123L, "request-a")).thenReturn(session);
        var retry = request(); retry.setPetId("other-pet");
        assertThat(service.start(123L, retry).sessionId()).isEqualTo("session-a");
    }

    @Test
    void rejectsMissingDisabledOrConflictingConfigurationBeforeStarting() {
        when(games.selectByGameType(any())).thenReturn(null);
        assertCode(() -> service.start(123L, request()), ErrorCode.GAME_NOT_FOUND);
        var disabled = new GameEntity(); disabled.setStatus(0);
        when(games.selectByGameType(any())).thenReturn(disabled);
        assertCode(() -> service.start(123L, request()), ErrorCode.GAME_DISABLED);
        disabled.setStatus(1); disabled.setEarnStars(7); disabled.setConsumeStars(1);
        assertCode(() -> service.start(123L, request()), ErrorCode.GAME_STAR_CONFIG_CONFLICT);
        verifyNoInteractions(stars);
    }

    @Test
    void rejectsUnknownSessionBeforeCompletionWithoutReward() {
        assertCode(() -> service.complete(123L, "missing-session"),
                ErrorCode.DRAWING_SESSION_NOT_FOUND);
        verifyNoInteractions(stars, dailyRewards);
    }

    @Test
    void anotherCompletionSameDayReturnsZeroNewStarsAndPaidSessionNeverClaimsReward() {
        var session = started(); when(sessions.findOwned(123L, "session-a")).thenReturn(session);
        var previousReward = DailyRewardResult.alreadyClaimed(7, 10L, 11L, java.time.LocalDate.now(clock));
        previousReward.setSettledAt(clock.instant());
        when(dailyRewards.claimWithDecision(eq(123L), any(), any(), any())).thenReturn(previousReward);
        var result = service.complete(123L, "session-a");
        assertThat(result.awardedStars()).isZero();
        assertThat(result.rewardStatus()).isEqualTo("ALREADY_CLAIMED");
        clearInvocations(dailyRewards);
        var paid = started();
        var changed = new GameEntity(); changed.setStatus(1); changed.setConsumeStars(3);
        when(games.selectByGameType(any())).thenReturn(changed);
        when(sessions.findOwned(123L, "paid-session")).thenReturn(paid);
        assertThat(service.complete(123L, "paid-session").rewardStatus())
                .isEqualTo("NO_REWARD");
        verifyNoInteractions(dailyRewards);
    }

    private DrawingSessionEntity started() {
        var session = new DrawingSessionEntity(); session.setId("session-a"); session.setUserId(123L);
        session.setStartedAt(Date.from(clock.instant().minusSeconds(10))); return session;
    }

    private DrawingStartDTO request() {
        var dto = new DrawingStartDTO(); dto.setPetId("pet-a"); dto.setRequestId("request-a");
        return dto;
    }

    private void assertCode(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RenException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
