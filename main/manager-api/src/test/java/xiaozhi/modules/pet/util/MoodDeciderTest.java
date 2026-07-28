package xiaozhi.modules.pet.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xiaozhi.modules.pet.constant.TodayMood;
import xiaozhi.modules.pet.entity.PetEntity;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoodDecider 今日心情判定")
class MoodDeciderTest {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_700_000_000_000L; // 固定基准，避免真随机时钟
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    private PetEntity egg(String mbti) {
        PetEntity pet = new PetEntity();
        pet.setId("pet-egg-1");
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(new Date(NOW - DAY_MS)); // 1天前开始孵化
        pet.setExpectedHatchTime(new Date(NOW + 5 * DAY_MS));
        pet.setCreateDate(new Date(NOW - DAY_MS));
        pet.setMbti(mbti);
        return pet;
    }

    private PetEntity hatched(String mbti) {
        PetEntity pet = new PetEntity();
        pet.setId("pet-hatched-1");
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
        // 用破壳后宠物：孵化期蛋若同时临近破壳，新顺序下会优先兴奋
        PetEntity pet = hatched("INFP");
        // baseline=NOW-1d，但把 now 推到 5天后，inactive=6天
        assertThat(MoodDecider.decide(pet, NOW - 1 * DAY_MS, NOW + 5 * DAY_MS, TODAY)).isEqualTo(TodayMood.LOW);
    }

    @Test
    @DisplayName("inactive 2-3天 → 想念")
    void decide_inactive2to3Days_returnsMiss() {
        PetEntity pet = egg("INFP");
        assertThat(MoodDecider.decide(pet, NOW - 1 * DAY_MS, NOW + 2 * DAY_MS, TODAY)).isEqualTo(TodayMood.MISS);
    }

    @Test
    @DisplayName("孵化期临近预计破壳日(<=1天) → 兴奋")
    void decide_eggSoonHatch_returnsExcited() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-egg-soon");
        pet.setHatchStatus("EGG");
        pet.setExpectedHatchTime(new Date(NOW + 12 * 60 * 60 * 1000)); // 12小时后破壳
        pet.setCreateDate(new Date(NOW - 12 * 60 * 60 * 1000));
        pet.setMbti("INFP");
        long base = NOW - 12 * 60 * 60 * 1000; // 半天前开始，inactive=0
        pet.setHatchStartTime(new Date(base));
        assertThat(MoodDecider.decide(pet, base, NOW, TODAY)).isEqualTo(TodayMood.EXCITED);
    }

    @Test
    @DisplayName("孵化期已过预计破壳日仍未破壳 → 兴奋(准备破壳)")
    void decide_eggOverdue_returnsExcited() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-egg-overdue");
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(new Date(NOW - 12 * 60 * 60 * 1000)); // 半天前
        pet.setExpectedHatchTime(new Date(NOW - 60 * 1000)); // 1分钟前已到点
        pet.setCreateDate(new Date(NOW - 12 * 60 * 60 * 1000));
        pet.setMbti("INFP");
        long base = NOW - 12 * 60 * 60 * 1000;
        assertThat(MoodDecider.decide(pet, base, NOW, TODAY)).isEqualTo(TodayMood.EXCITED);
    }

    @Test
    @DisplayName("孵化期临近破壳 且 不活跃>=4天 → 仍为兴奋(顺序修复回归)")
    void decide_eggSoonHatchInactive4Days_stillExcited() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-egg-long");
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(new Date(NOW - 6 * DAY_MS)); // 孵化6天，基线陈旧
        pet.setExpectedHatchTime(new Date(NOW + 12 * 60 * 60 * 1000)); // 12小时后破壳
        pet.setCreateDate(new Date(NOW - 6 * DAY_MS));
        pet.setMbti("INFP");
        assertThat(MoodDecider.decide(pet, NOW - 6 * DAY_MS, NOW, TODAY)).isEqualTo(TodayMood.EXCITED);
    }

    @Test
    @DisplayName("12小时内有活跃 → 开心")
    void decide_recentInteraction_returnsHappy() {
        PetEntity pet = hatched("INFP");
        long base = NOW - 6 * 60 * 60 * 1000; // 6小时前
        assertThat(MoodDecider.decide(pet, base, NOW, TODAY)).isEqualTo(TodayMood.HAPPY);
    }

    @Test
    @DisplayName("加权池：同一 petId+日期多次判定结果一致(同日幂等)")
    void decide_weightedPool_idempotentSameDay() {
        PetEntity pet = hatched("INFP");
        long base = NOW - 18 * 60 * 60 * 1000; // >12h 且 <2d → 落入加权池
        TodayMood first = MoodDecider.decide(pet, base, NOW, TODAY);
        for (int i = 0; i < 10; i++) {
            assertThat(MoodDecider.decide(pet, base, NOW, TODAY)).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("加权池：E 池按种子区间选取(兴奋50/开心30/平静20)")
    void decide_weightedPool_mbtiE_followsSeedBucket() {
        PetEntity pet = hatched("ENFP");
        long base = NOW - 18 * 60 * 60 * 1000;
        // 遍历一年日期，覆盖三个权重区间，逐一验证选取正确
        for (int i = 0; i < 365; i++) {
            LocalDate day = TODAY.plusDays(i);
            int seed = MoodDecider.dailySeed(pet.getId(), day);
            TodayMood expected = seed < 50 ? TodayMood.EXCITED : (seed < 80 ? TodayMood.HAPPY : TodayMood.CALM);
            assertThat(MoodDecider.decide(pet, base, NOW, day))
                    .as("seed=%d day=%s", seed, day)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("加权池：I 池按种子区间选取(平静50/开心30/兴奋20)")
    void decide_weightedPool_mbtiI_followsSeedBucket() {
        PetEntity pet = hatched("INFP");
        long base = NOW - 18 * 60 * 60 * 1000;
        for (int i = 0; i < 365; i++) {
            LocalDate day = TODAY.plusDays(i);
            int seed = MoodDecider.dailySeed(pet.getId(), day);
            TodayMood expected = seed < 50 ? TodayMood.CALM : (seed < 80 ? TodayMood.HAPPY : TodayMood.EXCITED);
            assertThat(MoodDecider.decide(pet, base, NOW, day))
                    .as("seed=%d day=%s", seed, day)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("加权池：无 mbti 回退 开心50/平静50")
    void decide_weightedPool_noMbti_happyOrCalm() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-no-mbti");
        pet.setHatchStatus("HATCHED");
        pet.setHatchedAt(new Date(NOW - 18 * 60 * 60 * 1000));
        pet.setCreateDate(new Date(NOW - 8 * DAY_MS));
        long base = NOW - 18 * 60 * 60 * 1000;
        for (int i = 0; i < 365; i++) {
            LocalDate day = TODAY.plusDays(i);
            int seed = MoodDecider.dailySeed(pet.getId(), day);
            TodayMood expected = seed < 50 ? TodayMood.HAPPY : TodayMood.CALM;
            assertThat(MoodDecider.decide(pet, base, NOW, day))
                    .as("seed=%d day=%s", seed, day)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("加权池：跨日心情有变化，不再锁死单一心情")
    void decide_weightedPool_variesAcrossDays() {
        PetEntity pet = hatched("INFP");
        long base = NOW - 18 * 60 * 60 * 1000;
        Set<TodayMood> moods = new HashSet<>();
        for (int i = 0; i < 365; i++) {
            moods.add(MoodDecider.decide(pet, base, NOW, TODAY.plusDays(i)));
        }
        assertThat(moods.size()).isGreaterThanOrEqualTo(2);
    }
}
