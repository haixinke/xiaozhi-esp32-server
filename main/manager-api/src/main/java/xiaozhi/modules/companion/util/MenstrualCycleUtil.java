package xiaozhi.modules.companion.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class MenstrualCycleUtil {

    private MenstrualCycleUtil() {
    }

    public static MenstrualPhase computePhase(LocalDate startDate, int cycleLength, int periodLength, LocalDate today) {
        int day = cycleDay(startDate, cycleLength, today);
        if (day <= periodLength) {
            return MenstrualPhase.MENSTRUATION;
        }
        if (day == cycleLength - 14) {
            return MenstrualPhase.OVULATION;
        }
        if (day < cycleLength - 14) {
            return MenstrualPhase.FOLLICULAR;
        }
        return MenstrualPhase.LUTEAL;
    }

    public static int cycleDay(LocalDate startDate, int cycleLength, LocalDate today) {
        long daysBetween = ChronoUnit.DAYS.between(startDate, today);
        int offset = (int) (daysBetween % cycleLength);
        if (offset < 0) {
            offset += cycleLength;
        }
        return offset + 1;
    }

    public static int daysUntilNextPeriod(LocalDate startDate, int cycleLength, LocalDate today) {
        int day = cycleDay(startDate, cycleLength, today);
        return cycleLength - day + 1;
    }
}
