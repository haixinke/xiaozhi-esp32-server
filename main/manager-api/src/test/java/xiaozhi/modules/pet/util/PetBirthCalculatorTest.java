package xiaozhi.modules.pet.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PetBirthCalculatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("计算八字 - 验证四柱格式")
    void calculate_bazi_hasFourPillars() throws Exception {
        PetBirthCalculator.BirthResult result = PetBirthCalculator.calculate(
                LocalDateTime.of(2026, 5, 7, 10, 30, 0)
        );

        JsonNode bazi = MAPPER.readTree(result.bazi());
        assertThat(bazi.has("year")).isTrue();
        assertThat(bazi.has("month")).isTrue();
        assertThat(bazi.has("day")).isTrue();
        assertThat(bazi.has("hour")).isTrue();

        assertThat(bazi.get("year").asText().length()).isEqualTo(2);
        assertThat(bazi.get("month").asText().length()).isEqualTo(2);
        assertThat(bazi.get("day").asText().length()).isEqualTo(2);
        assertThat(bazi.get("hour").asText().length()).isEqualTo(2);
    }

    @Test
    @DisplayName("计算五行 - 验证键名和数值范围")
    void calculate_wuxing_validKeysAndRange() throws Exception {
        PetBirthCalculator.BirthResult result = PetBirthCalculator.calculate(
                LocalDateTime.of(2026, 5, 7, 10, 30, 0)
        );

        JsonNode wuxing = MAPPER.readTree(result.wuxing());
        assertThat(wuxing.has("metal")).isTrue();
        assertThat(wuxing.has("wood")).isTrue();
        assertThat(wuxing.has("water")).isTrue();
        assertThat(wuxing.has("fire")).isTrue();
        assertThat(wuxing.has("earth")).isTrue();

        for (String key : new String[]{"metal", "wood", "water", "fire", "earth"}) {
            int value = wuxing.get(key).asInt();
            assertThat(value).isBetween(0, 8);
        }

        int total = wuxing.get("metal").asInt() + wuxing.get("wood").asInt()
                + wuxing.get("water").asInt() + wuxing.get("fire").asInt()
                + wuxing.get("earth").asInt();
        assertThat(total).isEqualTo(8);
    }

    @Test
    @DisplayName("计算星座 - 验证返回英文编码")
    void calculate_zodiac_englishCode() {
        PetBirthCalculator.BirthResult result = PetBirthCalculator.calculate(
                LocalDateTime.of(2026, 5, 7, 10, 30, 0)
        );

        assertThat(result.zodiac()).isNotBlank();
        assertThat(result.zodiac()).isEqualTo("taurus");
    }

    @Test
    @DisplayName("不同时辰的八字时柱不同")
    void calculate_differentHours_differentTimePillar() throws Exception {
        PetBirthCalculator.BirthResult morning = PetBirthCalculator.calculate(
                LocalDateTime.of(2026, 5, 7, 8, 0, 0)
        );
        PetBirthCalculator.BirthResult evening = PetBirthCalculator.calculate(
                LocalDateTime.of(2026, 5, 7, 20, 0, 0)
        );

        JsonNode morningBazi = MAPPER.readTree(morning.bazi());
        JsonNode eveningBazi = MAPPER.readTree(evening.bazi());

        assertThat(morningBazi.get("hour").asText()).isNotEqualTo(eveningBazi.get("hour").asText());
    }

    @Test
    @DisplayName("MbtiParser - 解析合法 MBTI")
    void parseMbti_validInput() {
        assertThat(MbtiParser.parse("INTJ")).isEqualTo("INTJ");
        assertThat(MbtiParser.parse("根据分析，这个宠物的MBTI是ENFP")).isEqualTo("ENFP");
        assertThat(MbtiParser.parse("infp")).isEqualTo("INFP");
    }

    @Test
    @DisplayName("MbtiParser - 非法输入返回默认值")
    void parseMbti_invalidInput_returnsDefault() {
        assertThat(MbtiParser.parse("")).isEqualTo("INFP");
        assertThat(MbtiParser.parse(null)).isEqualTo("INFP");
        assertThat(MbtiParser.parse("这是一段普通文本")).isEqualTo("INFP");
        assertThat(MbtiParser.parse("XXXX")).isEqualTo("INFP");
    }

    @Test
    @DisplayName("PetNicknameGenerator - 生成非空昵称")
    void generateNickname_notBlank() {
        String nickname = PetNicknameGenerator.generate();
        assertThat(nickname).isNotBlank();
    }

    @Test
    @DisplayName("PetMood - 随机生成返回合法枚举值")
    void randomMood_returnsValidEnum() {
        PetMood mood = PetMood.random();
        assertThat(mood).isNotNull();
        assertThat(mood.getLabel()).isNotBlank();
    }

    @Test
    @DisplayName("PetMood - 大量随机生成权重分布合理")
    void randomMood_distributionMatchesWeights() {
        int iterations = 10000;
        int[] counts = new int[PetMood.values().length];

        for (int i = 0; i < iterations; i++) {
            counts[PetMood.random().ordinal()]++;
        }

        // JOY(20%) and CALM(20%) should have more than FATIGUE(5%)
        assertThat(counts[PetMood.JOY.ordinal()]).isGreaterThan(counts[PetMood.FATIGUE.ordinal()]);
        assertThat(counts[PetMood.CALM.ordinal()]).isGreaterThan(counts[PetMood.ANXIETY.ordinal()]);

        // Every mood should appear at least once
        for (int count : counts) {
            assertThat(count).isGreaterThan(0);
        }
    }
}
