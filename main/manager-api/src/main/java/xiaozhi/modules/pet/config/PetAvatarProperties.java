package xiaozhi.modules.pet.config;

import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 蛋宝宝 AI 宠物默认头像配置。
 *
 * <p>按原型（锦鲤/玉兔）配置 OSS 基础 URL、文件名前缀与数量，破壳时随机生成
 * {@code baseUrl + prefix + "-" + index + ".png"} 形式的头像 URL。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pet.avatar")
public class PetAvatarProperties {

    /**
     * 锦鲤默认头像池配置。
     */
    private Prototype koi;

    /**
     * 玉兔默认头像池配置。
     */
    private Prototype rabbit;

    /**
     * 配置缺失或数量为零时的兜底头像 URL。
     */
    private String fallbackUrl;

    /**
     * 按原型随机选择一张默认头像 URL。
     *
     * @param prototype 宠物原型，当前支持 "锦鲤" / "玉兔"
     * @return 头像 URL；配置不可用时返回 fallbackUrl，fallbackUrl 也未配置时返回空字符串
     */
    public String randomAvatarUrl(String prototype) {
        Prototype config = selectConfig(prototype);
        if (config == null || !config.hasImage()) {
            return fallbackUrl == null ? "" : fallbackUrl;
        }
        int index = ThreadLocalRandom.current().nextInt(config.getCount());
        return config.getBaseUrl() + config.getPrefix() + "-" + index + ".png";
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
     * 单个原型的头像池配置。
     */
    @Data
    public static class Prototype {

        /**
         * OSS 基础 URL，需以斜杠结尾。
         */
        private String baseUrl;

        /**
         * 文件名前缀，例如 fish / rabbit。
         */
        private String prefix;

        /**
         * 可用头像数量，用于生成 [0, count) 的随机 index。
         */
        private int count;

        /**
         * 配置是否包含至少一张可用头像。
         */
        public boolean hasImage() {
            return baseUrl != null && !baseUrl.isBlank()
                    && prefix != null && !prefix.isBlank()
                    && count > 0;
        }
    }
}
