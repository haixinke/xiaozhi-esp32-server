package xiaozhi.modules.smallobject.controller;

import java.util.List;
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
import xiaozhi.common.validator.group.DefaultGroup;
import xiaozhi.common.validator.group.UpdateGroup;
import xiaozhi.modules.smallobject.dto.SmallObjectDTO;
import xiaozhi.modules.smallobject.dto.SmallObjectStatusDTO;
import xiaozhi.modules.smallobject.service.SmallObjectService;
import xiaozhi.modules.smallobject.vo.SmallObjectTypeVO;
import xiaozhi.modules.smallobject.vo.SmallObjectVO;

/**
 * 小物件管理（运营管理菜单下的「小物件管理」子项）。
 * 本片提供只读列表/详情、新增、编辑、状态切换与删除。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/admin/small-object")
@Tag(name = "小物件管理")
public class SmallObjectController {

    private final SmallObjectService smallObjectService;

    @GetMapping("/page")
    @Operation(summary = "分页查询小物件列表")
    @RequiresPermissions("sys:role:superAdmin")
    @Parameters({ @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true) })
    public Result<PageData<SmallObjectVO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        ValidatorUtils.validateEntity(params);
        PageData<SmallObjectVO> page = smallObjectService.page(params);
        return new Result<PageData<SmallObjectVO>>().ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取小物件详情")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<SmallObjectVO> get(@PathVariable("id") Long id) {
        SmallObjectVO vo = smallObjectService.get(id);
        return new Result<SmallObjectVO>().ok(vo);
    }

    @PostMapping("/save")
    @Operation(summary = "新增小物件")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> save(@RequestBody SmallObjectDTO dto) {
        ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class);
        smallObjectService.save(dto);
        return new Result<>();
    }

    @PutMapping("/update")
    @Operation(summary = "更新小物件")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> update(@RequestBody SmallObjectDTO dto) {
        ValidatorUtils.validateEntity(dto, UpdateGroup.class, DefaultGroup.class);
        smallObjectService.update(dto);
        return new Result<>();
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用/停用小物件")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> updateStatus(@RequestBody SmallObjectStatusDTO dto) {
        ValidatorUtils.validateEntity(dto);
        smallObjectService.updateStatus(dto.getId(), dto.getStatus());
        return new Result<>();
    }

    @GetMapping("/object-types")
    @Operation(summary = "查询可选物件类型（含是否已被占用）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<SmallObjectTypeVO>> objectTypes() {
        return new Result<List<SmallObjectTypeVO>>().ok(smallObjectService.listObjectTypes());
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除小物件（软删，列表与详情按不存在处理）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> delete(@PathVariable("id") Long id) {
        smallObjectService.delete(id);
        return new Result<>();
    }
}
