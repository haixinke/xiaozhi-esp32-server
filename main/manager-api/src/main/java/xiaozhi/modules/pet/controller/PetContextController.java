package xiaozhi.modules.pet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.service.PetService;

import java.util.Map;

/**
 * 蛋宝宝实时上下文端点。
 * <p>
 * 由 xiaozhi-server 的 ContextDataProvider 携带 device-id 头调用，返回今日心情，
 * 注入到系统提示词的动态上下文（dynamic_context）中。
 * 路径位于 /config/** 之下，受 server 服务密钥过滤器保护。
 */
@Tag(name = "蛋宝宝实时上下文")
@RestController
@RequestMapping("/config")
@AllArgsConstructor
public class PetContextController {

    private final PetService petService;

    @GetMapping("/pet-context")
    @Operation(summary = "获取蛋宝宝实时易变状态")
    public Result<Map<String, String>> petContext(
            @RequestHeader(value = "device-id", required = false) String deviceId) {
        return new Result<Map<String, String>>().ok(petService.buildRealtimeContext(deviceId));
    }
}
