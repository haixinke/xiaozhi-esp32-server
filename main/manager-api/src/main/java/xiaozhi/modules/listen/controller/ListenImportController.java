package xiaozhi.modules.listen.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.listen.service.ListenImportService;
import xiaozhi.modules.listen.vo.ListenImportResultVO;

/**
 * 题库运营 — JSON 增量导入。
 * multipart 上传题库 JSON（大类数组 → groups → questions → options）。
 * 判重冲突返回 10373 + data=冲突明细列表；成功返回写入统计（N 组 M 题）。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/admin/listen/import")
@Tag(name = "题库运营")
public class ListenImportController {

    private final ListenImportService listenImportService;

    @PostMapping
    @Operation(summary = "增量导入题库 JSON（判重冲突整单拒绝，返回冲突明细）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ListenImportResultVO> importBank(@RequestParam("file") MultipartFile file) {
        return new Result<ListenImportResultVO>().ok(listenImportService.importBank(file));
    }
}
