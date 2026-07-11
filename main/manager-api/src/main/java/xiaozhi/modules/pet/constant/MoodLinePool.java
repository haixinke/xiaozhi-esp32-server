package xiaozhi.modules.pet.constant;

import java.util.List;
import java.util.Map;

/**
 * 今日心情文案池（PRD §8.7：孵化期/破壳后两套独立文案池，同一心情类型下分阶段）。
 * 文案 ≤20 字、拟人、不鸡汤（PRD §8.8）。
 * 选句按当日日期确定性取（同一只蛋同一天取同一句），保证刷新前后一致。
 */
public final class MoodLinePool {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** 孵化期文案池（对象是蛋：壳里的动静、等待、被照顾、即将破壳）。 */
    private static final Map<TodayMood, List<String>> EGG_LINES = Map.of(
            TodayMood.HAPPY, List.of(
                    "蛋壳里传来轻轻的回应。",
                    "它把你的声音藏进了壳里。",
                    "壳里暖了一下，像在笑。"),
            TodayMood.CALM, List.of(
                    "蛋壳里很安静，但很暖。",
                    "它今天睡得很踏实。",
                    "壳纹慢慢稳了下来。"),
            TodayMood.MISS, List.of(
                    "它好像等了你很久。",
                    "它把想你藏在壳里。",
                    "壳里轻轻动了一下，像在张望。"),
            TodayMood.EXCITED, List.of(
                    "蛋壳里的动静变多了。",
                    "裂纹好像又亮了一点。",
                    "它在壳里翻了个身。"),
            TodayMood.LOW, List.of(
                    "它今天安静了很久。",
                    "蛋壳里的声音变小了。",
                    "壳纹暗了一点点。")
    );

    /** 破壳后文案池（对象是宠物：心情、行为、想念、今天在做什么）。 */
    private static final Map<TodayMood, List<String>> PET_LINES = Map.of(
            TodayMood.HAPPY, List.of(
                    "它把快乐摆在了脸上。",
                    "它好像一直在等你来。",
                    "它今天比平时轻快。"),
            TodayMood.CALM, List.of(
                    "它今天把日子过得很慢。",
                    "它安静地待在你身边。",
                    "它把今天过得很安稳。"),
            TodayMood.MISS, List.of(
                    "它偷偷练习了怎么叫你。",
                    "它把想你写进了今天。",
                    "它一直看着门口的方向。"),
            TodayMood.EXCITED, List.of(
                    "它好像准备了一个小秘密。",
                    "它今天比平时更坐不住。",
                    "它转了好几个圈。"),
            TodayMood.LOW, List.of(
                    "它今天有一点点没精神。",
                    "它安静了很久，像是在等你。",
                    "它把尾巴收了起来。")
    );

    private MoodLinePool() {
    }

    /**
     * 按阶段与心情取一句，同一日期种子取同一句（确定性）。
     *
     * @param hatched  true=破壳后，false=孵化期
     * @param mood     心情类型
     * @param dateSeed 日期种子（如 "2026-07-11"），保证当日一致
     */
    public static String pick(boolean hatched, TodayMood mood, String dateSeed) {
        Map<TodayMood, List<String>> pool = hatched ? PET_LINES : EGG_LINES;
        TodayMood key = mood != null ? mood : TodayMood.HAPPY;
        List<String> lines = pool.getOrDefault(key, pool.get(TodayMood.HAPPY));
        int idx = Math.floorMod(dateSeed.hashCode(), lines.size());
        return lines.get(idx);
    }

    public static long dayMillis() {
        return DAY_MS;
    }
}
