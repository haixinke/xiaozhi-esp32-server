package xiaozhi.modules.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.common.validator.ValidatorUtils;
import xiaozhi.modules.feedback.dto.FeedbackHandleDTO;
import xiaozhi.modules.feedback.service.FeedbackService;
import xiaozhi.modules.feedback.vo.FeedbackAdminVO;

import java.util.Map;

/**
 * 用户反馈管理（智控台）
 */
@Tag(name = "用户反馈管理")
@RestController
@RequestMapping("/admin/feedback")
@AllArgsConstructor
public class FeedbackAdminController {

    private final FeedbackService feedbackService;

    @GetMapping("/page")
    @Operation(summary = "分页查询用户反馈")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({ @Parameter(name = "status", description = "处理状态：0-未处理 1-已处理"),
            @Parameter(name = "type", description = "诉求类型"),
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true) })
    public Result<PageData<FeedbackAdminVO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        ValidatorUtils.validateEntity(params);
        return new Result<PageData<FeedbackAdminVO>>().ok(feedbackService.page(params));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询反馈详情")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<FeedbackAdminVO> get(@PathVariable("id") Long id) {
        return new Result<FeedbackAdminVO>().ok(feedbackService.get(id));
    }

    @PutMapping("/update")
    @Operation(summary = "处理反馈：更新状态与备注")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> update(@Valid @RequestBody FeedbackHandleDTO dto) {
        feedbackService.handle(dto);
        return new Result<>();
    }
}
