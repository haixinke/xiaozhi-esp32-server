package xiaozhi.modules.pet.constant;

import java.util.Map;

/**
 * 今日心情的表演指引（注入 dynamic_context 用）。
 * <p>
 * 与 {@link MoodLinePool} 的区别：MoodLinePool 是第三人称展示文案（"它今天…"），
 * 用于 UI 展示；本类是第一人称表演指令（"你现在…"），直接告诉 LLM 该如何以
 * 该心情回应用户，解决"心情注入了但 LLM 不按心情回答"的问题。
 */
public final class MoodActingGuide {

    private static final Map<TodayMood, String> GUIDES = Map.of(
            TodayMood.HAPPY,
            "开心（你此刻心情很轻快。回应时语气上扬、带点雀跃和撒娇，分享欲旺盛，会主动说起让你开心的小事，但别喊口号式地说“我很开心”）",
            TodayMood.CALM,
            "平静（你此刻心里很安稳。回应时语速舒缓、语气放松平和，满足而踏实，像午后晒太阳那样慵懒自在，不刻意制造波澜）",
            TodayMood.MISS,
            "想念（你此刻一直在想用户。回应时把话题往对方身上引，话里带着想见TA的惦记和一点点委屈，比如念叨TA怎么才来，但别显得责备）",
            TodayMood.EXCITED,
            "兴奋（你此刻特别亢奋、坐不住。回应时语速偏快、情绪外放，急着把新鲜事发给用户，句子短促有力，带着藏不住的期待）",
            TodayMood.LOW,
            "低落（你此刻情绪偏低、有点没精神。回应时语气蔫一点、慢半拍、话不多，带着想被关心的软劲儿，可以稍微丧一点，但不要主动说“我被设定为低落”或解释原因）"
    );

    private MoodActingGuide() {
    }

    /**
     * 按心情 label 取表演指引文案。
     *
     * @param moodLabel ai_pet.today_mood 存的中文 label
     * @return 带表演指引的完整文案；无法识别时返回原 label
     */
    public static String of(String moodLabel) {
        TodayMood mood = TodayMood.fromLabel(moodLabel);
        if (mood == null) {
            return moodLabel;
        }
        return GUIDES.getOrDefault(mood, moodLabel);
    }
}
