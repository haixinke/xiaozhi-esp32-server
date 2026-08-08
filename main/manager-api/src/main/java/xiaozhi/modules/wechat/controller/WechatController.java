package xiaozhi.modules.wechat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.service.ChatHistoryDeleteService;
import xiaozhi.modules.agent.service.ChatHistoryExportService;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.wechat.dto.ChatHistoryExportReqDTO;
import xiaozhi.modules.wechat.dto.WechatBindAccountReqDTO;
import xiaozhi.modules.wechat.dto.WechatBindPhoneReqDTO;
import xiaozhi.modules.wechat.dto.WechatBindPhoneRespDTO;
import xiaozhi.modules.wechat.dto.WechatLoginReqDTO;
import xiaozhi.modules.wechat.dto.WechatLoginRespDTO;
import xiaozhi.modules.wechat.dto.WechatProfileUpdateDTO;
import xiaozhi.modules.wechat.service.WechatService;
import xiaozhi.modules.wechat.vo.WechatProfileVO;

/**
 * 微信小程序登录控制器
 */
@Tag(name = "微信小程序")
@RestController
@RequestMapping("/wechat")
@AllArgsConstructor
public class WechatController {

    private final WechatService wechatService;
    private final ChatHistoryExportService chatHistoryExportService;
    private final ChatHistoryDeleteService chatHistoryDeleteService;

    @PostMapping("/login")
    @Operation(summary = "微信小程序登录(无需认证)")
    public Result<WechatLoginRespDTO> login(@RequestBody @Valid WechatLoginReqDTO dto) {
        WechatLoginRespDTO resp = wechatService.login(dto.getCode());
        return new Result<WechatLoginRespDTO>().ok(resp);
    }

    @PostMapping("/bindAccount")
    @Operation(summary = "将当前微信账号绑定到已有账号")
    public Result<Void> bindAccount(@RequestBody @Valid WechatBindAccountReqDTO dto) {
        Long userId = SecurityUser.getUserId();
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        wechatService.bindAccount(userId, dto.getUsername(), dto.getPassword());
        return new Result<>();
    }

    @PostMapping("/bindPhone")
    @Operation(summary = "绑定微信授权手机号")
    public Result<WechatBindPhoneRespDTO> bindPhone(@RequestBody @Valid WechatBindPhoneReqDTO dto) {
        Long userId = SecurityUser.getUserId();
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        WechatBindPhoneRespDTO resp = wechatService.bindPhone(userId, dto.getPhoneCode());
        return new Result<WechatBindPhoneRespDTO>().ok(resp);
    }

    @GetMapping("/profile")
    @Operation(summary = "查询当前用户资料")
    @RequiresPermissions("sys:role:normal")
    public Result<WechatProfileVO> getProfile() {
        Long userId = SecurityUser.getUserId();
        return new Result<WechatProfileVO>().ok(wechatService.getProfile(userId));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新当前用户资料")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> updateProfile(@RequestBody @Valid WechatProfileUpdateDTO dto) {
        Long userId = SecurityUser.getUserId();
        wechatService.updateProfile(userId, dto);
        return new Result<>();
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传头像到OSS")
    @RequiresPermissions("sys:role:normal")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUser.getUserId();
        return new Result<String>().ok(wechatService.uploadAvatar(userId, file));
    }

    @PostMapping("/chat-history/export")
    @Operation(summary = "异步导出当前用户全部聊天记录并发送到邮箱")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> exportChatHistory(@RequestBody @Valid ChatHistoryExportReqDTO dto) {
        Long userId = SecurityUser.getUserId();
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        chatHistoryExportService.exportAndEmailAsync(userId, dto.getEmail());
        return new Result<>();
    }

    @PostMapping("/chat-history/delete")
    @Operation(summary = "删除当前用户全部聊天记录(不可恢复)")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> deleteChatHistory() {
        Long userId = SecurityUser.getUserId();
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        chatHistoryDeleteService.deleteAllByUserId(userId);
        return new Result<>();
    }
}
