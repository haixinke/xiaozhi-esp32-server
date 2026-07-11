package xiaozhi.modules.pet.util;

import xiaozhi.modules.pet.constant.TodayMood;
import xiaozhi.modules.pet.entity.PetEntity;

import java.util.Date;

/**
 * 今日心情判定器（PRD §8.5：宠物阶段 + 性格/偏好 + 最近行为 + 用户活跃 + 随机）。
 * 复刻前端 pet-store.getDailyStatus 的判定逻辑，按后端可得的活跃度信号适配。
 *
 * <p>活跃度基线：</p>
 * <ul>
 *   <li>孵化期(EGG)：hatchStartTime（无则 createDate）——开始孵化的时刻</li>
 *   <li>破壳后(HATCHED)：hatchedAt——破壳时刻（MVP 兜底，后续可接 chat-history 最近消息时间）</li>
 * </ul>
 */
public final class MoodDecider {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long SOON_THRESHOLD_MS = DAY_MS;
    private static final long RECENT_INTERACTION_MS = 12L * 60 * 60 * 1000;

    private MoodDecider() {
    }

    /**
     * 判定今日心情。
     *
     * @param pet        宠物实体（读取 hatchStatus/expectedHatchTime/hatchedAt/hatchStartTime/createDate/mbti）
     * @param baselineMs 活跃度基线时间戳（ms），由调用方按阶段选取
     * @param now        当前时间戳（ms）
     */
    public static TodayMood decide(PetEntity pet, long baselineMs, long now) {
        boolean hatched = pet.getHatchStatus() != null && pet.getHatchStatus().equals("HATCHED");
        long inactiveMs = now - baselineMs;
        long inactiveDays = inactiveMs / DAY_MS;

        if (inactiveDays >= 4) {
            return TodayMood.LOW;
        }
        if (inactiveDays >= 2) {
            return TodayMood.MISS;
        }

        // 孵化期：临近预计破壳日(≤1天) → 兴奋
        if (!hatched && pet.getExpectedHatchTime() != null) {
            long remain = pet.getExpectedHatchTime().getTime() - now;
            if (remain >= 0 && remain <= SOON_THRESHOLD_MS) {
                return TodayMood.EXCITED;
            }
            if (remain < 0) {
                // 已到/过预计破壳日仍未破壳 → 兴奋(准备破壳)
                return TodayMood.EXCITED;
            }
        }

        // 12小时内有"活跃" → 开心
        if (now - baselineMs < RECENT_INTERACTION_MS && inactiveMs >= 0) {
            return TodayMood.HAPPY;
        }

        // 软分桶：破壳后用 mbti 倾向（E→兴奋, I→平静）；无则回退随机开心/平静
        TodayMood soft = softByPersonality(pet);
        if (soft != null) {
            return soft;
        }
        return (baselineMs % 2 == 0) ? TodayMood.HAPPY : TodayMood.CALM;
    }

    private static TodayMood softByPersonality(PetEntity pet) {
        String mbti = pet.getMbti();
        if (mbti == null || mbti.length() < 1) {
            return null;
        }
        char first = mbti.charAt(0);
        if (first == 'E') {
            return TodayMood.EXCITED;
        }
        if (first == 'I') {
            return TodayMood.CALM;
        }
        return null;
    }

    /**
     * 选取活跃度基线时间戳：孵化期用 hatchStartTime(无则 createDate)，破壳后用 hatchedAt(无则 createDate)。
     */
    public static long baseline(PetEntity pet, long fallbackNow) {
        boolean hatched = pet.getHatchStatus() != null && pet.getHatchStatus().equals("HATCHED");
        if (hatched) {
            Date h = pet.getHatchedAt();
            return h != null ? h.getTime() : fallbackBase(pet, fallbackNow);
        }
        Date hs = pet.getHatchStartTime();
        return hs != null ? hs.getTime() : fallbackBase(pet, fallbackNow);
    }

    private static long fallbackBase(PetEntity pet, long fallbackNow) {
        Date c = pet.getCreateDate();
        return c != null ? c.getTime() : fallbackNow;
    }
}
