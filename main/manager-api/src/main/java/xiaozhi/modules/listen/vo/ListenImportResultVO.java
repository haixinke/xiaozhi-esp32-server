package xiaozhi.modules.listen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 题库导入成功统计：写入的组数与题数，供前端明确提示「成功写入 N 组 M 题」。
 */
@Data
@Schema(description = "题库导入成功统计")
public class ListenImportResultVO {

    /**
     * 本次导入写入的题目分组数
     */
    @Schema(description = "写入的分组数")
    private Integer groupCount;

    /**
     * 本次导入写入的题目数
     */
    @Schema(description = "写入的题目数")
    private Integer questionCount;
}
