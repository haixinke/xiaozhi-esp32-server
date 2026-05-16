package xiaozhi.modules.pet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.MemoryVO;

import java.util.Map;

@Tag(name = "宠物记忆管理")
@RestController
@RequestMapping("/pet/memory")
@AllArgsConstructor
public class MemoryController {

    private final PetService petService;

    @GetMapping("/list")
    @Operation(summary = "根据设备ID查询记忆记录")
    @Parameters({
            @Parameter(name = "deviceId", description = "设备ID (user_id)", required = true),
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true),
    })
    @RequiresPermissions("sys:role:normal")
    public Result<PageData<MemoryVO>> getMemoryByDeviceId(
            @RequestParam("deviceId") String deviceId,
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        PageData<MemoryVO> page = petService.getMemoryByDeviceId(deviceId, params);
        return new Result<PageData<MemoryVO>>().ok(page);
    }
}
