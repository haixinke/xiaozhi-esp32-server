package xiaozhi.modules.companion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.companion.dto.CompanionCreateDTO;
import xiaozhi.modules.companion.dto.CompanionSyncPromptDTO;
import xiaozhi.modules.companion.dto.CompanionUpdateDTO;
import xiaozhi.modules.companion.service.CompanionService;
import xiaozhi.modules.companion.vo.CompanionVO;

@Tag(name = "AI伴侣管理")
@RestController
@RequestMapping("/companion")
@AllArgsConstructor
public class CompanionController {

    private final CompanionService companionService;

    @PostMapping("/create")
    @Operation(summary = "创建伴侣")
    public Result<CompanionVO> create(@RequestBody @Valid CompanionCreateDTO dto) {
        return new Result<CompanionVO>().ok(companionService.create(dto));
    }

    @PostMapping("/update")
    @Operation(summary = "修改伴侣")
    public Result<CompanionVO> update(@RequestBody @Valid CompanionUpdateDTO dto) {
        return new Result<CompanionVO>().ok(companionService.update(dto));
    }

    @GetMapping("/detail/{deviceId}")
    @Operation(summary = "根据设备ID查询伴侣")
    public Result<CompanionVO> detail(@PathVariable String deviceId) {
        return new Result<CompanionVO>().ok(companionService.getByDeviceId(deviceId));
    }

    @PostMapping("/sync-prompt")
    @Operation(summary = "同步伴侣信息到智能体系统提示词")
    public Result<Void> syncPrompt(@RequestBody @Valid CompanionSyncPromptDTO dto) {
        companionService.syncPromptToAgent(dto.getAgentId(), dto.getCompanionId());
        return new Result<Void>();
    }
}
