package xiaozhi.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "退款请求")
public class RefundDTO {

    @NotBlank
    @Schema(description = "商户订单号")
    private String outTradeNo;

    @NotNull
    @Schema(description = "退款金额(分)")
    private Long refundFen;

    @Schema(description = "原因")
    private String reason;
}
