package xiaozhi.modules.wechat.controller;

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
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.wechat.dto.WechatBindAccountReqDTO;
import xiaozhi.modules.wechat.dto.WechatLoginReqDTO;
import xiaozhi.modules.wechat.dto.WechatLoginRespDTO;
import xiaozhi.modules.wechat.service.WechatService;

/**
 * 微信小程序登录控制器
 */
@Tag(name = "微信小程序")
@RestController
@RequestMapping("/wechat")
@AllArgsConstructor
public class WechatController {

    private final WechatService wechatService;

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
}
