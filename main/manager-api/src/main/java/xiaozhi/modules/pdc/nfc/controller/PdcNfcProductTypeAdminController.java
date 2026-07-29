package xiaozhi.modules.pdc.nfc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcReleaseEvidenceDTO;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAuditService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcProductTypeService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcProductTypeVO;
import xiaozhi.modules.security.user.SecurityUser;

import java.util.List;

@RestController
@RequestMapping("/pdc/nfc/product-type")
@RequiredArgsConstructor
@Tag(name = "NFC商品类型管理")
@RequiresPermissions("sys:role:superAdmin")
public class PdcNfcProductTypeAdminController {

    private final PdcNfcProductTypeService productTypeService;
    private final PdcNfcAuditService auditService;

    @GetMapping("/list")
    @Operation(summary = "查询所有商品类型")
    public Result<List<PdcNfcProductTypeVO>> list() {
        return new Result<List<PdcNfcProductTypeVO>>().ok(productTypeService.list());
    }

    @PostMapping("/release-evidence")
    @Operation(summary = "登记发布证据（append-only）")
    public Result<Void> registerReleaseEvidence(@Valid @RequestBody PdcNfcReleaseEvidenceDTO dto) {
        Long operatorId = SecurityUser.getUserId();
        auditService.registerReleaseEvidence(dto, operatorId);
        return new Result<Void>().ok(null);
    }
}
