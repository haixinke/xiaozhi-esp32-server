package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePdcNfcBatchDTO {

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @NotNull(message = "商品类型ID不能为空")
    private Long productTypeId;

    @NotBlank(message = "SKU编码不能为空")
    private String skuCode;

    @NotBlank(message = "原型不能为空")
    private String prototype;

    @NotNull(message = "计划数量不能为空")
    @Min(value = 1, message = "计划数量最少为1")
    @Max(value = 10000, message = "计划数量最多为10000")
    private Integer plannedQuantity;

    private String remark;
}
