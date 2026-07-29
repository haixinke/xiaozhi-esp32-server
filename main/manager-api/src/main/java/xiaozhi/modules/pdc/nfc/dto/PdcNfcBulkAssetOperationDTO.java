package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 批量资产操作请求 DTO（入库 / 激活 / 停用 / 作废）。
 * <p>
 * assetIds 最多 500 个，不允许重复、不允许 null 元素。
 */
@Data
public class PdcNfcBulkAssetOperationDTO {

    /** 资产 ID 列表，最多 500 个，不允许重复和 null */
    @NotEmpty(message = "assetIds不能为空")
    private List<Long> assetIds;

    /** 业务单号 */
    @NotBlank(message = "businessNo不能为空")
    private String businessNo;

    /** 幂等请求 ID */
    @NotNull(message = "requestId不能为空")
    private UUID requestId;
}
