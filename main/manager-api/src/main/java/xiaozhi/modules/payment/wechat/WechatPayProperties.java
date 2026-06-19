package xiaozhi.modules.payment.wechat;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;

/**
 * 微信支付配置（运行时从环境变量加载）。
 *
 * <p>在 SAE 等云原生环境中，敏感字段应通过保密字典（Secret）以环境变量形式注入，
 * 不再存储到数据库 {@code sys_params} 表中。</p>
 */
@Data
public class WechatPayProperties {

    /** 环境变量名常量 */
    public static final String ENV_MCHID = "WECHAT_PAY_MCHID";
    public static final String ENV_SERIAL_NO = "WECHAT_PAY_SERIAL_NO";
    public static final String ENV_PRIVATE_KEY = "WECHAT_PAY_PRIVATE_KEY";
    public static final String ENV_API_V3_KEY = "WECHAT_PAY_API_V3_KEY";
    public static final String ENV_NOTIFY_URL = "WECHAT_PAY_NOTIFY_URL";
    public static final String ENV_APPID = "WECHAT_MINIPROGRAM_APPID";

    /** 小程序 appid */
    private String appid;

    /** 商户号 */
    private String mchid;

    /** 商户证书序列号 */
    private String serialNo;

    /** 商户私钥 PEM 字符串 */
    private String privateKey;

    /** APIv3 密钥 */
    private String apiV3Key;

    /** 回调 URL，公网 HTTPS */
    private String notifyUrl;

    /**
     * 从环境变量加载真实模式所需的全部配置。
     *
     * @return 配置对象
     * @throws RenException 必填字段缺失
     */
    public static WechatPayProperties loadReal() {
        WechatPayProperties p = new WechatPayProperties();
        p.setAppid(readEnv(ENV_APPID, true));
        p.setMchid(readEnv(ENV_MCHID, true));
        p.setSerialNo(readEnv(ENV_SERIAL_NO, true));
        p.setNotifyUrl(readEnv(ENV_NOTIFY_URL, true));
        p.setPrivateKey(readEnv(ENV_PRIVATE_KEY, true));
        p.setApiV3Key(readEnv(ENV_API_V3_KEY, true));

        if (!p.getNotifyUrl().toLowerCase().startsWith("https://")) {
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                    ENV_NOTIFY_URL + " 必须是 HTTPS 公网地址");
        }
        return p;
    }

    private static String readEnv(String name, boolean required) {
        String value = System.getenv(name);
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value)) {
            if (required) {
                throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                        "缺少环境变量: " + name);
            }
            return null;
        }
        return value.trim();
    }
}
