package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "故事引擎-小场景权重合计")
public class WeightSummaryVO {

    @Schema(description = "深夜时段权重合计")
    private Integer totalNight;

    @Schema(description = "上午时段权重合计")
    private Integer totalMorning;

    @Schema(description = "下午时段权重合计")
    private Integer totalAfternoon;

    @Schema(description = "傍晚时段权重合计")
    private Integer totalEvening;
}
