package xiaozhi.modules.storyengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "故事引擎-大场景请求")
public class BigSceneDTO {

    @Schema(description = "ID，新增时为空，修改时必填")
    private String id;

    @NotBlank(message = "大场景名称不能为空")
    @Schema(description = "大场景名称(如:在家、旅行、上学、打工)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "排序序号,越小越靠前")
    private Integer sortOrder;

    @Schema(description = "状态: 1=启用 0=禁用")
    private Integer status;
}
