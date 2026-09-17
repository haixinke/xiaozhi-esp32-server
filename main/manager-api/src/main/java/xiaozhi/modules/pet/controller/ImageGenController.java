package xiaozhi.modules.pet.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.dto.ImageGenTaskCreateDTO;
import xiaozhi.modules.pet.service.ImageGenTaskService;
import xiaozhi.modules.pet.vo.ImageGenTaskVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 宠物AI生图控制器。
 *
 * <p>创建任务毫秒级返回（只做落库 + 照片审核提交），生成与审核由异步链路推进，
 * 小程序按 2~3s 轮询查询接口。
 */
@Tag(name = "宠物AI生图")
@RestController
@RequestMapping("/pet/image-gen")
@AllArgsConstructor
public class ImageGenController {

    private final ImageGenTaskService imageGenTaskService;

    @PostMapping("/tasks")
    @Operation(summary = "创建AI生图任务")
    @RequiresPermissions("sys:role:normal")
    public Result<ImageGenTaskVO> createTask(@RequestBody @Valid ImageGenTaskCreateDTO dto) {
        Long userId = SecurityUser.getUserId();
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        return new Result<ImageGenTaskVO>().ok(imageGenTaskService.createTask(userId, dto.getPhotoUrl()));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询AI生图任务状态")
    @RequiresPermissions("sys:role:normal")
    public Result<ImageGenTaskVO> getTask(@PathVariable("taskId") Long taskId) {
        Long userId = SecurityUser.getUserId();
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        return new Result<ImageGenTaskVO>().ok(imageGenTaskService.getTask(userId, taskId));
    }
}
