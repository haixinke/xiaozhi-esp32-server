package xiaozhi.modules.wechat.controller;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.util.crypto.SHA1;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import me.chanjar.weixin.mp.util.crypto.WxMpCryptUtil;
import xiaozhi.modules.pet.service.ImageGenTaskService;
import xiaozhi.modules.wechat.config.WechatMsgPushProperties;

/**
 * 微信小程序「消息推送」回调端点（mp 后台「开发管理-消息推送」配置）。
 *
 * <p>当前承载 mediaCheckAsync 内容审核结果（event=wxa_media_check）：按 trace_id 路由到
 * AI生图任务的照片/结果图审核结论。安全模式（AES）下消息体加密，需验签 + 解密。
 *
 * <p>注意：本端点为匿名访问（Shiro anon），安全性完全依赖微信签名与 AES 密钥校验；
 * 处理必须幂等且快速返回 "success"（微信 5s 无响应会重推 3 次）。
 */
@Slf4j
@RestController
@RequestMapping("/wechat/mp")
@RequiredArgsConstructor
public class WechatMsgCallbackController {

    /** mediaCheckAsync 审核结果事件名 */
    private static final String EVENT_MEDIA_CHECK = "wxa_media_check";

    private final WechatMsgPushProperties msgPushProperties;
    private final ImageGenTaskService imageGenTaskService;

    @Value("${eggbaby.miniprogram.appid:${wechat.miniprogram.appid:}}")
    private String appid;

    /**
     * mp 后台配置 URL 时的一次性校验：验签通过则原样回 echostr。
     */
    @GetMapping(value = "/callback", produces = "text/plain")
    public String verify(@RequestParam("signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        if (!msgPushProperties.isConfigured()) {
            log.warn("消息推送未配置，拒绝URL校验");
            return "fail";
        }
        boolean ok = SHA1.gen(msgPushProperties.getToken(), timestamp, nonce).equals(signature);
        log.info("消息推送URL校验 result={}", ok ? "pass" : "fail");
        return ok ? echostr : "fail";
    }

    /**
     * 接收消息推送（安全模式 AES 加密）。
     */
    @PostMapping(value = "/callback", produces = "text/plain")
    public String receive(@RequestBody String requestBody,
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce) {
        if (!msgPushProperties.isConfigured()) {
            log.warn("消息推送未配置，忽略回调");
            return "success";
        }
        try {
            Document encryptedDoc = parseXml(requestBody);
            String encryptContent = xpath(encryptedDoc, "/xml/Encrypt");
            if (StringUtils.isBlank(encryptContent)) {
                log.warn("消息推送缺少Encrypt字段");
                return "success";
            }
            // 验签：msg_signature = sha1(sort(token, timestamp, nonce, encrypt))
            String expected = SHA1.gen(msgPushProperties.getToken(), timestamp, nonce, encryptContent);
            if (!expected.equals(msgSignature)) {
                log.warn("消息推送验签失败");
                return "success";
            }

            String plainXml = cryptUtils().decrypt(encryptContent);
            Document doc = parseXml(plainXml);
            String msgType = xpath(doc, "/xml/MsgType");
            String event = xpath(doc, "/xml/Event");
            if (!"event".equalsIgnoreCase(msgType) || !EVENT_MEDIA_CHECK.equalsIgnoreCase(event)) {
                log.info("忽略非审核事件 msgType={}, event={}", msgType, event);
                return "success";
            }

            String traceId = xpath(doc, "/xml/trace_id");
            // suggest: pass / review / risky；仅 pass 视为通过，其余保守按驳回处理
            String suggest = xpath(doc, "/xml/result/suggest");
            log.info("收到mediaCheck审核结果 traceId={}, suggest={}", traceId, suggest);
            imageGenTaskService.handleMediaCheckResult(traceId, "pass".equalsIgnoreCase(suggest));
        } catch (Exception e) {
            // 回调处理失败不重推（任务侧有 24h 超时兜底），记录日志即可
            log.error("消息推送处理失败", e);
        }
        return "success";
    }

    private WxMpCryptUtil cryptUtils() {
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId(appid);
        config.setToken(msgPushProperties.getToken());
        config.setAesKey(msgPushProperties.getAesKey());
        return new WxMpCryptUtil(config);
    }

    /** 安全解析 XML：关闭 DTD 与外部实体，防 XXE */
    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String xpath(Document doc, String expression) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        String value = xpath.evaluate(expression, doc);
        return value == null ? "" : value.trim();
    }
}
