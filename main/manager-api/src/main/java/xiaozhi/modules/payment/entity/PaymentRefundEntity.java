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
@TableName("ai_payment_refund")
@Schema(description = "退款记录")
public class PaymentRefundEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "商户退款单号")
    private String outRefundNo;

    @Schema(description = "关联支付订单ID")
    private Long orderId;

    @Schema(description = "退款金额(分)")
    private Long refundFen;

    @Schema(description = "退款原因")
    private String reason;

    /** 0处理中 1成功 2失败 */
    @Schema(description = "退款状态: 0处理中 1成功 2失败")
    private Integer status;

    @Schema(description = "微信退款单号")
    private String refundId;

    @Schema(description = "退款成功时间")
    private Date refundedAt;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新时间")
    private Date updatedAt;
}
