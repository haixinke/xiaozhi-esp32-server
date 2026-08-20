package xiaozhi.common.upload;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 通用文件上传控制器。
 *
 * <p>当前提供图片上传入口，按 {@code scene} 区分业务场景（涂鸦/头像等），
 * 后续其他图片上传需求可直接复用本接口，无需新建各自的上传端点。
 */
@Tag(name = "文件上传")
@RestController
@RequestMapping("/upload")
@AllArgsConstructor
public class ImageUploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping("/image")
    @Operation(summary = "上传图片到OSS")
    @RequiresPermissions("sys:role:normal")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file,
            @RequestParam("scene") String scene) {
        Long userId = SecurityUser.getUserId();
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        // scene 大小写不敏感解析；前端传 doodle/DOODLE 均可，非法值明确报错
        UploadScene uploadScene = UploadScene.fromCode(scene);
        if (uploadScene == null) {
            throw new RenException("不支持的上传场景: " + scene);
        }
        return new Result<String>().ok(imageUploadService.uploadImage(userId, file, uploadScene));
    }
}
