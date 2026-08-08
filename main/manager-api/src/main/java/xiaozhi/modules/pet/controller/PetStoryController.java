package xiaozhi.modules.pet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.storyengine.service.PetStoryQueryService;
import xiaozhi.modules.storyengine.vo.PetStoryHistoryVO;
import xiaozhi.modules.storyengine.vo.PetStoryStateVO;

import java.util.Map;

@Tag(name = "宠物故事状态")
@RestController
@RequestMapping("/pet")
@RequiredArgsConstructor
public class PetStoryController {

    private final PetStoryQueryService queryService;

    @Operation(summary = "宠物原型共享故事当前状态")
    @GetMapping("/{id}/story-state")
    @RequiresPermissions("sys:role:normal")
    public Result<PetStoryStateVO> current(@PathVariable String id) {
        return new Result<PetStoryStateVO>().ok(
                queryService.getCurrent(SecurityUser.getUserId(), id));
    }

    @Operation(summary = "宠物原型共享故事历史")
    @GetMapping("/{id}/story-history")
    @RequiresPermissions("sys:role:normal")
    public Result<PageData<PetStoryHistoryVO>> history(
            @PathVariable String id, @RequestParam Map<String, Object> params) {
        return new Result<PageData<PetStoryHistoryVO>>().ok(
                queryService.getHistory(SecurityUser.getUserId(), id, params));
    }
}
