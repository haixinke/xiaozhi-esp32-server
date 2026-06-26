package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MenstrualCycleUtil 周期计算")
class MenstrualCycleUtilTest {

    @Test
    @DisplayName("经期第 1 天返回 MENSTRUATION")
    void computePhase_firstDayOfPeriod_returnsMenstruation() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 1);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.MENSTRUATION);
    }

    @Test
    @DisplayName("经期最后一天返回 MENSTRUATION")
    void computePhase_lastDayOfPeriod_returnsMenstruation() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 5);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.MENSTRUATION);
    }

    @Test
    @DisplayName("经期后一天返回 FOLLICULAR")
    void computePhase_dayAfterPeriod_returnsFollicular() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 6);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.FOLLICULAR);
    }

    @Test
    @DisplayName("28 天周期第 14 天返回 OVULATION")
    void computePhase_day14Of28Cycle_returnsOvulation() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 14);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.OVULATION);
    }

    @Test
    @DisplayName("周期第 15 天返回 LUTEAL")
    void computePhase_day15Of28Cycle_returnsLuteal() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 15);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.LUTEAL);
    }

    @Test
    @DisplayName("跨周期时正确回绕")
    void computePhase_acrossCycles_wrapsCorrectly() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 7, 1);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.MENSTRUATION);
    }

    @Test
    @DisplayName("cycleDay 返回周期内第几天")
    void cycleDay_returnsOneBasedDayInCycle() {
        LocalDate start = LocalDate.of(2026, 6, 1);

        assertThat(MenstrualCycleUtil.cycleDay(start, 28, LocalDate.of(2026, 6, 1))).isEqualTo(1);
        assertThat(MenstrualCycleUtil.cycleDay(start, 28, LocalDate.of(2026, 6, 28))).isEqualTo(28);
        assertThat(MenstrualCycleUtil.cycleDay(start, 28, LocalDate.of(2026, 6, 29))).isEqualTo(1);
    }

    @Test
    @DisplayName("daysUntilNextPeriod 计算正确")
    void daysUntilNextPeriod_returnsCorrectDays() {
        LocalDate start = LocalDate.of(2026, 6, 1);

        assertThat(MenstrualCycleUtil.daysUntilNextPeriod(start, 28, LocalDate.of(2026, 6, 1))).isEqualTo(28);
        assertThat(MenstrualCycleUtil.daysUntilNextPeriod(start, 28, LocalDate.of(2026, 6, 2))).isEqualTo(27);
        assertThat(MenstrualCycleUtil.daysUntilNextPeriod(start, 28, LocalDate.of(2026, 6, 28))).isEqualTo(1);
        assertThat(MenstrualCycleUtil.daysUntilNextPeriod(start, 28, LocalDate.of(2026, 6, 29))).isEqualTo(28);
    }
}
