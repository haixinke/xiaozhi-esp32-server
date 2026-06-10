package xiaozhi.modules.payment.wechat;

import lombok.Data;

/**
 * 微信支付配置（运行时从 sys_params 加载）
 */
@Data
public class WechatPayConfig {

    /** 是否启用 mock 模式（无证书时本地联调用） */
    private boolean mock = true;

    /** 小程序 appid，与 wechat.miniprogram.appid 共用 */
    private String appid;

    /** 商户号 */
    private String mchid;

    /** 商户证书序列号 */
    private String serialNo;

    /** 商户私钥 PEM 字符串（解密后） */
    private String privateKey;

    /** APIv3 密钥 */
    private String apiV3Key;

    /** 回调URL */
    private String notifyUrl;
}
