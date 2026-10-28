package xiaozhi.modules.listen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.service.ListenAnswerService;
import xiaozhi.modules.listen.vo.ListenDrawVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 听你说·小程序答题接口。
 * 抽题：随机抽一个当前用户未答过的题目分组；提交：整组一次性保存，幂等。
 */
@Tag(name = "听你说-小程序答题")
@RestController
@RequestMapping("/listen")
@AllArgsConstructor
public class ListenAnswerController {

    private final ListenAnswerService listenAnswerService;

    @GetMapping("/draw")
    @Operation(summary = "随机抽取一组未答过的题目")
    @RequiresPermissions("sys:role:normal")
    public Result<ListenDrawVO> draw() {
        Long userId = SecurityUser.getUserId();
        ListenDrawVO vo = listenAnswerService.draw(userId);
        if (vo == null) {
            // 所有题目分组都已答过：HTTP 200 + 业务码，前端据此出提示文案
            return new Result<ListenDrawVO>().error(ErrorCode.LISTEN_ALL_ANSWERED);
        }
        return new Result<ListenDrawVO>().ok(vo);
    }

    @PostMapping("/answers")
    @Operation(summary = "整组提交答案")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> submitAnswers(@Valid @RequestBody ListenAnswerSubmitDTO dto) {
        Long userId = SecurityUser.getUserId();
        listenAnswerService.submitAnswers(userId, dto);
        return new Result<Void>().ok(null);
    }
}
