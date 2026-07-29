package xiaozhi.modules.pdc.nfc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pdc.nfc.service.PdcNfcSchemeJobService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcSchemeProgressVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * NFC Scheme 任务管理后台接口。
 * <p>
 * 所有接口要求 superAdmin 权限；generate/retry 仅创建 job 后立即返回 job ID，
 * 实际生成异步执行；progress 返回总数/成功/失败/游标/最近脱敏错误。
 */
@RestController
@RequestMapping("/pdc/nfc/scheme")
@RequiredArgsConstructor
@Tag(name = "NFC Scheme任务管理")
@RequiresPermissions("sys:role:superAdmin")
public class PdcNfcSchemeAdminController {

    private final PdcNfcSchemeJobService schemeJobService;

    @PostMapping("/generate/{batchId}")
    @Operation(summary = "发起批次 Scheme 生成任务")
    public Result<Long> generate(@PathVariable Long batchId) {
        Long operatorId = SecurityUser.getUserId();
        Long jobId = schemeJobService.start(batchId, operatorId);
        return new Result<Long>().ok(jobId);
    }

    @PostMapping("/retry/{batchId}")
    @Operation(summary = "重试已失败/部分成功的 Scheme 任务")
    public Result<Long> retry(@PathVariable Long batchId) {
        Long operatorId = SecurityUser.getUserId();
        Long jobId = schemeJobService.retry(batchId, operatorId);
        return new Result<Long>().ok(jobId);
    }

    @GetMapping("/progress/{batchId}")
    @Operation(summary = "查询批次 Scheme 任务进度")
    public Result<PdcNfcSchemeProgressVO> progress(@PathVariable Long batchId) {
        PdcNfcSchemeProgressVO vo = schemeJobService.progress(batchId);
        return new Result<PdcNfcSchemeProgressVO>().ok(vo);
    }

    @PostMapping("/cancel/{jobId}")
    @Operation(summary = "取消 Scheme 任务")
    public Result<Void> cancel(@PathVariable Long jobId) {
        Long operatorId = SecurityUser.getUserId();
        schemeJobService.cancel(jobId, operatorId);
        return new Result<Void>().ok(null);
    }
}
