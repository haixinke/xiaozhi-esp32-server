package xiaozhi.modules.storyengine.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialSceneTagRegistryTest {

    private final SpecialSceneTagRegistry registry = new SpecialSceneTagRegistry();

    @Test
    void windowTagAppliesOnlyToHomeBedroom() {
        assertThat(registry.specialTagOf("在家", "卧室")).contains("窗户");
    }

    @Test
    void otherSceneCombinationsHaveNoSpecialTag() {
        assertThat(registry.specialTagOf("在家", "客厅")).isEmpty();
        assertThat(registry.specialTagOf("旅行", "卧室")).isEmpty();
        assertThat(registry.specialTagOf("上学", "教室")).isEmpty();
    }

    @Test
    void nullAndBlankNamesNeverMatch() {
        assertThat(registry.specialTagOf(null, "卧室")).isEmpty();
        assertThat(registry.specialTagOf("在家", null)).isEmpty();
        assertThat(registry.specialTagOf("", "")).isEmpty();
    }
}
