package xiaozhi.modules.companion.util;

import xiaozhi.modules.companion.entity.CompanionEntity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 「重塑命运」扣券决策（纯函数）。
 *
 * <p>规则（与产品决策一致）：
 * <ul>
 *   <li>职业变化 -> 扣 occupation_change</li>
 *   <li>灵魂特质 或 小任性 任一变化 -> 扣 1 张 soul_quirk_change</li>
 *   <li>声音变化 -> 扣 voice_change</li>
 * </ul>
 * 「传了某字段但与旧值相同」不算变化、不扣券。
 */
public final class ReshapeVoucherRule {

    public static final String OCCUPATION_CHANGE = "occupation_change";
    public static final String SOUL_QUIRK_CHANGE = "soul_quirk_change";
    public static final String VOICE_CHANGE = "voice_change";

    private ReshapeVoucherRule() {
    }

    /** 决定本次 update 需要消耗的券（保持「职业->性格->声音」顺序）。after 可为 null。 */
    public static Set<String> decide(CompanionEntity before, After after) {
        Set<String> skus = new LinkedHashSet<>();
        if (after == null) {
            return skus;
        }
        if (changed(after.occupation, before.getOccupation())) {
            skus.add(OCCUPATION_CHANGE);
        }
        boolean soulTraitsChanged = changed(after.soulTraits, before.getSoulTraits());
        boolean soulQuirkChanged = changed(after.soulQuirk, before.getSoulQuirk());
        if (soulTraitsChanged || soulQuirkChanged) {
            skus.add(SOUL_QUIRK_CHANGE);
        }
        if (changed(after.voice, before.getVoice())) {
            skus.add(VOICE_CHANGE);
        }
        return skus;
    }

    /** 职业或性格或声音任一变化，都需要重新同步 agent 系统提示词与 TTS 音色。 */
    public static boolean needsAgentSync(Set<String> skus) {
        return skus != null && !skus.isEmpty();
    }

    private static boolean changed(String after, String before) {
        return after != null && !after.equals(before);
    }

    /** update DTO 的投影，避免把整个 DTO 带入纯函数。null 表示「不改」。 */
    public static After after(String occupation, String soulTraits, String soulQuirk, String voice) {
        return new After(occupation, soulTraits, soulQuirk, voice);
    }

    public static final class After {
        private final String occupation;
        private final String soulTraits;
        private final String soulQuirk;
        private final String voice;

        private After(String occupation, String soulTraits, String soulQuirk, String voice) {
            this.occupation = occupation;
            this.soulTraits = soulTraits;
            this.soulQuirk = soulQuirk;
            this.voice = voice;
        }
    }
}
