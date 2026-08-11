package xiaozhi.common.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 阿里云OSS配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    private String bucketName;

    private String region;

    /**
     * OSS 公网访问域名，例如 https://oss.eggbabe.com。用于把 ossKey 拼接成可访问 URL。
     * 留空时由 OssService 兜底到默认域名，保证向后兼容。
     */
    private String publicUrl;

    /**
     * 判断 OSS 是否已配置完整
     */
    public boolean isConfigured() {
        return StringUtils.isNoneBlank(endpoint, accessKeyId, accessKeySecret, bucketName, region);
    }
}
