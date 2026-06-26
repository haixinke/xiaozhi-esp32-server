package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompanionMood 心情枚举")
class CompanionMoodTest {

    @Test
    @DisplayName("random() 返回的始终是有效心情")
    void random_returnsValidMood() {
        for (int i = 0; i < 1000; i++) {
            CompanionMood mood = CompanionMood.random();
            assertThat(mood).isIn(Arrays.asList(CompanionMood.values()));
        }
    }

    @Test
    @DisplayName("所有心情都有中文标签")
    void allMoods_haveChineseLabel() {
        for (CompanionMood mood : CompanionMood.values()) {
            assertThat(mood.getLabel()).isNotBlank();
        }
    }

    @Test
    @DisplayName("权重随机分布符合预期（正面积心情占主导）")
    void random_distributionMatchesWeights() {
        int total = 10_000;
        Map<CompanionMood, Long> counts = Arrays.stream(CompanionMood.values())
                .collect(Collectors.toMap(m -> m, m -> 0L));

        for (int i = 0; i < total; i++) {
            CompanionMood mood = CompanionMood.random();
            counts.merge(mood, 1L, Long::sum);
        }

        // 总权重 = 100
        assertThat(counts.get(CompanionMood.JOY)).isBetween(1700L, 2300L);
        assertThat(counts.get(CompanionMood.CALM)).isBetween(1700L, 2300L);
        assertThat(counts.get(CompanionMood.EXCITEMENT)).isBetween(1200L, 1800L);
        assertThat(counts.get(CompanionMood.CURIOSITY)).isBetween(1200L, 1800L);
        assertThat(counts.get(CompanionMood.CARE)).isBetween(1200L, 1800L);
        assertThat(counts.get(CompanionMood.ANXIETY)).isBetween(300L, 700L);
        assertThat(counts.get(CompanionMood.FRUSTRATION)).isBetween(300L, 700L);
        assertThat(counts.get(CompanionMood.FATIGUE)).isBetween(300L, 700L);
    }

    @Test
    @DisplayName("valueOf 可正确解析心情编码")
    void valueOf_parsesMoodCode() {
        assertThat(CompanionMood.valueOf("JOY")).isEqualTo(CompanionMood.JOY);
        assertThat(CompanionMood.valueOf("CALM")).isEqualTo(CompanionMood.CALM);
    }

    @Test
    @DisplayName("fromCode 对空值或非法编码回退到平静")
    void fromCode_invalidOrBlank_returnsCalm() {
        assertThat(CompanionMood.fromCode(null)).isEqualTo(CompanionMood.CALM);
        assertThat(CompanionMood.fromCode("")).isEqualTo(CompanionMood.CALM);
        assertThat(CompanionMood.fromCode("unknown")).isEqualTo(CompanionMood.CALM);
    }

    @Test
    @DisplayName("fromCode 支持大小写不敏感匹配")
    void fromCode_caseInsensitive_returnsMood() {
        assertThat(CompanionMood.fromCode("joy")).isEqualTo(CompanionMood.JOY);
        assertThat(CompanionMood.fromCode("Calm")).isEqualTo(CompanionMood.CALM);
    }
}

