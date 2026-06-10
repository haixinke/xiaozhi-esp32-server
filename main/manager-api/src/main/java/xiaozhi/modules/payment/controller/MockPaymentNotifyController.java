package xiaozhi.modules.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.modules.payment.enums.PayChannel;
import xiaozhi.modules.payment.service.PaymentNotifyService;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock 支付回调端点。
 *
 * <p><b>仅在 {@code wechat.pay.mock=true} 时加载</b>，与 {@link xiaozhi.modules.payment.wechat.MockWechatPayClient}
 * 共享同一开关。生产环境必须保持 {@code wechat.pay.mock=false}，避免任意请求触发履约。</p>
 */
@Tag(name = "支付回调-Mock")
@RestController
@RequestMapping("/payment/notify")
@ConditionalOnProperty(name = "wechat.pay.mock", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class MockPaymentNotifyController {

    private final PaymentNotifyService paymentNotifyService;

    @PostMapping(value = "/mock")
    @Operation(summary = "Mock 模式回调（仅本地联调）")
    public Map<String, String> mockNotify(@RequestBody String body) {
        String result = paymentNotifyService.handleNotify(PayChannel.MOCK, new HashMap<>(), body);
        Map<String, String> resp = new HashMap<>();
        resp.put("code", "SUCCESS".equals(result) ? "SUCCESS" : "FAIL");
        resp.put("message", result);
        return resp;
    }
}
