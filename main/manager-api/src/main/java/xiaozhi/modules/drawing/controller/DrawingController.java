package xiaozhi.modules.drawing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.drawing.dto.DrawingStartDTO;
import xiaozhi.modules.drawing.service.DrawingService;
import xiaozhi.modules.drawing.vo.DrawingCompleteVO;
import xiaozhi.modules.drawing.vo.DrawingConfigVO;
import xiaozhi.modules.drawing.vo.DrawingStartVO;
import xiaozhi.modules.security.user.SecurityUser;

/** 一起画认证业务接口；身份来自服务端登录上下文，金额在完成时按当前配置结算。 */
@RestController
@RequestMapping("/app/drawing")
@RequiresPermissions("sys:role:normal")
@RequiredArgsConstructor
public class DrawingController {
    private final DrawingService drawing;

    /** 查询可玩资格、配置和剩余次数。 */
    @GetMapping("/config")
    public Result<DrawingConfigVO> config(@RequestParam("petId") String petId) {
        return new Result<DrawingConfigVO>().ok(drawing.config(SecurityUser.getUserId(), petId));
    }

    /** 按稳定请求标识开局，占次数与局次记录原子提交，不结算星星。 */
    @PostMapping("/start")
    public Result<DrawingStartVO> start(@Valid @RequestBody DrawingStartDTO dto) {
        return new Result<DrawingStartVO>().ok(drawing.start(SecurityUser.getUserId(), dto));
    }

    /** 完成本人已开局的局次；绘画门槛由本地判断，本接口只接收局次 ID 并幂等结算。 */
    @PostMapping("/{sessionId}/complete")
    public Result<DrawingCompleteVO> complete(@PathVariable("sessionId") String sessionId) {
        return new Result<DrawingCompleteVO>().ok(drawing.complete(SecurityUser.getUserId(), sessionId));
    }
}
