package xiaozhi.modules.dailynote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小纸条 JSON 导入结果：新增数与覆盖数，供运营核对实际影响。
 */
@Data
@Schema(description = "小纸条导入结果统计")
public class DailyNoteImportResultVO {

    /**
     * 新增的小纸条数量（库内无同日期记录）
     */
    @Schema(description = "新增数量")
    private Integer addedCount;

    /**
     * 覆盖的小纸条数量（库内已有同日期记录，整项覆盖内容并保留 id/创建人/创建时间）
     */
    @Schema(description = "覆盖数量")
    private Integer overwrittenCount;
}
