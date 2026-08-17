package xiaozhi.modules.storyengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.storyengine.dto.ActionDTO;
import xiaozhi.modules.storyengine.dto.ActionImageUpdateDTO;
import xiaozhi.modules.storyengine.dto.BatchWeightUpdateDTO;
import xiaozhi.modules.storyengine.dto.BigSceneDTO;
import xiaozhi.modules.storyengine.dto.SmallSceneDTO;
import xiaozhi.modules.storyengine.service.StoryActionImageService;
import xiaozhi.modules.storyengine.service.StoryActionService;
import xiaozhi.modules.storyengine.service.StoryBigSceneService;
import xiaozhi.modules.storyengine.service.StorySmallSceneService;
import xiaozhi.modules.storyengine.vo.ActionVO;
import xiaozhi.modules.storyengine.vo.BigSceneVO;
import xiaozhi.modules.storyengine.vo.CaptionsImportVO;
import xiaozhi.modules.storyengine.vo.SmallSceneVO;
import xiaozhi.modules.storyengine.vo.WeightSummaryVO;

import java.util.List;

@Tag(name = "故事引擎管理")
@RestController
@RequestMapping("/storyEngine")
@AllArgsConstructor
public class StoryEngineController {

    private final StoryBigSceneService bigSceneService;
    private final StorySmallSceneService smallSceneService;
    private final StoryActionService actionService;
    private final StoryActionImageService actionImageService;

    @GetMapping("/bigScene/list")
    @Operation(summary = "查询大场景列表")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<BigSceneVO>> listBigScene() {
        return new Result<List<BigSceneVO>>().ok(bigSceneService.listAll());
    }

    @PostMapping("/bigScene")
    @Operation(summary = "新增大场景")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> saveBigScene(@Valid @RequestBody BigSceneDTO dto) {
        bigSceneService.save(dto);
        return new Result<>();
    }

    @PutMapping("/bigScene")
    @Operation(summary = "修改大场景")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> updateBigScene(@Valid @RequestBody BigSceneDTO dto) {
        bigSceneService.update(dto);
        return new Result<>();
    }

    @DeleteMapping("/bigScene/{id}")
    @Operation(summary = "删除大场景(级联删除小场景、动作与图片)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> deleteBigScene(@PathVariable String id) {
        bigSceneService.delete(id);
        return new Result<>();
    }

    @GetMapping("/smallScene/list")
    @Operation(summary = "查询指定大场景下的小场景列表")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<SmallSceneVO>> listSmallScene(@RequestParam String bigSceneId) {
        return new Result<List<SmallSceneVO>>().ok(smallSceneService.listByBigSceneId(bigSceneId));
    }

    @PostMapping("/smallScene")
    @Operation(summary = "新增小场景")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> saveSmallScene(@Valid @RequestBody SmallSceneDTO dto) {
        smallSceneService.save(dto);
        return new Result<>();
    }

    @PutMapping("/smallScene")
    @Operation(summary = "修改小场景")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> updateSmallScene(@Valid @RequestBody SmallSceneDTO dto) {
        smallSceneService.update(dto);
        return new Result<>();
    }

    @PutMapping("/smallScene/batchWeights")
    @Operation(summary = "批量修改小场景时段权重")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> batchUpdateWeights(@Valid @RequestBody BatchWeightUpdateDTO dto) {
        smallSceneService.batchUpdateWeights(dto);
        return new Result<>();
    }

    @DeleteMapping("/smallScene/{id}")
    @Operation(summary = "删除小场景(级联删除动作与图片)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> deleteSmallScene(@PathVariable String id) {
        smallSceneService.delete(id);
        return new Result<>();
    }

    @GetMapping("/smallScene/weightSummary")
    @Operation(summary = "查询各时段权重合计（全局）")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<WeightSummaryVO> weightSummary() {
        return new Result<WeightSummaryVO>().ok(smallSceneService.getWeightSummary());
    }

    @GetMapping("/action/list")
    @Operation(summary = "查询指定小场景下的动作列表(含图片)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<ActionVO>> listAction(@RequestParam String smallSceneId) {
        return new Result<List<ActionVO>>().ok(actionService.listBySmallSceneId(smallSceneId));
    }

    @PostMapping("/action")
    @Operation(summary = "新增动作")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> saveAction(@Valid @RequestBody ActionDTO dto) {
        actionService.save(dto);
        return new Result<>();
    }

    @PutMapping("/action")
    @Operation(summary = "修改动作")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> updateAction(@Valid @RequestBody ActionDTO dto) {
        actionService.update(dto);
        return new Result<>();
    }

    @DeleteMapping("/action/{id}")
    @Operation(summary = "删除动作(级联删除图片)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> deleteAction(@PathVariable String id) {
        actionService.delete(id);
        return new Result<>();
    }

    @PostMapping("/action/{id}/image")
    @Operation(summary = "上传动作图片到OSS")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> uploadActionImage(@PathVariable String id,
            @RequestParam String petPrototype,
            @RequestParam String timeOfDay,
            @RequestParam(required = false) String captions,
            @RequestParam(required = false) String tag,
            @RequestParam("file") MultipartFile file) {
        actionImageService.uploadImage(id, petPrototype, timeOfDay, captions, tag, file);
        return new Result<>();
    }

    @PutMapping("/actionImage")
    @Operation(summary = "修改动作图片配文与标签(整体更新,两字段一起提交)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> updateActionImage(@Valid @RequestBody ActionImageUpdateDTO dto) {
        actionImageService.updateInfo(dto.getId(), dto.getCaptions(), dto.getTag());
        return new Result<>();
    }

    @PostMapping("/actionImage/importCaptions")
    @Operation(summary = "通过Excel模版批量更新图片配文(大场景/小场景/动作/时段/宠物类型/图片文案)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<CaptionsImportVO> importCaptions(@RequestParam("file") MultipartFile file) {
        return new Result<CaptionsImportVO>().ok(actionImageService.importCaptions(file));
    }

    @DeleteMapping("/actionImage/{id}")
    @Operation(summary = "删除动作图片(含OSS文件)")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> deleteActionImage(@PathVariable String id) {
        actionImageService.delete(id);
        return new Result<>();
    }
}
