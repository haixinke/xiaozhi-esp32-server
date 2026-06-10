package xiaozhi.modules.item.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("ai_item_sku")
@Schema(description = "道具SKU")
public class ItemSkuEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "道具编码(唯一标识)")
    private String skuCode;

    @Schema(description = "道具名称")
    private String skuName;

    @Schema(description = "道具大类: consumable_change/outfit/voice_quota/intimacy")
    private String category;

    @Schema(description = "原价(分)")
    private Long priceFen;

    @Schema(description = "促销价(分)")
    private Long promoPriceFen;

    @Schema(description = "属性JSON(如服装图集、声音参数等)")
    private String attributes;

    @Schema(description = "图标URL")
    private String iconUrl;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "状态: 0下架 1上架")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新时间")
    private Date updatedAt;
}
