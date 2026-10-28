package xiaozhi.modules.listen.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 题库导入冲突明细项：判重（整单失败制）触发时，逐条告知运营冲突位置与原因。
 * 冲突分两类：组名在大类内重复（questionText 为空）、题干在组内重复。
 */
@Data
@Schema(description = "题库导入冲突明细项")
public class ListenImportConflictVO {

    /**
     * 冲突类型：GROUP_NAME（组名在大类内重复）、QUESTION_TEXT（题干在组内重复）
     */
    @Schema(description = "冲突类型：GROUP_NAME=组名重复，QUESTION_TEXT=题干重复")
    private String type;

    /**
     * 冲突所属题目大类（字典 LISTEN_CATEGORY 的 dict_value）
     */
    @Schema(description = "题目大类 code")
    private String categoryCode;

    /**
     * 冲突所属题目分组名称
     */
    @Schema(description = "题目分组名称")
    private String groupName;

    /**
     * 冲突题干（仅 QUESTION_TEXT 类型有值）
     */
    @Schema(description = "冲突题干")
    private String questionText;

    /**
     * 冲突原因说明（与库内已有数据重复 / 文件内部重复）
     */
    @Schema(description = "冲突原因")
    private String reason;
}
