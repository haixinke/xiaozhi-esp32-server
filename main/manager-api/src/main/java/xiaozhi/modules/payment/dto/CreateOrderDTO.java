package xiaozhi.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "下单请求")
public class CreateOrderDTO {

    @NotBlank(message = "商品类型不能为空")
    @Schema(description = "商品类型: SUBSCRIPTION/ITEM", example = "SUBSCRIPTION")
    private String productType;

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID: plan_id 或 sku_id")
    private Long productRefId;

    @Schema(description = "数量(仅 ITEM 类型可>1)，默认1", example = "1")
    private Integer quantity = 1;
}
