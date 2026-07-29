package xiaozhi.modules.pdc.nfc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcOperationLogQueryDTO;
import xiaozhi.modules.pdc.nfc.service.PdcNfcInventoryService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcOperationLogVO;

/**
 * NFC 操作日志管理后台接口。
 * <p>
 * 提供操作日志分页查询和按对象查询。
 * 所有接口要求 superAdmin 权限。
 */
@Slf4j
@RestController
@RequestMapping("/pdc/nfc/admin/logs")
@RequiredArgsConstructor
@Tag(name = "NFC操作日志管理")
@RequiresPermissions("sys:role:superAdmin")
public class PdcNfcOperationLogAdminController {

    private final PdcNfcInventoryService inventoryService;

    @GetMapping
    @Operation(summary = "操作日志分页查询")
    public Result<PageData<PdcNfcOperationLogVO>> list(PdcNfcOperationLogQueryDTO query) {
        return new Result<PageData<PdcNfcOperationLogVO>>().ok(inventoryService.queryOperationLogs(query));
    }

    @GetMapping("/by-object/{objectType}/{objectId}")
    @Operation(summary = "按对象查询操作日志")
    public Result<PageData<PdcNfcOperationLogVO>> byObject(
            @PathVariable String objectType,
            @PathVariable Long objectId,
            PdcNfcOperationLogQueryDTO query) {
        return new Result<PageData<PdcNfcOperationLogVO>>()
                .ok(inventoryService.queryLogsByObject(objectType, objectId, query));
    }
}
