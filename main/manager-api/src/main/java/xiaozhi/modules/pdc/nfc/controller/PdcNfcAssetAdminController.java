package xiaozhi.modules.pdc.nfc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcAssetQueryDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcBulkAssetOperationDTO;
import xiaozhi.modules.pdc.nfc.service.PdcNfcInventoryService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcAssetVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBulkOperationVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * NFC 资产管理后台接口。
 * <p>
 * 提供批量入库 / 激活 / 停用 / 作废，以及资产分页查询和详情查询。
 * 所有接口要求 superAdmin 权限。
 */
@Slf4j
@RestController
@RequestMapping("/pdc/nfc/admin/assets")
@RequiredArgsConstructor
@Tag(name = "NFC资产管理")
@RequiresPermissions("sys:role:superAdmin")
public class PdcNfcAssetAdminController {

    private final PdcNfcInventoryService inventoryService;

    @PostMapping("/stock-in")
    @Operation(summary = "批量入库")
    public Result<PdcNfcBulkOperationVO> stockIn(@Valid @RequestBody PdcNfcBulkAssetOperationDTO request) {
        Long operatorId = SecurityUser.getUserId();
        return new Result<PdcNfcBulkOperationVO>().ok(inventoryService.stockIn(request, operatorId));
    }

    @PostMapping("/activate")
    @Operation(summary = "批量激活")
    public Result<PdcNfcBulkOperationVO> activate(@Valid @RequestBody PdcNfcBulkAssetOperationDTO request) {
        Long operatorId = SecurityUser.getUserId();
        return new Result<PdcNfcBulkOperationVO>().ok(inventoryService.activate(request, operatorId));
    }

    @PostMapping("/disable")
    @Operation(summary = "批量停用")
    public Result<PdcNfcBulkOperationVO> disable(@Valid @RequestBody PdcNfcBulkAssetOperationDTO request) {
        Long operatorId = SecurityUser.getUserId();
        return new Result<PdcNfcBulkOperationVO>().ok(inventoryService.disable(request, operatorId));
    }

    @PostMapping("/scrap")
    @Operation(summary = "批量作废")
    public Result<PdcNfcBulkOperationVO> scrap(@Valid @RequestBody PdcNfcBulkAssetOperationDTO request) {
        Long operatorId = SecurityUser.getUserId();
        return new Result<PdcNfcBulkOperationVO>().ok(inventoryService.scrap(request, operatorId));
    }

    @GetMapping
    @Operation(summary = "资产分页查询")
    public Result<PageData<PdcNfcAssetVO>> list(PdcNfcAssetQueryDTO query) {
        return new Result<PageData<PdcNfcAssetVO>>().ok(inventoryService.queryAssets(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "资产详情")
    public Result<PdcNfcAssetVO> detail(@PathVariable Long id) {
        return new Result<PdcNfcAssetVO>().ok(inventoryService.getAssetDetail(id));
    }
}
