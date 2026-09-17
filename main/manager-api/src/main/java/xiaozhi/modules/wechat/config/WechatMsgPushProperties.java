package xiaozhi.modules.wechat.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序「消息推送」配置。
 *
 * <p>对应 mp 后台「开发管理-消息推送」中配置的 Token 与 EncodingAESKey（安全模式），
 * 用于回调端点验签与消息体 AES 解密。未配置时回调端点拒绝处理。
 */
@Data
@Component
@ConfigurationProperties(prefix = "eggbaby.miniprogram.msgpush")
public class WechatMsgPushProperties {

    /** 消息推送 Token（mp 后台自定义） */
    private String token;

    /** 消息推送 EncodingAESKey（43 位字符） */
    private String aesKey;

    /**
     * 判断是否已完整配置
     */
    public boolean isConfigured() {
        return StringUtils.isNoneBlank(token, aesKey);
    }
}
