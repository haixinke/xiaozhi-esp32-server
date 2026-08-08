package xiaozhi.modules.storyengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "故事引擎-小场景请求")
public class SmallSceneDTO {

    @Schema(description = "ID，新增时为空，修改时必填")
    private String id;

    @Schema(description = "所属大场景ID，新增时必填")
    private String bigSceneId;

    @NotBlank(message = "小场景名称不能为空")
    @Schema(description = "小场景名称(如:卧室、北京-故宫、快餐厅)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Min(value = 0, message = "深夜时段权重不能小于0")
    @Max(value = 100, message = "深夜时段权重不能大于100")
    @Schema(description = "深夜时段(00:00~05:59)权重百分比")
    private Integer weightNight;

    @Min(value = 0, message = "上午时段权重不能小于0")
    @Max(value = 100, message = "上午时段权重不能大于100")
    @Schema(description = "上午时段(06:00~11:59)权重百分比")
    private Integer weightMorning;

    @Min(value = 0, message = "下午时段权重不能小于0")
    @Max(value = 100, message = "下午时段权重不能大于100")
    @Schema(description = "下午时段(12:00~17:59)权重百分比")
    private Integer weightAfternoon;

    @Min(value = 0, message = "傍晚时段权重不能小于0")
    @Max(value = 100, message = "傍晚时段权重不能大于100")
    @Schema(description = "傍晚时段(18:00~23:59)权重百分比")
    private Integer weightEvening;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "状态: 1=启用 0=禁用")
    private Integer status;
}
