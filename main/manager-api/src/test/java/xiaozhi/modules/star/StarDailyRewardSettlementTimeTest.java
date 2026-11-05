package xiaozhi.modules.star;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.star.dao.StarAccountDao;
import xiaozhi.modules.star.dao.StarDailyRewardDao;
import xiaozhi.modules.star.entity.StarAccountEntity;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;
import xiaozhi.modules.star.entity.StarTransactionEntity;
import xiaozhi.modules.star.enums.DailyRewardSourceType;
import xiaozhi.modules.star.service.StarDailyRewardService;
import xiaozhi.modules.star.service.StarDailyRewardService.ClaimDecision;
import xiaozhi.modules.star.service.StarService;
import xiaozhi.modules.star.service.impl.StarDailyRewardServiceImpl;

/** 共用每日奖励回归：快速回放、免费跳过与锁后跨日重算均保留原业务语义。 */
class StarDailyRewardSettlementTimeTest {
    private StarDailyRewardDao rewards;
    private StarAccountDao accounts;
    private StarService stars;
    private StarDailyRewardServiceImpl service;
    private MutableClock clock;
    private static final Instant AFTER_MIDNIGHT = Instant.parse("2026-11-04T16:00:01Z");
    @BeforeAll static void messages() { MessageSourceMockSupport.install(); }

    @BeforeEach
    void setup() {
        rewards = mock(StarDailyRewardDao.class); accounts = mock(StarAccountDao.class);
        stars = mock(StarService.class); clock = new MutableClock();
        when(accounts.selectOne(any())).thenReturn(new StarAccountEntity());
        var txn = new StarTransactionEntity(); txn.setId(10L);
        when(stars.earn(anyLong(), any(), anyString(), anyLong(), anyString())).thenReturn(txn);
        service = new StarDailyRewardServiceImpl(rewards, accounts, stars, clock);
    }

    @Test
    void skippedGameDoesNotCreateOrLockWalletAndDoesNotExposeInternalInstant() throws Exception {
        var result = service.claimWithDecision(123L, DailyRewardSourceType.GAME, "TEST_GAME", (u, t, k, d) -> ClaimDecision.skip());
        assertThat(result.isSkipped()).isTrue();
        assertThat(result.getSettledAt()).isEqualTo(clock.instant());
        assertThat(new ObjectMapper().findAndRegisterModules().valueToTree(result).has("settledAt")).isFalse();
        verifyNoInteractions(accounts, stars);
        verify(rewards, never()).insert(any(StarDailyRewardEntity.class));
    }

    @Test
    void alreadyClaimedFastPathDoesNotReadConfigurationOrTouchWallet() {
        when(rewards.selectOne(any())).thenReturn(previous(7));
        StarDailyRewardService.ClaimDecider decider = mock(StarDailyRewardService.ClaimDecider.class);
        var result = service.claimWithDecision(123L, DailyRewardSourceType.GAME, "TEST_GAME", decider);
        assertThat(result.isAwarded()).isFalse();
        assertThat(result.getAmount()).isEqualTo(7);
        assertThat(result.getSettledAt()).isEqualTo(clock.instant());
        verifyNoInteractions(decider, accounts, stars);
    }

    @Test
    void rechecksNewDayAfterWalletLockAndReturnsExistingNewDayReward() {
        when(rewards.selectOne(any())).thenReturn(null, previous(9));
        crossMidnightAtWalletLock();
        var result = service.claimWithDecision(123L, DailyRewardSourceType.GAME, "TEST_GAME", (u, t, k, d) -> ClaimDecision.award(7));
        assertThat(result.isAwarded()).isFalse();
        assertThat(result.getAmount()).isEqualTo(9);
        assertThat(result.getRewardDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(result.getSettledAt()).isEqualTo(AFTER_MIDNIGHT);
        verifyNoInteractions(stars);
    }

    @Test
    void recomputesDateDependentGameDecisionWhenLockWaitCrossesDay() {
        crossMidnightAtWalletLock();
        var decisionDates = new ArrayList<LocalDate>();
        var result = service.claimWithDecision(123L, DailyRewardSourceType.GAME, "TEST_GAME", (u, t, k, day) -> {
            decisionDates.add(day); return ClaimDecision.award(day.getDayOfMonth() == 4 ? 7 : 9);
        });
        assertThat(decisionDates).containsExactly(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));
        assertThat(result.getAmount()).isEqualTo(9);
        assertThat(result.getSettledAt()).isEqualTo(AFTER_MIDNIGHT);
        verify(stars).earn(eq(123L), any(), anyString(), eq(9L), anyString());
    }

    @Test
    void dailyNoteCrossingMidnightRecomputesStreakFromCurrentYesterdayRecord() {
        crossMidnightAtWalletLock();
        when(rewards.selectOne(any())).thenAnswer(invocation -> {
            QueryWrapper<?> query = invocation.getArgument(0);
            query.getSqlSegment();
            var day = query.getParamNameValuePairs().values().stream().filter(java.sql.Date.class::isInstance)
                    .map(java.sql.Date.class::cast).map(java.sql.Date::toLocalDate).findFirst().orElseThrow();
            if (clock.instant().equals(AFTER_MIDNIGHT) && day.equals(LocalDate.of(2026, 11, 4))) {
                // 等锁期间昨天的记录才提交；RC 重新读取它，不能对缺失行加间隙锁。
                assertThat(query.getSqlSegment()).doesNotContain("FOR UPDATE");
                return previous(15);
            }
            return null;
        });
        var result = service.claimDailyReward(123L, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(result.getAmount()).isEqualTo(16);
        assertThat(result.getRewardDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(result.getSettledAt()).isEqualTo(AFTER_MIDNIGHT);
        verify(stars).earn(eq(123L), any(), anyString(), eq(16L), anyString());
    }

    @Test
    void dailyNoteSameDayKeepsInitialElevenAndStreakCap() {
        var first = service.claimDailyReward(123L, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(first.getAmount()).isEqualTo(11);
        when(rewards.selectOne(any())).thenAnswer(invocation -> {
            QueryWrapper<?> query = invocation.getArgument(0); query.getSqlSegment();
            return query.getParamNameValuePairs().containsValue(java.sql.Date.valueOf("2026-11-03")) ? previous(17) : null;
        });
        var capped = service.claimDailyReward(123L, "DAILY_NOTE", "DAILY_NOTE");
        assertThat(capped.getAmount()).isEqualTo(17);
    }

    private void crossMidnightAtWalletLock() {
        when(accounts.selectByUserIdForUpdate(123L)).thenAnswer(invocation -> {
            clock.now = AFTER_MIDNIGHT; return new StarAccountEntity();
        });
    }

    private StarDailyRewardEntity previous(long amount) {
        var result = new StarDailyRewardEntity(); result.setId(20L); result.setAmount(amount);
        result.setStarTransactionId(10L); return result;
    }

    /** 只改变服务器时刻，锁等待由 DAO 回调模拟。 */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T15:59:59Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
