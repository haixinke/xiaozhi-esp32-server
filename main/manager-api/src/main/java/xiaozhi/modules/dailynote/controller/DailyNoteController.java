package xiaozhi.modules.dailynote.controller;

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
import xiaozhi.modules.dailynote.dto.DailyNoteSaveDTO;
import xiaozhi.modules.dailynote.service.DailyNoteService;
import xiaozhi.modules.dailynote.vo.DailyNoteVO;

/**
 * 小纸条管理（运营管理菜单下的「小纸条管理」）。
 * 分页浏览、按完整日期精确查询、新增、编辑和按 ID 物理删除；
 * 全部接口要求超级管理员权限。JSON 导入在后续 ticket 落地。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/admin/daily-note")
@Tag(name = "小纸条管理")
public class DailyNoteController {

    private final DailyNoteService dailyNoteService;

    @GetMapping("/page")
    @Operation(summary = "分页查询小纸条列表（默认日期倒序）")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({ @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true),
            @Parameter(name = "noteDate", description = "完整日期(yyyy-MM-dd)精确过滤，不传查全部") })
    public Result<PageData<DailyNoteVO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        ValidatorUtils.validateEntity(params);
        PageData<DailyNoteVO> page = dailyNoteService.page(params);
        return new Result<PageData<DailyNoteVO>>().ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取小纸条详情（含完整正文）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<DailyNoteVO> get(@PathVariable("id") Long id) {
        DailyNoteVO vo = dailyNoteService.get(id);
        return new Result<DailyNoteVO>().ok(vo);
    }

    @PostMapping
    @Operation(summary = "新增小纸条（目标日期已存在时拒绝）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> create(@RequestBody DailyNoteSaveDTO dto) {
        ValidatorUtils.validateEntity(dto);
        dailyNoteService.create(dto);
        return new Result<>();
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑小纸条（日期与其他记录冲突时拒绝，编辑自身不算冲突）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody DailyNoteSaveDTO dto) {
        ValidatorUtils.validateEntity(dto);
        dailyNoteService.update(id, dto);
        return new Result<>();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "物理删除小纸条（不可恢复，不影响游戏与用户已打开记录）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> delete(@PathVariable("id") Long id) {
        dailyNoteService.delete(id);
        return new Result<>();
    }
}
