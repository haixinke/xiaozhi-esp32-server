package xiaozhi.modules.dailynote.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.dailynote.service.DailyNoteTodayService;
import xiaozhi.modules.dailynote.vo.DailyNoteTodayVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 小纸条（小程序端）：当日查询接口。
 * 日期由服务器按 Asia/Shanghai 决定，不接受客户端日期参数；
 * 用户身份只来自认证 Token。与运营端 DailyNoteController（/admin/daily-note）分离，
 * 避免后台权限暴露给 C 端。
 */
@Tag(name = "小纸条-小程序端")
@RestController
@RequestMapping("/app/daily-note")
@AllArgsConstructor
public class DailyNoteAppController {

    private final DailyNoteTodayService dailyNoteTodayService;

    @GetMapping("/today")
    @Operation(summary = "当日小纸条与随机游戏（无内容或无合格游戏时返回空）")
    @RequiresPermissions("sys:role:normal")
    public Result<DailyNoteTodayVO> today() {
        // Service 返回 null 时统一为 code=0、data=null 的成功空响应，不下发部分内容
        return new Result<DailyNoteTodayVO>().ok(dailyNoteTodayService.today(SecurityUser.getUserId()));
    }
}
