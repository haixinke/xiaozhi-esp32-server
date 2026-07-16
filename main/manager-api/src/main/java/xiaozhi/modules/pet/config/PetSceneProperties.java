package xiaozhi.modules.pet.config;

import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 蛋宝宝 AI 宠物默认场景图配置。
 *
 * <p>按原型（锦鲤/玉兔）配置 OSS 基础 URL、文件名前缀与数量，破壳时随机生成
 * {@code baseUrl + prefix + "-" + index + ".jpg"} 形式的场景图 URL。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pet.scene")
public class PetSceneProperties {

    /**
     * 锦鲤默认场景图池配置。
     */
    private Prototype koi;

    /**
     * 玉兔默认场景图池配置。
     */
    private Prototype rabbit;

    /**
     * 配置缺失或数量为零时的兜底场景图 URL。
     */
    private String fallbackUrl;

    /**
     * 按原型随机选择一张默认场景图 URL。
     *
     * @param prototype 宠物原型，当前支持 "锦鲤" / "玉兔"
     * @return 场景图 URL；配置不可用时返回 fallbackUrl，fallbackUrl 也未配置时返回空字符串
     */
    public String randomSceneUrl(String prototype) {
        Prototype config = selectConfig(prototype);
        if (config == null || !config.hasImage()) {
            return fallbackUrl == null ? "" : fallbackUrl;
        }
        int index = ThreadLocalRandom.current().nextInt(config.getCount());
        return config.getBaseUrl() + config.getPrefix() + "-" + index + ".jpg";
    }

    /**
     * 按原型递增选择下一张场景图 URL。
     * <p>从当前 URL 中提取索引，计算 (index + 1) % count 作为下一张。
     * 若当前 URL 为空、格式异常或 count <= 1，降级为随机选择。
     *
     * @param prototype  宠物原型，当前支持 "锦鲤" / "玉兔"
     * @param currentUrl 当前场景图 URL
     * @return 下一张场景图 URL
     */
    public String nextSceneUrl(String prototype, String currentUrl) {
        Prototype config = selectConfig(prototype);
        if (config == null || !config.hasImage() || config.getCount() <= 1) {
            return randomSceneUrl(prototype);
        }
        if (currentUrl == null || currentUrl.isBlank()) {
            return randomSceneUrl(prototype);
        }
        // 用 prefix 提取当前索引，如 scenes-fish-3.jpg → 3
        String marker = config.getPrefix() + "-";
        int markerPos = currentUrl.lastIndexOf(marker);
        if (markerPos < 0) {
            return randomSceneUrl(prototype);
        }
        int startIndex = markerPos + marker.length();
        int dotPos = currentUrl.indexOf('.', startIndex);
        if (dotPos < 0) {
            return randomSceneUrl(prototype);
        }
        try {
            int currentIndex = Integer.parseInt(currentUrl.substring(startIndex, dotPos));
            int nextIndex = (currentIndex + 1) % config.getCount();
            return config.getBaseUrl() + config.getPrefix() + "-" + nextIndex + ".jpg";
        } catch (NumberFormatException e) {
            return randomSceneUrl(prototype);
        }
    }

    private Prototype selectConfig(String prototype) {
        if ("锦鲤".equals(prototype)) {
            return koi;
        }
        if ("玉兔".equals(prototype)) {
            return rabbit;
        }
        return null;
    }

    /**
     * 单个原型的场景图池配置。
     */
    @Data
    public static class Prototype {

        /**
         * OSS 基础 URL，需以斜杠结尾。
         */
        private String baseUrl;

        /**
         * 文件名前缀，例如 scenes-fish / scenes-rabbit。
         */
        private String prefix;

        /**
         * 可用场景图数量，用于生成 [0, count) 的随机 index。
         */
        private int count;

        /**
         * 配置是否包含至少一张可用场景图。
         */
        public boolean hasImage() {
            return baseUrl != null && !baseUrl.isBlank()
                    && prefix != null && !prefix.isBlank()
                    && count > 0;
        }
    }
}
