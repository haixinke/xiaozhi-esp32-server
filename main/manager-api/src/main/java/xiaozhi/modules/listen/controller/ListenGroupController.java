package xiaozhi.modules.listen.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import xiaozhi.modules.listen.dto.ListenGroupSaveDTO;
import xiaozhi.modules.listen.service.ListenGroupService;
import xiaozhi.modules.listen.vo.ListenGroupDetailVO;
import xiaozhi.modules.listen.vo.ListenGroupVO;

/**
 * 题库运营（运营管理菜单下的「题库运营」子项）。
 * 只读：大类下的题组分页与组详情（含组内题目与选项）；
 * 写操作：整组编辑保存、整组/单题物理删除。导入在后续 ticket 落地。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/admin/listen")
@Tag(name = "题库运营")
public class ListenGroupController {

    private final ListenGroupService listenGroupService;

    @GetMapping("/group/page")
    @Operation(summary = "分页查询题组列表")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({ @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true),
            @Parameter(name = "categoryCode", description = "题目大类(字典 LISTEN_CATEGORY 的 dict_value)，不传查全部") })
    public Result<PageData<ListenGroupVO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        ValidatorUtils.validateEntity(params);
        PageData<ListenGroupVO> page = listenGroupService.page(params);
        return new Result<PageData<ListenGroupVO>>().ok(page);
    }

    @GetMapping("/group/{id}")
    @Operation(summary = "获取题组详情（含组内题目与选项）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ListenGroupDetailVO> get(@PathVariable("id") Long id) {
        ListenGroupDetailVO vo = listenGroupService.getDetail(id);
        return new Result<ListenGroupDetailVO>().ok(vo);
    }

    @PutMapping("/group/{id}")
    @Operation(summary = "整组编辑保存（组名 + 组内全部题目，diff：更新保 id / 新增 / 缺失物理删除）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> updateGroup(@PathVariable("id") Long id, @RequestBody ListenGroupSaveDTO dto) {
        ValidatorUtils.validateEntity(dto);
        listenGroupService.updateGroup(id, dto);
        return new Result<>();
    }

    @DeleteMapping("/group/{id}")
    @Operation(summary = "整组物理删除（含组内题目，不可恢复）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> deleteGroup(@PathVariable("id") Long id) {
        listenGroupService.deleteGroup(id);
        return new Result<>();
    }

    @DeleteMapping("/question/{id}")
    @Operation(summary = "单题物理删除（不可恢复）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> deleteQuestion(@PathVariable("id") Long id) {
        listenGroupService.deleteQuestion(id);
        return new Result<>();
    }
}
