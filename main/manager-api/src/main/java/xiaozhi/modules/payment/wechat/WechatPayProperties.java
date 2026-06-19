package xiaozhi.modules.payment.wechat;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;

import java.net.MalformedURLException;
import java.net.URL;

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
    public static final String ENV_PUB_KEY_ID = "WECHAT_PAY_PUB_KEY_ID";
    public static final String ENV_PUB_KEY = "WECHAT_PAY_PUB_KEY";
    public static final String ENV_NOTIFY_URL = "WECHAT_PAY_NOTIFY_URL";
    public static final String ENV_APPID = "WECHAT_MINIPROGRAM_APPID";

    /** 小程序 appid */
    private String appid;

    /** 商户号 */
    private String mchid;

    /** 商户证书序列号 */
    private String serialNo;

    /** 商户私钥 PEM 字符串 */
    @Getter(AccessLevel.PACKAGE)
    @ToString.Exclude
    private String privateKey;

    /** APIv3 密钥 */
    @Getter(AccessLevel.PACKAGE)
    @ToString.Exclude
    private String apiV3Key;

    /** 微信支付公钥 ID */
    private String pubKeyId;

    /** 微信支付公钥 PEM 字符串 */
    @Getter(AccessLevel.PACKAGE)
    @ToString.Exclude
    private String pubKey;

    /** 回调 URL，公网 HTTPS */
    @Getter(AccessLevel.PACKAGE)
    @ToString.Exclude
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
        p.setPubKeyId(readEnv(ENV_PUB_KEY_ID, true));
        p.setPubKey(readEnv(ENV_PUB_KEY, true));

        validateNotifyUrl(p.getNotifyUrl());
        return p;
    }

    private static void validateNotifyUrl(String notifyUrl) {
        URL url;
        try {
            url = new URL(notifyUrl);
        } catch (MalformedURLException e) {
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                    ENV_NOTIFY_URL + " 格式无效");
        }

        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                    ENV_NOTIFY_URL + " 必须使用 HTTPS 协议");
        }

        String host = url.getHost().toLowerCase();
        if (host.isEmpty() || isPrivateOrLocalHost(host)) {
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                    ENV_NOTIFY_URL + " 必须是公网域名，不能是本地或内网地址");
        }
    }

    private static boolean isPrivateOrLocalHost(String host) {
        return host.equals("localhost")
                || host.startsWith("127.")
                || host.equals("0.0.0.0")
                || host.equals("::1")
                || host.equals("[::1]")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || isRFC1918_172(host);
    }

    private static boolean isRFC1918_172(String host) {
        if (!host.startsWith("172.")) {
            return false;
        }
        String[] parts = host.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
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
