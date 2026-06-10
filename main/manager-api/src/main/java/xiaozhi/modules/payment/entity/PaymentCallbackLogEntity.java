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
@TableName("ai_payment_callback_log")
@Schema(description = "支付回调日志")
public class PaymentCallbackLogEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "支付渠道: WECHAT_JSAPI/MOCK")
    private String channel;

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "微信支付交易号")
    private String transactionId;

    @Schema(description = "原始请求头")
    private String rawHeaders;

    @Schema(description = "原始请求体")
    private String rawBody;

    @Schema(description = "签名是否有效: 0否 1是")
    private Integer signatureValid;

    @Schema(description = "处理结果: SUCCESS/DUPLICATE/SIGN_FAIL/PROCESS_FAIL")
    private String processResult;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;
}
