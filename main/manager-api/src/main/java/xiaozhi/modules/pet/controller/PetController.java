package xiaozhi.modules.pet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.dto.PetBirthDTO;
import xiaozhi.modules.pet.dto.PetUpdateDTO;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.security.user.SecurityUser;

import java.util.List;

@Tag(name = "AI宠物管理")
@RestController
@RequestMapping("/pet")
@AllArgsConstructor
public class PetController {

    private final PetService petService;

    @PostMapping("/birth")
    @Operation(summary = "宠物出生")
    public Result<PetVO> birth(@Valid @RequestBody PetBirthDTO dto) {
        PetVO pet = petService.birth(dto.getDeviceId());
        return new Result<PetVO>().ok(pet);
    }

    @GetMapping("/detail/{deviceId}")
    @Operation(summary = "查询宠物详情")
    @RequiresPermissions("sys:role:normal")
    public Result<PetVO> detail(@PathVariable String deviceId) {
        PetVO pet = petService.getByDeviceId(deviceId);
        return new Result<PetVO>().ok(pet);
    }

    @GetMapping("/list")
    @Operation(summary = "查询当前用户的宠物列表")
    @RequiresPermissions("sys:role:normal")
    public Result<List<PetVO>> list() {
        Long userId = SecurityUser.getUserId();
        List<PetVO> pets = petService.listByUserId(userId);
        return new Result<List<PetVO>>().ok(pets);
    }

    @PutMapping("/update")
    @Operation(summary = "编辑宠物信息")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> update(@Valid @RequestBody PetUpdateDTO dto) {
        Long userId = SecurityUser.getUserId();
        petService.updatePet(userId, dto.getId(), dto.getNickname());
        return new Result<>();
    }
}
