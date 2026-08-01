package xiaozhi.modules.sys.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.sys.dto.SysOperationLogDTO;
import xiaozhi.modules.sys.service.OperationLogService;

/**
 * 操作日志查询
 */
@RestController
@RequestMapping("admin/operation-log")
@Tag(name = "操作日志")
@AllArgsConstructor
public class SysOperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping("page")
    @Operation(summary = "分页查询操作日志")
    @Parameters({
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", in = ParameterIn.QUERY, required = true, ref = "int"),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", in = ParameterIn.QUERY, required = true, ref = "int"),
            @Parameter(name = "userId", description = "操作人用户ID", in = ParameterIn.QUERY, ref = "Long"),
            @Parameter(name = "operationType", description = "操作类型", in = ParameterIn.QUERY, ref = "String"),
            @Parameter(name = "startDate", description = "开始时间(yyyy-MM-dd HH:mm:ss)", in = ParameterIn.QUERY, ref = "String"),
            @Parameter(name = "endDate", description = "结束时间(yyyy-MM-dd HH:mm:ss)", in = ParameterIn.QUERY, ref = "String")
    })
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<SysOperationLogDTO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        PageData<SysOperationLogDTO> page = operationLogService.page(params);
        return new Result<PageData<SysOperationLogDTO>>().ok(page);
    }
}
