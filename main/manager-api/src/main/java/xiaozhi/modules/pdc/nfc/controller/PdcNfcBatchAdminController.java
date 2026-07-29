package xiaozhi.modules.pdc.nfc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.dto.CreatePdcNfcBatchDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcBatchQueryDTO;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBatchVO;

import xiaozhi.modules.security.user.SecurityUser;

import java.util.List;

@RestController
@RequestMapping("/pdc/nfc/batch")
@RequiredArgsConstructor
@Tag(name = "NFC批次管理")
@RequiresPermissions("sys:role:superAdmin")
public class PdcNfcBatchAdminController {

    private final PdcNfcBatchService batchService;

    @PostMapping("/create")
    @Operation(summary = "创建批次并分配资产")
    public Result<PdcNfcBatchVO> create(@Valid @RequestBody CreatePdcNfcBatchDTO dto) {
        Long operatorId = SecurityUser.getUserId();
        return new Result<PdcNfcBatchVO>().ok(batchService.create(dto, operatorId));
    }

    @GetMapping("/list")
    @Operation(summary = "查询批次列表")
    public Result<List<PdcNfcBatchVO>> list(PdcNfcBatchQueryDTO query) {
        return new Result<List<PdcNfcBatchVO>>().ok(batchService.list(query));
    }

    @PostMapping("/{batchId}/cancel")
    @Operation(summary = "取消批次")
    public Result<Void> cancel(@PathVariable Long batchId) {
        Long operatorId = SecurityUser.getUserId();
        batchService.cancel(batchId, operatorId);
        return new Result<Void>().ok(null);
    }
}
