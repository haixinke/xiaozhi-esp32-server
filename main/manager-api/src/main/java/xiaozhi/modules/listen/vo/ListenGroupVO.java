package xiaozhi.modules.listen.vo;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 题目分组 VO，用于管理端题组分页列表。
 * categoryCode 是字典 LISTEN_CATEGORY 的 dict_value，categoryName 是其 dict_label 译文；
 * 字典里查不到时 categoryName 回落为原始 code（与 smallobject 的翻译回落策略一致）。
 */
@Data
@Schema(description = "题目分组VO")
public class ListenGroupVO implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "题目大类(字典 LISTEN_CATEGORY 的 dict_value)")
    private String categoryCode;

    @Schema(description = "题目大类名称(字典 dict_label，翻译不到时为原始 dict_value)")
    private String categoryName;

    @Schema(description = "题目分组名称")
    private String name;

    @Schema(description = "排序序号，越小越靠前")
    private Integer sortOrder;

    @Schema(description = "组内题目数")
    private Integer questionCount;

    @Schema(description = "创建时间")
    private Date createDate;

    @Schema(description = "更新时间")
    private Date updateDate;
}
