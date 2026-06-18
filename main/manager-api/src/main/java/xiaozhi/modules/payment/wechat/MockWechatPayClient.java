package xiaozhi.modules.payment.wechat;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import xiaozhi.modules.sys.service.SysParamsService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付客户端 Mock 实现（仅本地联调/测试使用）。
 *
 * <p><b>重要安全提示：</b>该 Bean 仅在同时满足以下两个条件才会被加载：
 * <ul>
 *   <li>配置 {@code wechat.pay.mock=true}（默认 false，生产环境必须保持 false）</li>
 *   <li>不存在其他 {@link WechatPayClient} 的 {@code @Primary} 实现</li>
 * </ul>
 * 在生产环境使用时，请提供真实的 V3 SDK 实现并带 {@code @Primary}。
 * 启动期 {@code WechatPayClientStartupGuard} 会在检测到 mock 客户端 + prod profile 同时生效时拒绝启动。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "wechat.pay.mock", havingValue = "true")
@ConditionalOnMissingBean(name = "wechatPayV3Client")
@RequiredArgsConstructor
public class MockWechatPayClient implements WechatPayClient {

    private final SysParamsService sysParamsService;

    @Value("${wechat.miniprogram.appid:}")
    private String appidFallback;

    @Override
    public boolean isMockMode() {
        return true;
    }

    @Override
    public String getAppid() {
        String appid = safeGetParam("wechat.miniprogram.appid");
        return appid != null && !appid.isBlank() ? appid : appidFallback;
    }

    @Override
    public PrepayResult jsapiPrepay(PrepayRequest request) {
        log.info("[mock-wechat-pay] jsapiPrepay outTradeNo={}, amountFen={}, openid={}",
                request.getOutTradeNo(), request.getAmountFen(), request.getOpenid());

        String prepayId = "mock_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> params = new HashMap<>();
        params.put("appId", getAppid());
        params.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("nonceStr", UUID.randomUUID().toString().replace("-", ""));
        params.put("package", "prepay_id=" + prepayId);
        params.put("signType", "MOCK");
        params.put("paySign", "mock-pay-sign");
        params.put("mockNotifyUrl", "/xiaozhi/payment/notify/mock");

        PrepayResult result = new PrepayResult();
        result.setPrepayId(prepayId);
        result.setJsapiParams(params);
        return result;
    }

    @Override
    public void closeOrder(String outTradeNo) {
        log.info("[mock-wechat-pay] closeOrder outTradeNo={}", outTradeNo);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.info("[mock-wechat-pay] refund outTradeNo={}, outRefundNo={}, refundFen={}",
                request.getOutTradeNo(), request.getOutRefundNo(), request.getRefundFen());
        RefundResult result = new RefundResult();
        result.setSuccess(true);
        result.setRefundId("mock_refund_" + UUID.randomUUID().toString().replace("-", ""));
        result.setMessage("mock refund accepted");
        return result;
    }

    /**
     * Mock 回调协议：
     * <pre>{ "outTradeNo":"PG...", "transactionId":"WX...", "amountFen": 9900 }</pre>
     */
    @Override
    public NotifyResult parseNotify(Map<String, String> headers, String body) {
        NotifyResult r = new NotifyResult();
        try {
            JSONObject json = JSONUtil.parseObj(body);
            r.setValid(true);
            r.setPaySuccess(true);
            r.setOutTradeNo(json.getStr("outTradeNo"));
            String tid = json.getStr("transactionId");
            r.setTransactionId(tid != null && !tid.isBlank() ? tid : "MOCK_TX_" + UUID.randomUUID().toString().replace("-", ""));
            Long amt = json.getLong("amountFen");
            r.setAmountFen(amt != null ? amt : 0L);
            r.setMessage("mock notify parsed");
        } catch (Exception e) {
            log.warn("[mock-wechat-pay] parseNotify failed", e);
            r.setValid(false);
            r.setPaySuccess(false);
            r.setMessage("parse error: " + e.getMessage());
        }
        return r;
    }

    private String safeGetParam(String key) {
        try {
            return sysParamsService.getValue(key, true);
        } catch (Exception e) {
            log.warn("读取参数 {} 失败: {}", key, e.getMessage());
            return null;
        }
    }
}
