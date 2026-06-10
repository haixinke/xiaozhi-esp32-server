package xiaozhi.modules.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.modules.payment.enums.PayChannel;
import xiaozhi.modules.payment.service.PaymentNotifyService;

import java.io.BufferedReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付回调接收端：URL 走 anon 过滤器，不依赖登录态。
 * 实际验签/解密由 {@link xiaozhi.modules.payment.wechat.WechatPayClient#parseNotify} 完成。
 */
@Slf4j
@Tag(name = "支付回调")
@RestController
@RequestMapping("/payment/notify")
@RequiredArgsConstructor
public class PaymentNotifyController {

    private final PaymentNotifyService paymentNotifyService;

    @PostMapping(consumes = MediaType.ALL_VALUE)
    @Operation(summary = "微信支付回调（生产环境）")
    public Map<String, String> wechatNotify(HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> headers = collectHeaders(request);
        String body = readBody(request);
        String result = paymentNotifyService.handleNotify(PayChannel.WECHAT_JSAPI, headers, body);
        Map<String, String> resp = new HashMap<>();
        boolean ok = "SUCCESS".equals(result) || "DUPLICATE".equals(result);
        resp.put("code", ok ? "SUCCESS" : "FAIL");
        resp.put("message", result);
        if (!ok) {
            // 以 5xx 返回，让微信走重推机制
            response.setStatus(500);
        }
        return resp;
    }

    private Map<String, String> collectHeaders(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name.toLowerCase(), request.getHeader(name));
        }
        return map;
    }

    private String readBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            log.warn("读取回调 body 失败", e);
        }
        return sb.toString();
    }
}
