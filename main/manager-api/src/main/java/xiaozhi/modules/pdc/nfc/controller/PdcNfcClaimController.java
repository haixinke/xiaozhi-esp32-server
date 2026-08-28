package xiaozhi.modules.pdc.nfc.controller;

import java.util.UUID;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcClaimConfirmDTO;
import xiaozhi.modules.pdc.nfc.service.PdcNfcClaimService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimPreviewVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimResultVO;
import xiaozhi.modules.security.user.SecurityUser;

@Slf4j
@RestController
@RequestMapping("/pdc/nfc/claim")
@RequiredArgsConstructor
@Tag(name = "NFC领取")
@RequiresPermissions("sys:role:normal")
public class PdcNfcClaimController {

    private final PdcNfcClaimService claimService;

    @GetMapping("/preview")
    @Operation(summary = "领取预览")
    public Result<PdcNfcClaimPreviewVO> preview(
            @RequestParam String claimRef) {
        Long userId = SecurityUser.getUserId();
        // 必须返回 Result 信封：小程序 request.js 按 code 字段判断成败，裸 VO 会被判为响应异常
        return new Result<PdcNfcClaimPreviewVO>().ok(claimService.preview(userId, claimRef));
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认领取")
    public Result<PdcNfcClaimResultVO> confirm(@Valid @RequestBody PdcNfcClaimConfirmDTO dto) {
        Long userId = SecurityUser.getUserId();
        UUID requestId = UUID.fromString(dto.getRequestId());
        return new Result<PdcNfcClaimResultVO>().ok(claimService.confirm(userId, dto.getClaimRef(), requestId));
    }
}
