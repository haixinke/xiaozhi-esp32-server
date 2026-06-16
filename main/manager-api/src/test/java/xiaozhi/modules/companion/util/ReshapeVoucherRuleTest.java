package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xiaozhi.modules.companion.entity.CompanionEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReshapeVoucherRule 扣券决策")
class ReshapeVoucherRuleTest {

    // character 置首位，与 after(character, occupation, ...) 顺序一致
    private CompanionEntity entity(String character, String occupation, String soulTraits, String soulQuirk, String voice) {
        CompanionEntity e = new CompanionEntity();
        e.setCharacter(character);
        e.setOccupation(occupation);
        e.setSoulTraits(soulTraits);
        e.setSoulQuirk(soulQuirk);
        e.setVoice(voice);
        return e;
    }

    @Test
    @DisplayName("什么都不改 -> 不扣券")
    void noChange_noVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"), null);
        assertThat(skus).isEmpty();
    }

    @Test
    @DisplayName("改角色 -> 扣 role_change")
    void characterChange_consumesRole() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", null, null, null),
                ReshapeVoucherRule.after("linjiamei", null, null, null, null));
        assertThat(skus).containsExactly("role_change");
    }

    @Test
    @DisplayName("角色与旧值相同（传了但未变）-> 不扣券")
    void characterSameAsBefore_noVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", null, null, null),
                ReshapeVoucherRule.after("baiyueguang", null, null, null, null));
        assertThat(skus).isEmpty();
    }

    @Test
    @DisplayName("改职业 -> 扣 occupation_change")
    void occupationChange_consumesOccupation() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, "music", null, null, null));
        assertThat(skus).containsExactly("occupation_change");
    }

    @Test
    @DisplayName("改灵魂特质（小任性不变）-> 扣 soul_quirk_change")
    void soulTraitsChange_consumesSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, "clingy,flirty", null, null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("改小任性 -> 扣 soul_quirk_change")
    void soulQuirkChange_consumesSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, null, "grumpyMorning", null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("同时改灵魂特质和小任性 -> 只扣 1 张 soul_quirk_change")
    void bothSoulFieldsChange_consumesOneSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, "clingy,flirty", "grumpyMorning", null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("改声音 -> 扣 voice_change")
    void voiceChange_consumesVoice() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, null, null, "v2"));
        assertThat(skus).containsExactly("voice_change");
    }

    @Test
    @DisplayName("角色与职业同时变化 -> 各扣一张")
    void characterAndOccupationChange_consumesBoth() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", null, null, null),
                ReshapeVoucherRule.after("linjiamei", "music", null, null, null));
        assertThat(skus).containsExactly("role_change", "occupation_change");
    }

    @Test
    @DisplayName("四项全改 -> 扣四张，顺序 角色->职业->性格->声音")
    void allChange_consumesFourInOrder() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after("erciyuan", "music", "clingy,flirty", "grumpyMorning", "v2"));
        assertThat(skus).containsExactly("role_change", "occupation_change", "soul_quirk_change", "voice_change");
    }

    @Test
    @DisplayName("有任意券要扣 -> 需要同步 agent")
    void anyConsume_needsAgentSync() {
        assertThat(ReshapeVoucherRule.needsAgentSync(
                ReshapeVoucherRule.decide(entity("baiyueguang", "camera", null, null, null),
                        ReshapeVoucherRule.after("linjiamei", null, null, null, null)))).isTrue();
        assertThat(ReshapeVoucherRule.needsAgentSync(
                ReshapeVoucherRule.decide(entity("baiyueguang", "camera", null, null, null), null))).isFalse();
    }
}
