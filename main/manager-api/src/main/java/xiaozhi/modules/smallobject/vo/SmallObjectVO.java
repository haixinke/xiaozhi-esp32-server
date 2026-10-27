package xiaozhi.modules.smallobject.vo;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小物件 VO，用于管理端列表与详情展示。
 * objectType 是字典 dict_value，objectTypeName 是其 dict_label 译文；
 * 字典里查不到时 objectTypeName 回落为原始 code。
 */
@Data
@Schema(description = "小物件VO")
public class SmallObjectVO implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "物件类型(字典 SMALL_OBJECT_TYPE 的 dict_value)")
    private String objectType;

    @Schema(description = "物件类型名称(字典 dict_label，翻译不到时为原始 dict_value)")
    private String objectTypeName;

    @Schema(description = "物件图片完整 OSS URL")
    private String imageUrl;

    @Schema(description = "物件背后的故事")
    private String story;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "创建者")
    private Long creator;

    @Schema(description = "创建时间")
    private Date createDate;

    @Schema(description = "更新者")
    private Long updater;

    @Schema(description = "更新时间")
    private Date updateDate;
}