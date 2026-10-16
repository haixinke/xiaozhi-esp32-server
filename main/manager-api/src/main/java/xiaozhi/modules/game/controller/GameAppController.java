package xiaozhi.modules.game.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.game.service.GameService;
import xiaozhi.modules.game.vo.GameVO;

/**
 * 游戏（小程序端）：只读接口，向已登录的小程序用户返回启用的游戏列表。
 * 与运营端 GameController（/admin/game）分离，避免后台权限暴露给 C 端。
 */
@Tag(name = "游戏-小程序端")
@RestController
@RequestMapping("/app/game")
@AllArgsConstructor
public class GameAppController {

    private final GameService gameService;

    @GetMapping("/list")
    @Operation(summary = "启用游戏列表")
    @RequiresPermissions("sys:role:normal")
    public Result<List<GameVO>> list(
            @Parameter(description = "游戏入口（如 NOTE），可选；传入时只返回包含该入口的游戏")
            @RequestParam(value = "entrance", required = false) String entrance) {
        return new Result<List<GameVO>>().ok(gameService.listEnabled(entrance));
    }
}
