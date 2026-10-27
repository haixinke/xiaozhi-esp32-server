package xiaozhi.modules.smallobject.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import xiaozhi.modules.smallobject.service.SmallObjectService;
import xiaozhi.modules.smallobject.vo.SmallObjectVO;

/**
 * 小物件管理（运营管理菜单下的「小物件管理」子项）。
 * 本片只提供只读接口；新增/编辑/删除由后续票补齐。
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
}