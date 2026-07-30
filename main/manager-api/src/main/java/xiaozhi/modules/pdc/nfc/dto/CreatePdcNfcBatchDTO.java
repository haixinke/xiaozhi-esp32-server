package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建 NFC 批次请求 DTO。
 * <p>
 * 批次创建时指定商品类型、SKU、原型和计划数量，系统自动分配资产。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePdcNfcBatchDTO {

    /** 批次编号，全局唯一，由管理员手动指定 */
    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    /** 关联商品类型 ID（对应 pdc_nfc_product_type.id） */
    @NotNull(message = "商品类型ID不能为空")
    private Long productTypeId;

    /** SKU 编码，标识产品型号 */
    @NotBlank(message = "SKU编码不能为空")
    private String skuCode;

    /** 原型标识，区分同一 SKU 下的不同硬件版本 */
    @NotBlank(message = "原型不能为空")
    private String prototype;

    /** 计划生产数量，范围 1~10000 */
    @NotNull(message = "计划数量不能为空")
    @Min(value = 1, message = "计划数量最少为1")
    @Max(value = 10000, message = "计划数量最多为10000")
    private Integer plannedQuantity;

    /** 备注信息 */
    private String remark;
}
