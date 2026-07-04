package xiaozhi.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "伴侣亲密度信息")
public class CompanionIntimacyVO {

    @Schema(description = "亲密度 0.0~1.0")
    private Float intimacy;

    @Schema(description = "等级号 1~5")
    private Integer level;

    @Schema(description = "等级名")
    private String levelName;

    @Schema(description = "当前档内进度 0~1")
    private Float progressToNext;

    @Schema(description = "下一等级名（已满级则与当前相同）")
    private String nextLevelName;

    @Schema(description = "连续陪伴天数")
    private Integer streak;

    @Schema(description = "最近活跃日")
    private String lastActiveDate;
}
