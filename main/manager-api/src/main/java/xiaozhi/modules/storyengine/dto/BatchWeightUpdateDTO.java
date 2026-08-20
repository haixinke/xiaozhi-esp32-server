package xiaozhi.modules.storyengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "故事引擎-小场景权重批量修改请求")
public class BatchWeightUpdateDTO {

    @Valid
    @NotEmpty(message = "权重列表不能为空")
    @Schema(description = "待修改的小场景权重列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SmallSceneWeightItem> items;

    @Data
    @Schema(description = "小场景权重项")
    public static class SmallSceneWeightItem {

        @NotBlank(message = "小场景ID不能为空")
        @Schema(description = "小场景ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private String id;

        @Min(value = 0, message = "深夜时段权重不能小于0")
        @Max(value = 100, message = "深夜时段权重不能大于100")
        @Schema(description = "深夜时段(19:00~06:59)权重百分比")
        private Integer weightNight;

        @Min(value = 0, message = "上午时段权重不能小于0")
        @Max(value = 100, message = "上午时段权重不能大于100")
        @Schema(description = "上午时段(07:00~11:59)权重百分比")
        private Integer weightMorning;

        @Min(value = 0, message = "下午时段权重不能小于0")
        @Max(value = 100, message = "下午时段权重不能大于100")
        @Schema(description = "下午时段(12:00~16:59)权重百分比")
        private Integer weightAfternoon;

        @Min(value = 0, message = "傍晚时段权重不能小于0")
        @Max(value = 100, message = "傍晚时段权重不能大于100")
        @Schema(description = "傍晚时段(17:00~18:59)权重百分比")
        private Integer weightEvening;
    }
}
