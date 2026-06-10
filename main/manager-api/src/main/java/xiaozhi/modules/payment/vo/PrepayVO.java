package xiaozhi.modules.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "下单返回")
public class PrepayVO {

    @Schema(description = "商户订单号")
    private String outTradeNo;

    @Schema(description = "金额(分)")
    private Long amountFen;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "拉起支付参数(直接喂给 wx.requestPayment)")
    private Map<String, String> prepayParams;
}
