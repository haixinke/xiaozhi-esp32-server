package xiaozhi.modules.pet.util;

import xiaozhi.modules.pet.constant.TodayMood;
import xiaozhi.modules.pet.entity.PetEntity;

import java.time.LocalDate;
import java.util.Date;

/**
 * 今日心情判定器（PRD §8.5：宠物阶段 + 性格/偏好 + 最近行为 + 用户活跃 + 随机）。
 *
 * <p>判定顺序：临近破壳兴奋 > 不活跃降级(低落/想念) > 近期活跃开心 > 每日种子加权池。</p>
 *
 * <p>活跃度基线优先级（由调用方选取后传入 baselineMs）：</p>
 * <ul>
 *   <li>最近一条用户聊天消息时间（真实互动，见 PetServiceImpl#resolveLastInteractionMs）</li>
 *   <li>无聊天记录时静态兜底：孵化期(EGG)用 hatchStartTime（无则 createDate）；破壳后(HATCHED)用 hatchedAt</li>
 * </ul>
 *
 * <p>兜底分支使用 hash(petId + 日期) 的每日种子在 MBTI 加权池中选取：
 * 同一天内结果幂等（防并发双写不一致），跨天自然变化，避免心情被硬映射锁死。</p>
 */
public final class MoodDecider {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long SOON_THRESHOLD_MS = DAY_MS;
    private static final long RECENT_INTERACTION_MS = 12L * 60 * 60 * 1000;

    /** 加权池：{心情, 权重}，权重合计 100 */
    private static final Object[][] POOL_E = {{TodayMood.EXCITED, 50}, {TodayMood.HAPPY, 30}, {TodayMood.CALM, 20}};
    private static final Object[][] POOL_I = {{TodayMood.CALM, 50}, {TodayMood.HAPPY, 30}, {TodayMood.EXCITED, 20}};
    private static final Object[][] POOL_DEFAULT = {{TodayMood.HAPPY, 50}, {TodayMood.CALM, 50}};

    private MoodDecider() {
    }

    /**
     * 判定今日心情。
     *
     * @param pet        宠物实体（读取 id/hatchStatus/expectedHatchTime/mbti）
     * @param baselineMs 活跃度基线时间戳（ms），由调用方按"最近聊天时间 > 静态兜底"选取
     * @param now        当前时间戳（ms）
     * @param today      今日日期（Asia/Shanghai），用于构造每日种子
     */
    public static TodayMood decide(PetEntity pet, long baselineMs, long now, LocalDate today) {
        boolean hatched = pet.getHatchStatus() != null && pet.getHatchStatus().equals("HATCHED");

        // 1. 孵化期临近预计破壳日(≤1天)或已过期未破壳 → 兴奋（优先于不活跃降级，孵化≥4天也能触发）
        if (!hatched && pet.getExpectedHatchTime() != null
                && pet.getExpectedHatchTime().getTime() - now <= SOON_THRESHOLD_MS) {
            return TodayMood.EXCITED;
        }

        // 2. 不活跃降级
        long inactiveMs = now - baselineMs;
        long inactiveDays = inactiveMs / DAY_MS;
        if (inactiveDays >= 4) {
            return TodayMood.LOW;
        }
        if (inactiveDays >= 2) {
            return TodayMood.MISS;
        }

        // 3. 12小时内有"活跃" → 开心
        if (inactiveMs >= 0 && inactiveMs < RECENT_INTERACTION_MS) {
            return TodayMood.HAPPY;
        }

        // 4. 每日种子加权池：性格决定倾向(E→偏兴奋, I→偏平静)，日期种子决定当天落点
        return pickBySeed(selectPool(pet.getMbti()), dailySeed(pet.getId(), today));
    }

    /** 每日种子：同一 petId+日期结果稳定，跨天变化，范围 [0, 100) */
    static int dailySeed(String petId, LocalDate today) {
        return Math.floorMod((petId + "|" + today).hashCode(), 100);
    }

    private static Object[][] selectPool(String mbti) {
        if (mbti != null && !mbti.isEmpty()) {
            char first = mbti.charAt(0);
            if (first == 'E') {
                return POOL_E;
            }
            if (first == 'I') {
                return POOL_I;
            }
        }
        return POOL_DEFAULT;
    }

    /** 按累计权重区间选取：seed ∈ [0, 100) 落在哪个区间取哪个心情 */
    private static TodayMood pickBySeed(Object[][] pool, int seed) {
        int cumulative = 0;
        for (Object[] entry : pool) {
            cumulative += (Integer) entry[1];
            if (seed < cumulative) {
                return (TodayMood) entry[0];
            }
        }
        return (TodayMood) pool[pool.length - 1][0];
    }

    /**
     * 静态兜底基线（无聊天记录时使用）：孵化期用 hatchStartTime(无则 createDate)，破壳后用 hatchedAt(无则 createDate)。
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
