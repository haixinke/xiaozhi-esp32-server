package xiaozhi.modules.companion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.companion.service.CompanionService;

import java.util.Map;

/**
 * 伴侣实时上下文端点。
 * <p>
 * 由 xiaozhi-server 的 ContextDataProvider 每轮携带 device-id 头调用，返回当前易变状态
 * （时段/心情/亲密度/生理），注入到系统提示词的动态上下文中。
 * 路径位于 /config/** 之下，受 server 服务密钥过滤器保护。
 */
@Tag(name = "伴侣实时上下文")
@RestController
@RequestMapping("/config")
@AllArgsConstructor
public class CompanionContextController {

    private final CompanionService companionService;

    @GetMapping("/companion-context")
    @Operation(summary = "获取伴侣实时易变状态")
    public Result<Map<String, String>> companionContext(
            @RequestHeader(value = "device-id", required = false) String deviceId) {
        return new Result<Map<String, String>>().ok(companionService.buildRealtimeContext(deviceId));
    }
}
