package xiaozhi.modules.pet.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xiaozhi.modules.pet.constant.TodayMood;
import xiaozhi.modules.pet.entity.PetEntity;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoodDecider 今日心情判定")
class MoodDeciderTest {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_700_000_000_000L; // 固定基准，避免真随机时钟

    private PetEntity egg(String mbti) {
        PetEntity pet = new PetEntity();
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(new Date(NOW - DAY_MS)); // 1天前开始孵化
        pet.setExpectedHatchTime(new Date(NOW + 5 * DAY_MS));
        pet.setCreateDate(new Date(NOW - DAY_MS));
        pet.setMbti(mbti);
        return pet;
    }

    private PetEntity hatched(String mbti) {
        PetEntity pet = new PetEntity();
        pet.setHatchStatus("HATCHED");
        pet.setHatchedAt(new Date(NOW - DAY_MS)); // 1天前破壳
        pet.setCreateDate(new Date(NOW - 8 * DAY_MS));
        pet.setMbti(mbti);
        return pet;
    }

    @Test
    @DisplayName("破壳后 baseline 取 hatchedAt")
    void baseline_hatched_usesHatchedAt() {
        PetEntity pet = hatched("INFP");
        assertThat(MoodDecider.baseline(pet, NOW)).isEqualTo(NOW - DAY_MS);
    }

    @Test
    @DisplayName("孵化期 baseline 取 hatchStartTime")
    void baseline_egg_usesHatchStartTime() {
        PetEntity pet = egg("ENFP");
        assertThat(MoodDecider.baseline(pet, NOW)).isEqualTo(NOW - DAY_MS);
    }

    @Test
    @DisplayName("孵化期 baseline 缺 hatchStartTime 时回退 createDate")
    void baseline_egg_fallsBackCreateDate() {
        PetEntity pet = new PetEntity();
        pet.setHatchStatus("EGG");
        pet.setCreateDate(new Date(NOW - 3 * DAY_MS));
        assertThat(MoodDecider.baseline(pet, NOW)).isEqualTo(NOW - 3 * DAY_MS);
    }

    @Test
    @DisplayName("baseline 缺 hatchedAt 时回退 createDate")
    void baseline_hatched_fallsBackCreateDate() {
        PetEntity pet = new PetEntity();
        pet.setHatchStatus("HATCHED");
        pet.setCreateDate(new Date(NOW - 3 * DAY_MS));
        assertThat(MoodDecider.baseline(pet, NOW)).isEqualTo(NOW - 3 * DAY_MS);
    }

    @Test
    @DisplayName("inactive >=4天 → 低落")
    void decide_inactive4Days_returnsLow() {
        PetEntity pet = egg("INFP");
        // baseline=NOW-1d，但把 now 推到 5天后，inactive=6天
        assertThat(MoodDecider.decide(pet, NOW - 1 * DAY_MS, NOW + 5 * DAY_MS)).isEqualTo(TodayMood.LOW);
    }

    @Test
    @DisplayName("inactive 2-3天 → 想念")
    void decide_inactive2to3Days_returnsMiss() {
        PetEntity pet = egg("INFP");
        assertThat(MoodDecider.decide(pet, NOW - 1 * DAY_MS, NOW + 2 * DAY_MS)).isEqualTo(TodayMood.MISS);
    }

    @Test
    @DisplayName("孵化期临近预计破壳日(<=1天) → 兴奋")
    void decide_eggSoonHatch_returnsExcited() {
        PetEntity pet = new PetEntity();
        pet.setHatchStatus("EGG");
        pet.setExpectedHatchTime(new Date(NOW + 12 * 60 * 60 * 1000)); // 12小时后破壳
        pet.setCreateDate(new Date(NOW - 12 * 60 * 60 * 1000));
        pet.setMbti("INFP");
        long base = NOW - 12 * 60 * 60 * 1000; // 半天前开始，inactive=0
        pet.setHatchStartTime(new Date(base));
        assertThat(MoodDecider.decide(pet, base, NOW)).isEqualTo(TodayMood.EXCITED);
    }

    @Test
    @DisplayName("孵化期已过预计破壳日仍未破壳 → 兴奋(准备破壳)")
    void decide_eggOverdue_returnsExcited() {
        PetEntity pet = new PetEntity();
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(new Date(NOW - 12 * 60 * 60 * 1000)); // 半天前
        pet.setExpectedHatchTime(new Date(NOW - 60 * 1000)); // 1分钟前已到点
        pet.setCreateDate(new Date(NOW - 12 * 60 * 60 * 1000));
        pet.setMbti("INFP");
        long base = NOW - 12 * 60 * 60 * 1000;
        assertThat(MoodDecider.decide(pet, base, NOW)).isEqualTo(TodayMood.EXCITED);
    }

    @Test
    @DisplayName("12小时内有活跃 → 开心")
    void decide_recentInteraction_returnsHappy() {
        PetEntity pet = hatched("INFP");
        long base = NOW - 6 * 60 * 60 * 1000; // 6小时前
        assertThat(MoodDecider.decide(pet, base, NOW)).isEqualTo(TodayMood.HAPPY);
    }

    @Test
    @DisplayName("无近期活跃 + mbti=E → 兴奋(软分桶)")
    void decide_noRecentMbtiE_returnsExcited() {
        PetEntity pet = hatched("ENFP");
        long base = NOW - 2 * 60 * 60 * 1000; // 2小时前 → <12h 命中 happy
        // 要避开 happy：base 设到 >12h 且 <2d
        long base2 = NOW - 18 * 60 * 60 * 1000; // 18小时前 → >12h, <2d → 不命中 happy/miss
        assertThat(MoodDecider.decide(pet, base2, NOW)).isEqualTo(TodayMood.EXCITED);
    }

    @Test
    @DisplayName("无近期活跃 + mbti=I → 平静(软分桶)")
    void decide_noRecentMbtiI_returnsCalm() {
        PetEntity pet = hatched("INFP");
        long base = NOW - 18 * 60 * 60 * 1000; // 18小时前
        assertThat(MoodDecider.decide(pet, base, NOW)).isEqualTo(TodayMood.CALM);
    }

    @Test
    @DisplayName("无 mbti 无近期活跃 → 回退随机开心/平静")
    void decide_noMbtiNoRecent_returnsHappyOrCalm() {
        PetEntity pet = new PetEntity();
        pet.setHatchStatus("HATCHED");
        pet.setHatchedAt(new Date(NOW - 18 * 60 * 60 * 1000));
        pet.setCreateDate(new Date(NOW - 8 * DAY_MS));
        TodayMood m = MoodDecider.decide(pet, NOW - 18 * 60 * 60 * 1000, NOW);
        assertThat(m).isIn(TodayMood.HAPPY, TodayMood.CALM);
    }
}
