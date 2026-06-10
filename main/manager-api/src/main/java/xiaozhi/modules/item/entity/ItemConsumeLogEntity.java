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
@TableName("ai_item_consume_log")
@Schema(description = "道具消耗流水")
public class ItemConsumeLogEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "道具编码")
    private String skuCode;

    @Schema(description = "消耗数量(服装美备时为0)")
    private Integer count;

    @Schema(description = "业务类型: occupation_change/soul_quirk_change/outfit_equip/voice_clone/intimacy_gift")
    private String bizType;

    @Schema(description = "业务关联ID")
    private String bizRefId;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;
}
