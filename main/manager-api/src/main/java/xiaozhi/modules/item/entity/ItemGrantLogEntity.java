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
@TableName("ai_item_grant_log")
@Schema(description = "道具发放流水")
public class ItemGrantLogEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "道具编码")
    private String skuCode;

    @Schema(description = "发放数量")
    private Integer count;

    @Schema(description = "发放来源: purchase/subscription_bonus/admin_grant")
    private String source;

    @Schema(description = "来源关联ID(订单号等)")
    private String sourceRef;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;
}
