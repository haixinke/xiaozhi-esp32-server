package xiaozhi.modules.pdc.nfc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAdminOperationType;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcManualMarkDTO;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAdminIdempotencyService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcManualWriteService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultImporter;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcManualAssetVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteFile;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteJobVO;
import xiaozhi.modules.security.user.SecurityUser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NFC 写卡任务管理后台接口。
 * <p>
 * 所有接口要求 superAdmin 权限；create 创建快照任务，download 导出字节稳定 CSV，
 * cancel 取消任务，progress 查询进度。
 */
@Slf4j
@RestController
@RequestMapping("/pdc/nfc/write")
@RequiredArgsConstructor
@Tag(name = "NFC 写卡任务管理")
@RequiresPermissions("sys:role:superAdmin")
public class PdcNfcWriteJobAdminController {

    private final PdcNfcWriteJobService writeJobService;
    private final PdcNfcWriteResultImporter writeResultImporter;
    private final PdcNfcAdminIdempotencyService idempotencyService;
    private final PdcNfcManualWriteService manualWriteService;

    @PostMapping("/create/{batchId}")
    @Operation(summary = "创建写卡任务")
    public Result<PdcNfcWriteJobVO> create(
            @PathVariable Long batchId,
            @RequestParam(required = false) String mode) {
        Long operatorId = SecurityUser.getUserId();
        PdcNfcWriteJobVO vo = writeJobService.create(batchId, mode, operatorId);
        return new Result<PdcNfcWriteJobVO>().ok(vo);
    }

    @GetMapping("/download/{jobId}")
    @Operation(summary = "下载写卡 CSV 文件")
    public void download(@PathVariable Long jobId, HttpServletResponse response) throws IOException {
        Long operatorId = SecurityUser.getUserId();
        PdcNfcWriteFile file = writeJobService.export(jobId, operatorId);

        // 设置响应头
        String encodedName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        response.setContentType("text/csv;charset=UTF-8");
        response.setContentLength(file.bytes().length);

        // 写入响应体（不记录日志）
        response.getOutputStream().write(file.bytes());
        response.getOutputStream().flush();

        log.info("Write job CSV downloaded: jobId={}, fileName={}, operator={}",
                jobId, file.fileName(), operatorId);
    }

    @PostMapping("/cancel/{jobId}")
    @Operation(summary = "取消写卡任务")
    public Result<Void> cancel(@PathVariable Long jobId) {
        Long operatorId = SecurityUser.getUserId();
        writeJobService.cancel(jobId, operatorId);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/progress/{jobId}")
    @Operation(summary = "查询写卡任务进度")
    public Result<PdcNfcWriteJobVO> progress(@PathVariable Long jobId) {
        PdcNfcWriteJobVO vo = writeJobService.getProgress(jobId);
        return new Result<PdcNfcWriteJobVO>().ok(vo);
    }

    @PostMapping("/{jobId}/import")
    @Operation(summary = "导入工厂写卡结果 CSV")
    public Result<PdcNfcWriteImportVO> importResult(
            @PathVariable Long jobId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("requestId") UUID requestId
    ) {
        Long operatorId = SecurityUser.getUserId();
        String canonicalRequest = jobId + ":" + requestId;
        PdcNfcWriteImportVO vo = idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                canonicalRequest,
                PdcNfcWriteImportVO.class,
                () -> writeResultImporter.importResult(jobId, requestId, file, operatorId)
        );
        return new Result<PdcNfcWriteImportVO>().ok(vo);
    }

    // --- 手动写卡模式（ADR 0003）：仅 mode=MANUAL 的任务可用，与 CSV 通道互斥 ---

    @GetMapping("/manual/{jobId}/assets")
    @Operation(summary = "手动写卡任务资产列表")
    public Result<List<PdcNfcManualAssetVO>> manualAssets(@PathVariable Long jobId) {
        return new Result<List<PdcNfcManualAssetVO>>().ok(manualWriteService.listAssets(jobId));
    }

    @GetMapping("/manual/{jobId}/assets/{assetId}/scheme")
    @Operation(summary = "单条解密查看 Scheme（记审计）")
    public Result<Map<String, String>> manualRevealScheme(
            @PathVariable Long jobId, @PathVariable Long assetId) {
        Long operatorId = SecurityUser.getUserId();
        String scheme = manualWriteService.revealScheme(jobId, assetId, operatorId);
        return new Result<Map<String, String>>().ok(Map.of("scheme", scheme));
    }

    @PostMapping("/manual/{jobId}/assets/{assetId}/mark")
    @Operation(summary = "手动写卡单资产标记")
    public Result<PdcNfcManualAssetVO> manualMark(
            @PathVariable Long jobId,
            @PathVariable Long assetId,
            @Validated @RequestBody PdcNfcManualMarkDTO dto) {
        Long operatorId = SecurityUser.getUserId();
        PdcNfcManualAssetVO vo = manualWriteService.mark(jobId, assetId, dto.getAction(), operatorId);
        return new Result<PdcNfcManualAssetVO>().ok(vo);
    }
}
