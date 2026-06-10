package xiaozhi.modules.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.utils.IpUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.payment.dto.CreateOrderDTO;
import xiaozhi.modules.payment.service.PaymentOrderService;
import xiaozhi.modules.payment.vo.OrderVO;
import xiaozhi.modules.payment.vo.PrepayVO;
import xiaozhi.modules.security.user.SecurityUser;

import java.util.List;

@Tag(name = "支付管理")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    @PostMapping("/order")
    @Operation(summary = "创建订单并发起预支付")
    public Result<PrepayVO> createOrder(@RequestBody @Valid CreateOrderDTO dto, HttpServletRequest request) {
        Long userId = SecurityUser.getUserId();
        String ip = IpUtils.getIpAddr(request);
        return new Result<PrepayVO>().ok(paymentOrderService.createOrder(userId, dto, ip));
    }

    @GetMapping("/order/{outTradeNo}")
    @Operation(summary = "查询订单")
    public Result<OrderVO> query(@PathVariable("outTradeNo") String outTradeNo) {
        Long userId = SecurityUser.getUserId();
        return new Result<OrderVO>().ok(paymentOrderService.queryByOutTradeNo(userId, outTradeNo));
    }

    @GetMapping("/orders")
    @Operation(summary = "我的订单列表")
    public Result<List<OrderVO>> myOrders() {
        Long userId = SecurityUser.getUserId();
        return new Result<List<OrderVO>>().ok(paymentOrderService.myOrders(userId));
    }

    @PostMapping("/order/{outTradeNo}/cancel")
    @Operation(summary = "取消未支付订单")
    public Result<Void> cancel(@PathVariable("outTradeNo") String outTradeNo) {
        Long userId = SecurityUser.getUserId();
        paymentOrderService.cancel(userId, outTradeNo);
        return new Result<>();
    }
}
