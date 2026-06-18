package xiaozhi.modules.payment.wechat;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.AESUtils;
import xiaozhi.modules.sys.service.SysParamsService;

/**
 * 微信支付配置（运行时从 sys_params 加载）。
 *
 * <p>敏感字段（{@code privateKey} / {@code apiV3Key}）以 AES 加密存入 sys_params，
 * 加载时使用 {@link Constant#SERVER_SECRET} 作为密钥解密。</p>
 */
@Data
@Slf4j
public class WechatPayProperties {

    /** sys_params 参数 key 常量 */
    public static final String KEY_MCHID = "wechat.pay.mchid";
    public static final String KEY_SERIAL_NO = "wechat.pay.serial_no";
    public static final String KEY_PRIVATE_KEY = "wechat.pay.private_key";
    public static final String KEY_API_V3_KEY = "wechat.pay.api_v3_key";
    public static final String KEY_NOTIFY_URL = "wechat.pay.notify_url";
    public static final String KEY_APPID = "wechat.miniprogram.appid";

    /** 小程序 appid，与 wechat.miniprogram.appid 共用 */
    private String appid;

    /** 商户号 */
    private String mchid;

    /** 商户证书序列号 */
    private String serialNo;

    /** 商户私钥 PEM 字符串（解密后） */
    private String privateKey;

    /** APIv3 密钥（解密后） */
    private String apiV3Key;

    /** 回调 URL，公网 HTTPS */
    private String notifyUrl;

    /**
     * 从 sys_params 加载真实模式所需的全部配置。
     *
     * @param sysParamsService 参数服务
     * @return 配置对象
     * @throws RenException 必填字段缺失或加密字段解密失败
     */
    public static WechatPayProperties loadReal(SysParamsService sysParamsService) {
        WechatPayProperties p = new WechatPayProperties();
        p.setAppid(readPlain(sysParamsService, KEY_APPID, true));
        p.setMchid(readPlain(sysParamsService, KEY_MCHID, true));
        p.setSerialNo(readPlain(sysParamsService, KEY_SERIAL_NO, true));
        p.setNotifyUrl(readPlain(sysParamsService, KEY_NOTIFY_URL, true));

        String secret = readPlain(sysParamsService, Constant.SERVER_SECRET, true);
        p.setPrivateKey(readEncrypted(sysParamsService, KEY_PRIVATE_KEY, secret));
        p.setApiV3Key(readEncrypted(sysParamsService, KEY_API_V3_KEY, secret));

        if (!p.getNotifyUrl().toLowerCase().startsWith("https://")) {
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                    KEY_NOTIFY_URL + " 必须是 HTTPS 公网地址");
        }
        return p;
    }

    private static String readPlain(SysParamsService sysParamsService, String code, boolean required) {
        String value = sysParamsService.getValue(code, true);
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value)) {
            if (required) {
                throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                        "缺少配置项: " + code);
            }
            return null;
        }
        return value.trim();
    }

    /**
     * 读取并 AES 解密敏感字段。
     */
    private static String readEncrypted(SysParamsService sysParamsService, String code, String secret) {
        String cipher = readPlain(sysParamsService, code, true);
        try {
            return AESUtils.decrypt(secret, cipher);
        } catch (Exception e) {
            log.error("解密 sys_params[{}] 失败", code, e);
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE,
                    "配置项解密失败: " + code);
        }
    }
}
