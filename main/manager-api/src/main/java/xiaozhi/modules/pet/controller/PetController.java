package xiaozhi.modules.pet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.pet.dto.HatchActionDTO;
import xiaozhi.modules.pet.dto.PetAdoptDTO;
import xiaozhi.modules.pet.dto.PetBirthDTO;
import xiaozhi.modules.pet.dto.PetUpdateDTO;
import xiaozhi.modules.pet.service.HatchActionService;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.HatchActionResultVO;
import xiaozhi.modules.pet.vo.HatchActionVO;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.security.user.SecurityUser;

import java.util.List;

@Tag(name = "AI宠物管理")
@RestController
@RequestMapping("/pet")
@AllArgsConstructor
public class PetController {

    private final PetService petService;
    private final HatchActionService hatchActionService;

    @PostMapping("/adopt")
    @Operation(summary = "领养蛋")
    @RequiresPermissions("sys:role:normal")
    public Result<PetVO> adopt(@Valid @RequestBody PetAdoptDTO dto) {
        Long userId = SecurityUser.getUserId();
        PetVO pet = petService.adopt(userId, dto);
        return new Result<PetVO>().ok(pet);
    }

    @PostMapping("/birth")
    @Operation(summary = "宠物出生")
    public Result<PetVO> birth(@Valid @RequestBody PetBirthDTO dto) {
        PetVO pet = petService.birth(dto.getDeviceId());
        return new Result<PetVO>().ok(pet);
    }

    @GetMapping("/detail/{deviceId}")
    @Operation(summary = "查询宠物详情")
    public Result<PetVO> detail(@PathVariable String deviceId) {
        PetVO pet = petService.getByDeviceId(deviceId);
        return new Result<PetVO>().ok(pet);
    }

    @GetMapping("/{id}")
    @Operation(summary = "按petId查询宠物")
    @RequiresPermissions("sys:role:normal")
    public Result<PetVO> getById(@PathVariable String id) {
        Long userId = SecurityUser.getUserId();
        return new Result<PetVO>().ok(petService.getById(userId, id));
    }

    @PutMapping("/{id}/scene")
    @Operation(summary = "更换场景图")
    @RequiresPermissions("sys:role:normal")
    public Result<PetVO> changeScene(@PathVariable String id) {
        Long userId = SecurityUser.getUserId();
        return new Result<PetVO>().ok(petService.changeScene(userId, id));
    }

    @PostMapping("/{id}/hatch")
    @Operation(summary = "破壳")
    @RequiresPermissions("sys:role:normal")
    public Result<PetVO> hatch(@PathVariable String id) {
        Long userId = SecurityUser.getUserId();
        return new Result<PetVO>().ok(petService.hatch(userId, id));
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

    @PostMapping("/{id}/hatch-action")
    @Operation(summary = "孵化修炼动作")
    @RequiresPermissions("sys:role:normal")
    public Result<HatchActionResultVO> hatchAction(@PathVariable String id, @Valid @RequestBody HatchActionDTO dto) {
        Long userId = SecurityUser.getUserId();
        return new Result<HatchActionResultVO>().ok(hatchActionService.recordHatchAction(userId, id, dto));
    }

    @GetMapping("/{id}/hatch-actions")
    @Operation(summary = "查询修炼动作记录")
    @RequiresPermissions("sys:role:normal")
    public Result<List<HatchActionVO>> hatchActions(@PathVariable String id) {
        Long userId = SecurityUser.getUserId();
        return new Result<List<HatchActionVO>>().ok(hatchActionService.listByPetId(userId, id));
    }
}
