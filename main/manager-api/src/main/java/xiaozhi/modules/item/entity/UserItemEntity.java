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
@TableName("ai_user_item")
@Schema(description = "用户道具库存")
public class UserItemEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "道具编码")
    private String skuCode;

    @Schema(description = "累计获得总数")
    private Integer totalCount;

    @Schema(description = "已消耗数量")
    private Integer usedCount;

    @Schema(description = "剩余数量")
    private Integer remainCount;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新时间")
    private Date updatedAt;
}
