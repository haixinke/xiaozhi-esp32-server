package xiaozhi.modules.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("ai_payment_order")
@Schema(description = "统一支付订单")
public class PaymentOrderEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "下单用户ID")
    private Long userId;

    /** SUBSCRIPTION / ITEM */
    @Schema(description = "商品类型: SUBSCRIPTION/ITEM")
    private String productType;

    @Schema(description = "商品关联ID: plan_id 或 sku_id")
    private Long productRefId;

    @Schema(description = "下单时商品快照(JSON)")
    private String productSnapshot;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "订单金额(分)")
    private Long amountFen;

    /** WECHAT_JSAPI / MOCK */
    @Schema(description = "支付渠道: WECHAT_JSAPI/MOCK")
    private String payChannel;

    /** 0待支付 1已支付 2已发货 3已取消 4已退款 5已超时 */
    @Schema(description = "订单状态: 0待支付 1已支付 2已发货 3已取消 4已退款 5已超时")
    private Integer status;

    @Schema(description = "预支付ID(微信返回)")
    private String prepayId;

    @Schema(description = "微信支付交易号")
    private String transactionId;

    @Schema(description = "支付成功时间")
    private Date paidAt;

    @Schema(description = "履约完成时间")
    private Date fulfilledAt;

    @Schema(description = "订单过期时间")
    private Date expireAt;

    @Schema(description = "已退款金额(分)")
    private Long refundAmountFen;

    @Schema(description = "下单客户端IP")
    private String clientIp;

    @Schema(description = "失败原因")
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新时间")
    private Date updatedAt;
}
