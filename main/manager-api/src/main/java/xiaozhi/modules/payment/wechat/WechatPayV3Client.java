package xiaozhi.modules.payment.wechat;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RSAPublicKeyNotificationConfig;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.Refund;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付 V3 JSAPI 真实客户端实现，基于官方 SDK <code>wechatpay-java</code>。
 *
 * <p>采用微信支付公钥模式（{@link RSAPublicKeyConfig}），不再依赖平台证书自动下载，
 * 适配仅开放公钥入口的商户号。</p>
 *
 * <p>启用条件：{@code wechat.pay.mock=false}（默认）。bean 名称固定为 {@code wechatPayV3Client}，
 * 与 {@link MockWechatPayClient#getClass()} 上的 {@code @ConditionalOnMissingBean(name="wechatPayV3Client")}
 * 配合：真实 client 加载后 mock 自动让位。</p>
 *
 * <p>启动期 {@link PostConstruct} 会从环境变量加载配置；
 * 任何关键配置缺失立即抛 {@link RenException}，导致 Spring 容器启动失败 ——
 * 这正是 {@link WechatPayClientStartupGuard} 期望的"配错不允许上线"语义。</p>
 */
@Slf4j
@Primary
@Component("wechatPayV3Client")
@ConditionalOnProperty(name = "wechat.pay.mock", havingValue = "false")
public class WechatPayV3Client implements WechatPayClient {

    private volatile WechatPayProperties props;
    private volatile JsapiServiceExtension jsapiService;
    private volatile RefundService refundService;
    private volatile NotificationParser notificationParser;

    public WechatPayV3Client() {
    }

    @PostConstruct
    public void init() {
        this.props = WechatPayProperties.loadReal();
        RSAPublicKeyConfig sdkConfig = new RSAPublicKeyConfig.Builder()
                .merchantId(props.getMchid())
                .privateKey(props.getPrivateKey())
                .merchantSerialNumber(props.getSerialNo())
                .apiV3Key(props.getApiV3Key())
                .publicKeyId(props.getPubKeyId())
                .publicKey(props.getPubKey())
                .build();
        this.jsapiService = new JsapiServiceExtension.Builder().config(sdkConfig).build();
        this.refundService = new RefundService.Builder().config(sdkConfig).build();

        RSAPublicKeyNotificationConfig notificationConfig = new RSAPublicKeyNotificationConfig.Builder()
                .apiV3Key(props.getApiV3Key())
                .publicKeyId(props.getPubKeyId())
                .publicKey(props.getPubKey())
                .build();
        this.notificationParser = new NotificationParser(notificationConfig);
        log.info("WechatPayV3Client initialized, mchid={}, appid={}",
                props.getMchid(), props.getAppid());
    }

    @Override
    public boolean isMockMode() {
        return false;
    }

    @Override
    public String getAppid() {
        return props.getAppid();
    }

    @Override
    public PrepayResult jsapiPrepay(PrepayRequest req) {
        com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest sdkReq =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        sdkReq.setAppid(props.getAppid());
        sdkReq.setMchid(props.getMchid());
        sdkReq.setOutTradeNo(req.getOutTradeNo());
        sdkReq.setDescription(truncate(req.getDescription(), 127));
        sdkReq.setNotifyUrl(props.getNotifyUrl());

        Amount amount = new Amount();
        amount.setTotal((int) req.getAmountFen());
        amount.setCurrency("CNY");
        sdkReq.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(req.getOpenid());
        sdkReq.setPayer(payer);

        try {
            PrepayWithRequestPaymentResponse resp = jsapiService.prepayWithRequestPayment(sdkReq);
            Map<String, String> jsapiParams = new HashMap<>();
            jsapiParams.put("appId", resp.getAppId());
            jsapiParams.put("timeStamp", resp.getTimeStamp());
            jsapiParams.put("nonceStr", resp.getNonceStr());
            // 注意：SDK 字段名是 packageVal，对应小程序 wx.requestPayment 的 package
            jsapiParams.put("package", resp.getPackageVal());
            jsapiParams.put("signType", resp.getSignType());
            jsapiParams.put("paySign", resp.getPaySign());

            // SDK 在 0.2.x 不直接返回 prepay_id；用 packageVal 反解
            String prepayId = resp.getPackageVal() != null && resp.getPackageVal().startsWith("prepay_id=")
                    ? resp.getPackageVal().substring("prepay_id=".length())
                    : null;

            PrepayResult result = new PrepayResult();
            result.setPrepayId(prepayId);
            result.setJsapiParams(jsapiParams);
            return result;
        } catch (Exception e) {
            log.error("[wechat-pay-v3] jsapiPrepay 失败 outTradeNo={}, mchid={}",
                    req.getOutTradeNo(), props.getMchid(), e);
            throw new RenException(ErrorCode.PAY_CHANNEL_NOT_AVAILABLE, e.getMessage());
        }
    }

    @Override
    public void closeOrder(String outTradeNo) {
        try {
            CloseOrderRequest req = new CloseOrderRequest();
            req.setMchid(props.getMchid());
            req.setOutTradeNo(outTradeNo);
            jsapiService.closeOrder(req);
        } catch (Exception e) {
            // 关单失败不阻断业务（订单已在本地标记取消/超时），仅记录
            log.warn("[wechat-pay-v3] closeOrder 失败 outTradeNo={}: {}", outTradeNo, e.getMessage());
        }
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        RefundResult result = new RefundResult();
        try {
            com.wechat.pay.java.service.refund.model.CreateRequest sdkReq =
                    new com.wechat.pay.java.service.refund.model.CreateRequest();
            sdkReq.setOutTradeNo(req.getOutTradeNo());
            sdkReq.setOutRefundNo(req.getOutRefundNo());
            sdkReq.setReason(truncate(req.getReason(), 80));
            sdkReq.setNotifyUrl(props.getNotifyUrl());

            AmountReq amountReq = new AmountReq();
            amountReq.setRefund(req.getRefundFen());
            amountReq.setTotal(req.getTotalFen());
            amountReq.setCurrency("CNY");
            sdkReq.setAmount(amountReq);

            Refund refund = refundService.create(sdkReq);
            result.setSuccess(true);
            result.setRefundId(refund.getRefundId());
            result.setMessage(refund.getStatus() != null ? refund.getStatus().name() : "OK");
        } catch (Exception e) {
            log.error("[wechat-pay-v3] refund 失败 outTradeNo={}, outRefundNo={}",
                    req.getOutTradeNo(), req.getOutRefundNo(), e);
            result.setSuccess(false);
            result.setMessage("退款请求失败，请稍后重试");
        }
        return result;
    }

    /**
     * 解析微信支付回调：验签 → 解密 → 转 NotifyResult。
     * <p>本期仅处理支付结果（event_type 以 TRANSACTION 开头）；退款回调走相同路径但本期标记 paySuccess=false。</p>
     */
    @Override
    public NotifyResult parseNotify(Map<String, String> headers, String body) {
        NotifyResult r = new NotifyResult();
        try {
            RequestParam reqParam = new RequestParam.Builder()
                    .serialNumber(headerOrEmpty(headers, "wechatpay-serial"))
                    .nonce(headerOrEmpty(headers, "wechatpay-nonce"))
                    .signature(headerOrEmpty(headers, "wechatpay-signature"))
                    .timestamp(headerOrEmpty(headers, "wechatpay-timestamp"))
                    .signType(headers.getOrDefault("wechatpay-signature-type", "WECHATPAY2-SHA256-RSA2048"))
                    .body(body)
                    .build();

            // 先识别 event_type，再决定解码目标类型
            String eventType = "";
            try {
                JSONObject root = JSONUtil.parseObj(body);
                eventType = StringUtils.defaultString(root.getStr("event_type"));
            } catch (Exception ignore) {
                // body 非 JSON，由后续 parse 抛错
            }

            if (eventType.startsWith("TRANSACTION")) {
                Transaction tx = notificationParser.parse(reqParam, Transaction.class);
                r.setValid(true);
                r.setOutTradeNo(tx.getOutTradeNo());
                r.setTransactionId(tx.getTransactionId());
                r.setAmountFen(tx.getAmount() != null && tx.getAmount().getTotal() != null
                        ? tx.getAmount().getTotal().longValue() : 0L);
                r.setPaySuccess(tx.getTradeState() == Transaction.TradeStateEnum.SUCCESS);
                r.setMessage("trade_state=" + tx.getTradeState());
            } else {
                // 当前仅识别签名有效性，业务事件不消费
                // 通过把 body 反序列化为通用 Notification 来触发签名校验
                Object dummy = notificationParser.parse(reqParam, java.util.Map.class);
                String reqId = dummy instanceof Map<?, ?> map && map.get("id") != null
                        ? String.valueOf(map.get("id")) : IdUtil.fastSimpleUUID();
                r.setValid(true);
                r.setPaySuccess(false);
                r.setMessage("ignored event_type=" + eventType + ", id=" + reqId);
            }
        } catch (Exception e) {
            log.warn("[wechat-pay-v3] parseNotify 验签/解密失败", e);
            r.setValid(false);
            r.setPaySuccess(false);
            r.setMessage("parse error");
        }
        return r;
    }

    private static String headerOrEmpty(Map<String, String> headers, String key) {
        String v = headers.get(key);
        return v == null ? "" : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
