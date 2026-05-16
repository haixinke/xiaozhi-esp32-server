package xiaozhi.modules.pet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.UserProfileVO;

@Tag(name = "用户画像管理")
@RestController
@RequestMapping("/pet/profile")
@AllArgsConstructor
public class ProfileController {

    private final PetService petService;

    @GetMapping
    @Operation(summary = "根据设备ID查询用户画像")
    @Parameters({
            @Parameter(name = "deviceId", description = "设备ID (user_id)", required = true)
    })
    public Result<UserProfileVO> getUserProfileByDeviceId(
            @RequestParam("deviceId") String deviceId) {
        UserProfileVO profile = petService.getUserProfileByDeviceId(deviceId);
        return new Result<UserProfileVO>().ok(profile);
    }
}
