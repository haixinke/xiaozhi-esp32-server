package xiaozhi.modules.pet.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山引擎 Seedream 文生图配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "seedream")
public class SeedreamProperties {

    private String key;

    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    private String model = "doubao-seedream-5-0-pro-260628";

    private String size = "1024x1536";

    /**
     * 判断 Seedream 是否已配置完整
     */
    public boolean isConfigured() {
        return StringUtils.isNoneBlank(key, baseUrl, model);
    }
}
