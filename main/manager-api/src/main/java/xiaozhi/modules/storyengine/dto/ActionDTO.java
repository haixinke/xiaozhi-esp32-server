package xiaozhi.modules.storyengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "故事引擎-动作请求")
public class ActionDTO {

    @Schema(description = "ID，新增时为空，修改时必填")
    private String id;

    @Schema(description = "所属小场景ID，新增时必填")
    private String smallSceneId;

    @NotBlank(message = "动作名称不能为空")
    @Schema(description = "动作名称(如:小憩、看书、故宫红墙前散步)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Min(value = 0, message = "最短时长不能小于0")
    @Schema(description = "最短时长(小时)")
    private Integer durationMin;

    @Min(value = 0, message = "最长时长不能小于0")
    @Schema(description = "最长时长(小时)")
    private Integer durationMax;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "状态: 1=启用 0=禁用")
    private Integer status;
}
