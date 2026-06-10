package xiaozhi.modules.subscription.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 订阅套餐档位
 */
@Data
@TableName("ai_subscription_plan")
@Schema(description = "订阅套餐档位")
public class SubscriptionPlanEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "档位编码: bronze/silver/gold")
    private String planCode;

    @Schema(description = "档位名称")
    private String planName;

    @Schema(description = "周期天数")
    private Integer durationDays;

    @Schema(description = "原价(分)")
    private Long priceFen;

    @Schema(description = "促销价(分)")
    private Long promoPriceFen;

    @Schema(description = "权益JSON数组")
    private String features;

    @Schema(description = "附赠道具JSON数组")
    private String bonusItems;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "0下架 1上架")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "修改时间")
    private Date updatedAt;
}
