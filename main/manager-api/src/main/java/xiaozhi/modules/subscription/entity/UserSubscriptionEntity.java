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
 * 用户订阅记录
 */
@Data
@TableName("ai_user_subscription")
@Schema(description = "用户订阅记录")
public class UserSubscriptionEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "档位ID")
    private Long planId;

    @Schema(description = "档位编码冗余")
    private String planCode;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "权益JSON快照")
    private String featuresSnapshot;

    @Schema(description = "生效时间")
    private Date startAt;

    @Schema(description = "到期时间")
    private Date endAt;

    @Schema(description = "状态: 0未生效 1生效中 2已过期 3已退款")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createdAt;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "修改时间")
    private Date updatedAt;
}
