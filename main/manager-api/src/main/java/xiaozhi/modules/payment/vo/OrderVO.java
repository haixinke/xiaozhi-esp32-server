package xiaozhi.modules.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;

import java.util.Date;

@Data
@Schema(description = "订单信息")
public class OrderVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "商品类型: SUBSCRIPTION/ITEM")
    private String productType;

    @Schema(description = "商品关联ID")
    private Long productRefId;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "订单金额(分)")
    private Long amountFen;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "订单状态: 0待支付 1已支付 2已发货 3已取消 4已退款 5已超时")
    private Integer status;

    @Schema(description = "微信支付交易号")
    private String transactionId;

    @Schema(description = "支付成功时间")
    private Date paidAt;

    @Schema(description = "履约完成时间")
    private Date fulfilledAt;

    @Schema(description = "创建时间")
    private Date createdAt;

    /** 将 Entity 转换为 VO（隐藏内部字段如 prepayId、clientIp 等） */
    public static OrderVO toVO(PaymentOrderEntity entity) {
        OrderVO vo = new OrderVO();
        vo.setId(entity.getId());
        vo.setOutTradeNo(entity.getOutTradeNo());
        vo.setProductType(entity.getProductType());
        vo.setProductRefId(entity.getProductRefId());
        vo.setQuantity(entity.getQuantity());
        vo.setAmountFen(entity.getAmountFen());
        vo.setPayChannel(entity.getPayChannel());
        vo.setStatus(entity.getStatus());
        vo.setTransactionId(entity.getTransactionId());
        vo.setPaidAt(entity.getPaidAt());
        vo.setFulfilledAt(entity.getFulfilledAt());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
