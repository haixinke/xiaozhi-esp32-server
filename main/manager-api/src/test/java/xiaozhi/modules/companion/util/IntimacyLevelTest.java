package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IntimacyLevelTest {

    @Test
    void of_mapsValueToTier() {
        assertThat(IntimacyLevel.of(0.0f)).isEqualTo(IntimacyLevel.ACQUAINTED);
        assertThat(IntimacyLevel.of(0.35f)).isEqualTo(IntimacyLevel.CRUSH);
        assertThat(IntimacyLevel.of(0.5f)).isEqualTo(IntimacyLevel.AMBIGUOUS);
        assertThat(IntimacyLevel.of(0.79f)).isEqualTo(IntimacyLevel.LOVER);
        assertThat(IntimacyLevel.of(0.8f)).isEqualTo(IntimacyLevel.DEEP_LOVE);
        assertThat(IntimacyLevel.of(1.0f)).isEqualTo(IntimacyLevel.DEEP_LOVE);
    }

    @Test
    void of_clampsOutOfRange() {
        assertThat(IntimacyLevel.of(-1f)).isEqualTo(IntimacyLevel.ACQUAINTED);
        assertThat(IntimacyLevel.of(9f)).isEqualTo(IntimacyLevel.DEEP_LOVE);
    }

    @Test
    void next_returnsFollowingTier_deepLoveStays() {
        assertThat(IntimacyLevel.CRUSH.next()).isEqualTo(IntimacyLevel.AMBIGUOUS);
        assertThat(IntimacyLevel.DEEP_LOVE.next()).isEqualTo(IntimacyLevel.DEEP_LOVE);
    }

    @Test
    void progressWithin_isFractionOfTier() {
        // AMBIGUOUS [0.4,0.6): 0.5 -> 0.5
        assertThat(IntimacyLevel.of(0.5f).progressWithin(0.5f)).isEqualTo(0.5f, within(1e-4f));
        // DEEP_LOVE [0.8,1.0]: 0.9 -> 0.5
        assertThat(IntimacyLevel.DEEP_LOVE.progressWithin(0.9f)).isEqualTo(0.5f, within(1e-4f));
    }

    @Test
    void promptDescription_presentForEachTier() {
        for (IntimacyLevel l : IntimacyLevel.values()) {
            assertThat(l.getPromptDescription()).isNotBlank();
        }
    }
}
