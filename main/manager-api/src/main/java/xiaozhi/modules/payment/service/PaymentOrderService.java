package xiaozhi.modules.payment.service;

import xiaozhi.modules.payment.dto.CreateOrderDTO;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;
import xiaozhi.modules.payment.vo.OrderVO;
import xiaozhi.modules.payment.vo.PrepayVO;

import java.util.List;

public interface PaymentOrderService {

    /** 下单：仅校验商品、金额，落库 + 调用 prepay */
    PrepayVO createOrder(Long userId, CreateOrderDTO dto, String clientIp);

    /** 查询订单（强校验所属用户） */
    OrderVO queryByOutTradeNo(Long userId, String outTradeNo);

    /** 我的订单列表 */
    List<OrderVO> myOrders(Long userId);

    /** 用户主动取消未支付订单 */
    void cancel(Long userId, String outTradeNo);

    /** 主动查询微信支付订单状态并触发履约（内部使用/回调补偿） */
    void queryAndFulfill(String outTradeNo);

    /** 内部使用：根据商户单号取实体（不校验所属，回调用） */
    PaymentOrderEntity loadByOutTradeNo(String outTradeNo);
}
