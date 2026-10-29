package xiaozhi.modules.dailynote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小纸条导入校验错误明细项：逐条告知运营哪一行、哪个字段、什么原因，
 * 便于集中修正后重传（行号与 JSON 数组顺序一致，从 1 开始）。
 */
@Data
@Schema(description = "小纸条导入校验错误明细项")
public class DailyNoteImportErrorVO {

    /**
     * 条目在 JSON 数组中的位置（从 1 开始）
     */
    @Schema(description = "数组行号（从 1 开始）")
    private Integer row;

    /**
     * 出错字段名（date/title/content/solarTerm 或未知字段名）；文件级问题（如非 UTF-8）为 null
     */
    @Schema(description = "出错字段名")
    private String field;

    /**
     * 错误原因说明
     */
    @Schema(description = "错误原因")
    private String reason;
}
