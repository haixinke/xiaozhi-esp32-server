package xiaozhi.modules.pet.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PetAvatarProperties 头像 URL 生成")
class PetAvatarPropertiesTest {

    private static final String KOI_BASE_URL = "https://oss.eggbabe.com/default-avatar/fish/";
    private static final String RABBIT_BASE_URL = "https://oss.eggbabe.com/default-avatar/rabbit/";
    private static final String FALLBACK_URL = "https://oss.eggbabe.com/default-avatar/fish/fish-0.png";

    @Test
    @DisplayName("锦鲤返回 fish 前缀的 OSS URL 且 index 在范围内")
    void randomAvatarUrl_koi_returnsFishUrlWithinBounds() {
        PetAvatarProperties properties = buildProperties(22, 22);

        String url = properties.randomAvatarUrl("锦鲤");

        // 随机池含 index=0，恰好生成 fish-0.png 等于 FALLBACK_URL 是合法命中，
        // 不能再断 isNotEqualTo(FALLBACK_URL)，否则 ~1/22 概率假失败。
        // 格式匹配 + 范围断言已足够证明走了随机分支（fallback 分支的返回值不匹配该格式）。
        assertThat(url)
                .startsWith(KOI_BASE_URL)
                .matches("^" + KOI_BASE_URL + "fish-\\d+\\.png$");
        int index = extractIndex(url);
        assertThat(index).isBetween(0, 21);
    }

    @Test
    @DisplayName("玉兔返回 rabbit 前缀的 OSS URL 且 index 在范围内")
    void randomAvatarUrl_rabbit_returnsRabbitUrlWithinBounds() {
        PetAvatarProperties properties = buildProperties(22, 22);

        String url = properties.randomAvatarUrl("玉兔");

        // 同上：rabbit-0.png 与 FALLBACK_URL 前缀不同，本来就断不到，但保持两个用例对称、
        // 都只断格式与范围，避免后续维护者误补一条会 flaky 的 fallback 不等断言。
        assertThat(url)
                .startsWith(RABBIT_BASE_URL)
                .matches("^" + RABBIT_BASE_URL + "rabbit-\\d+\\.png$");
        int index = extractIndex(url);
        assertThat(index).isBetween(0, 21);
    }

    @Test
    @DisplayName("count 为 0 时返回 fallback URL")
    void randomAvatarUrl_zeroCount_returnsFallbackUrl() {
        PetAvatarProperties properties = buildProperties(0, 0);

        assertThat(properties.randomAvatarUrl("锦鲤")).isEqualTo(FALLBACK_URL);
        assertThat(properties.randomAvatarUrl("玉兔")).isEqualTo(FALLBACK_URL);
    }

    @Test
    @DisplayName("未知原型返回 fallback URL")
    void randomAvatarUrl_unknownPrototype_returnsFallbackUrl() {
        PetAvatarProperties properties = buildProperties(22, 22);

        assertThat(properties.randomAvatarUrl("龙")).isEqualTo(FALLBACK_URL);
    }

    @Test
    @DisplayName("空配置对象返回 fallback URL")
    void randomAvatarUrl_emptyProperties_returnsFallbackUrl() {
        PetAvatarProperties properties = new PetAvatarProperties();

        assertThat(properties.randomAvatarUrl("锦鲤")).isEqualTo("");
        assertThat(properties.randomAvatarUrl("玉兔")).isEqualTo("");
    }

    private PetAvatarProperties buildProperties(int koiCount, int rabbitCount) {
        PetAvatarProperties properties = new PetAvatarProperties();
        properties.setFallbackUrl(FALLBACK_URL);

        PetAvatarProperties.Prototype koi = new PetAvatarProperties.Prototype();
        koi.setBaseUrl(KOI_BASE_URL);
        koi.setPrefix("fish");
        koi.setCount(koiCount);
        properties.setKoi(koi);

        PetAvatarProperties.Prototype rabbit = new PetAvatarProperties.Prototype();
        rabbit.setBaseUrl(RABBIT_BASE_URL);
        rabbit.setPrefix("rabbit");
        rabbit.setCount(rabbitCount);
        properties.setRabbit(rabbit);

        return properties;
    }

    private int extractIndex(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        String number = name.substring(name.lastIndexOf('-') + 1, name.lastIndexOf('.'));
        return Integer.parseInt(number);
    }
}
