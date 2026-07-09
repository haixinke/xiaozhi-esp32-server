package xiaozhi.modules.invite.controller;

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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.invite.dto.InviteCodeCreateDTO;
import xiaozhi.modules.invite.dto.InviteCodeUpdateDTO;
import xiaozhi.modules.invite.dto.InviteConsumeDTO;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.invite.vo.InviteCodeVO;
import xiaozhi.modules.invite.vo.InviteConsumeVO;
import xiaozhi.modules.invite.vo.InviteStatsVO;
import xiaozhi.modules.invite.vo.InviteUsageVO;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "邀请码")
@RestController
@RequestMapping("/invite")
@AllArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/mine")
    @Operation(summary = "查询我的个人邀请码")
    @RequiresPermissions("sys:role:normal")
    public Result<InviteCodeVO> mine() {
        Long userId = SecurityUser.getUserId();
        return new Result<InviteCodeVO>().ok(inviteService.getMine(userId));
    }

    @PostMapping("/consume")
    @Operation(summary = "消耗邀请码领蛋")
    @RequiresPermissions("sys:role:normal")
    public Result<InviteConsumeVO> consume(@Valid @RequestBody InviteConsumeDTO dto) {
        Long userId = SecurityUser.getUserId();
        return new Result<InviteConsumeVO>().ok(inviteService.consume(dto.getCode(), userId));
    }

    @PostMapping
    @Operation(summary = "创建企业邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<InviteCodeVO> create(@Valid @RequestBody InviteCodeCreateDTO dto) {
        return new Result<InviteCodeVO>().ok(inviteService.createEnterprise(dto));
    }

    @PutMapping
    @Operation(summary = "编辑企业邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> update(@Valid @RequestBody InviteCodeUpdateDTO dto) {
        inviteService.update(dto);
        return new Result<>();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除企业邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> delete(@PathVariable Long id) {
        inviteService.delete(id);
        return new Result<>();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询邀请码")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<InviteCodeVO>> page(@RequestParam Map<String, Object> params) {
        return new Result<PageData<InviteCodeVO>>().ok(inviteService.page(params));
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "查询某邀请码使用记录")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<InviteUsageVO>> usage(@PathVariable Long id,
            @RequestParam Map<String, Object> params) {
        return new Result<PageData<InviteUsageVO>>().ok(inviteService.usageList(id, params));
    }

    @GetMapping("/stats")
    @Operation(summary = "邀请码概览统计")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<InviteStatsVO> stats() {
        return new Result<InviteStatsVO>().ok(inviteService.stats());
    }
}
