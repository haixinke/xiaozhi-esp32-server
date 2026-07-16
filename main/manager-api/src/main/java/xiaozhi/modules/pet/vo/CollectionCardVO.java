package xiaozhi.modules.pet.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "收藏卡视图对象")
public class CollectionCardVO {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "收藏卡图片URL")
    private String imageUrl;

    @Schema(description = "一句话简介")
    private String brief;

    @Schema(description = "来源类型: HATCH-破壳首卡")
    private String source;

    @Schema(description = "排序序号(0=最先获取)")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private Date createDate;
}
