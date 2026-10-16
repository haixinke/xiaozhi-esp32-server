package xiaozhi.modules.game.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.common.validator.ValidatorUtils;
import xiaozhi.common.validator.group.AddGroup;
import xiaozhi.common.validator.group.UpdateGroup;
import xiaozhi.modules.game.dto.GameDTO;
import xiaozhi.modules.game.service.GameService;
import xiaozhi.modules.game.vo.GameVO;

/**
 * 游戏管理（运营管理菜单下的游戏配置 CRUD）
 */
@AllArgsConstructor
@RestController
@RequestMapping("/admin/game")
@Tag(name = "游戏管理")
public class GameController {

    private final GameService gameService;

    @GetMapping("/page")
    @Operation(summary = "分页查询游戏列表")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({ @Parameter(name = "gameType", description = "游戏类型"),
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true) })
    public Result<PageData<GameVO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        ValidatorUtils.validateEntity(params);
        PageData<GameVO> page = gameService.page(params);
        return new Result<PageData<GameVO>>().ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取游戏详情")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<GameVO> get(@PathVariable("id") Long id) {
        GameVO vo = gameService.get(id);
        return new Result<GameVO>().ok(vo);
    }

    @PostMapping("/save")
    @Operation(summary = "新增游戏")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> save(@RequestBody GameDTO dto) {
        ValidatorUtils.validateEntity(dto, AddGroup.class);
        gameService.save(dto);
        return new Result<>();
    }

    @PutMapping("/update")
    @Operation(summary = "更新游戏")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> update(@RequestBody GameDTO dto) {
        ValidatorUtils.validateEntity(dto, UpdateGroup.class);
        gameService.update(dto);
        return new Result<>();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除游戏")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> delete(@PathVariable("id") Long id) {
        gameService.delete(id);
        return new Result<>();
    }
}
