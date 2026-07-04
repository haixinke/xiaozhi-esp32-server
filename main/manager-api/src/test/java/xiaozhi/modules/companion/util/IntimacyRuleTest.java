package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IntimacyRuleTest {

    @Test
    void startValue_biasByRelationType_allWithinCrushTier() {
        assertThat(IntimacyRule.startValue("childhood")).isEqualTo(0.38f);
        assertThat(IntimacyRule.startValue("loveAtFirst")).isEqualTo(0.35f);
        assertThat(IntimacyRule.startValue("bickering")).isEqualTo(0.32f);
        assertThat(IntimacyRule.startValue("unknown")).isEqualTo(0.35f);
        assertThat(IntimacyRule.startValue(null)).isEqualTo(0.35f);
    }

    @Test
    void engagement_saturates() {
        assertThat(IntimacyRule.engagement(0)).isEqualTo(0f);
        assertThat(IntimacyRule.engagement(1)).isEqualTo(0.25f, within(0.02f));
        assertThat(IntimacyRule.engagement(3)).isEqualTo(0.50f, within(0.02f));
        assertThat(IntimacyRule.engagement(7)).isEqualTo(0.75f, within(0.02f));
        assertThat(IntimacyRule.engagement(15)).isEqualTo(1.0f, within(1e-4f));
        assertThat(IntimacyRule.engagement(100)).isEqualTo(1.0f, within(1e-4f));
    }

    @Test
    void streakFactor_growsAndCapsAtSeven() {
        assertThat(IntimacyRule.streakFactor(1)).isEqualTo(1.0f, within(1e-4f));
        assertThat(IntimacyRule.streakFactor(7)).isEqualTo(1.48f, within(1e-4f));
        assertThat(IntimacyRule.streakFactor(30)).isEqualTo(1.48f, within(1e-4f));
    }

    @Test
    void grow_diminishesNearOneAndRespectsDailyCap() {
        float low = IntimacyRule.grow(0.35f, 15, 1);   // active, full engagement
        float high = IntimacyRule.grow(0.90f, 15, 1);
        assertThat(low - 0.35f).isGreaterThan(high - 0.90f); // low intimacy grows faster
        // daily cap 0.05
        float capped = IntimacyRule.grow(0.20f, 100, 7);
        assertThat(capped - 0.20f).isLessThanOrEqualTo(0.05f + 1e-4f);
        // no activity -> no growth
        assertThat(IntimacyRule.grow(0.5f, 0, 3)).isEqualTo(0.5f, within(1e-4f));
    }

    @Test
    void decay_respectsGraceResistanceAndFloor() {
        // within grace (<=2 days) -> unchanged
        assertThat(IntimacyRule.decay(0.7f, 2)).isEqualTo(0.7f, within(1e-4f));
        // past grace -> drops, higher intimacy drops slower
        float dropLover = 0.7f - IntimacyRule.decay(0.7f, 3);
        float dropCrush = 0.3f - IntimacyRule.decay(0.3f, 3);
        assertThat(dropLover).isLessThan(dropCrush);
        // hard floor 0.15
        assertThat(IntimacyRule.decay(0.16f, 999)).isGreaterThanOrEqualTo(0.15f);
    }

    @Test
    void nextStreak_incrementsOnConsecutiveResetsOnGap() {
        assertThat(IntimacyRule.nextStreak(3, true, true)).isEqualTo(4);
        assertThat(IntimacyRule.nextStreak(3, true, false)).isEqualTo(1);
        assertThat(IntimacyRule.nextStreak(3, false, false)).isEqualTo(0);
    }
}
