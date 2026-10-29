package xiaozhi.modules.dailynote.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.dailynote.service.DailyNoteOpenedService;
import xiaozhi.modules.dailynote.service.DailyNoteTodayService;
import xiaozhi.modules.dailynote.vo.DailyNoteTodayVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 小纸条（小程序端）：当日查询与标记已打开接口。
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
    private final DailyNoteOpenedService dailyNoteOpenedService;

    @GetMapping("/today")
    @Operation(summary = "当日小纸条与随机游戏（无内容、已打开或无合格游戏时返回空）")
    @RequiresPermissions("sys:role:normal")
    public Result<DailyNoteTodayVO> today() {
        // Service 返回 null 时统一为 code=0、data=null 的成功空响应，不下发部分内容
        return new Result<DailyNoteTodayVO>().ok(dailyNoteTodayService.today(SecurityUser.getUserId()));
    }

    @PostMapping("/opened")
    @Operation(summary = "标记当日小纸条已打开（重复或并发上报幂等，成功返回空数据）")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> opened() {
        // 无请求正文：用户 ID 来自 Token，日期由服务器时钟决定，客户端均不可指定
        dailyNoteOpenedService.markOpened(SecurityUser.getUserId());
        return new Result<Void>().ok(null);
    }
}
