package xiaozhi.modules.star.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 每日星星奖励记录，对应 ai_star_daily_reward 表。
 * 统一承载各来源（小纸条/游戏）的每日领取资格与实际奖励快照：
 * (user_id, source_type, source_key, reward_date) 唯一约束保证每用户每来源每天最多一份；
 * star_transaction_id 唯一约束保证奖励记录与钱包流水严格一对一。
 * 只落正数成功奖励；不存连续天数/宠物/设备维度，小纸条连续金额由昨天的成功奖励记录推导。
 */
@Data
@TableName("ai_star_daily_reward")
@Schema(description = "每日星星奖励记录")
public class StarDailyRewardEntity {

    /** 主键（雪花 ID，非自增）；先分配该 ID 作为钱包业务单号，保证奖励与流水一对一 */
    @TableId
    @Schema(description = "主键ID(雪花)")
    private Long id;

    @Schema(description = "用户ID(sys_user.id)")
    private Long userId;

    @Schema(description = "奖励来源类型: DAILY_NOTE/GAME")
    private String sourceType;

    @Schema(description = "来源标识: DAILY_NOTE固定为DAILY_NOTE; GAME为游戏类型code")
    private String sourceKey;

    @Schema(description = "北京时间领取自然日")
    private Date rewardDate;

    @Schema(description = "本次实际奖励数量(成功结算快照，不随配置变动重算)")
    private Long amount;

    @Schema(description = "关联星星入账流水ID")
    private Long starTransactionId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "领取记录创建时间")
    private Date createDate;
}
