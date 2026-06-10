package xiaozhi.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Mock 模式回调请求（仅供开发/联调使用）")
public class MockNotifyDTO {

    @NotBlank
    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "微信交易号；不传则系统生成")
    private String transactionId;
}
