package xiaozhi.modules.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.feedback.dto.FeedbackSubmitDTO;
import xiaozhi.modules.feedback.service.FeedbackService;
import xiaozhi.modules.feedback.vo.FeedbackSubmitVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 用户反馈（小程序端）
 */
@Tag(name = "用户反馈")
@RestController
@RequestMapping("/feedback")
@AllArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @Operation(summary = "提交用户反馈")
    @RequiresPermissions("sys:role:normal")
    public Result<FeedbackSubmitVO> submit(@Valid @RequestBody FeedbackSubmitDTO dto) {
        Long userId = SecurityUser.getUserId();
        return new Result<FeedbackSubmitVO>().ok(feedbackService.submit(userId, dto));
    }
}
