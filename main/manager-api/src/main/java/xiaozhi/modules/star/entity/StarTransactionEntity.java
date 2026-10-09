package xiaozhi.modules.star.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 星星罐流水：earn/consume 单表，amount 带正负。
 * 幂等：唯一索引 (user_id, biz_type, ref_id)，refId 为调用方业务单号，强制非空。
 * 流水只承载资金变动；履约状态/履约产物/宠物原型等履约语义不在本表（ADR 0009），
 * 由 refId 关联的业务对象承载，异步履约场景落地时再建对应实体。
 */
@Data
@TableName("ai_star_transaction")
@Schema(description = "星星罐流水")
public class StarTransactionEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID(sys_user.id)")
    private Long userId;

    @Schema(description = "类型: earn-赚取, consume-消费")
    private String type;

    @Schema(description = "业务类型: earn[sign_in/ad_reward/invite/activity/admin_grant], consume[exchange/lottery/travel]")
    private String bizType;

    @Schema(description = "业务单号(幂等键): 签到=日期, 兑换=订单号, 旅行预订=tv-前缀单号")
    private String refId;

    @Schema(description = "变动数量(earn为正,consume为负)")
    private Long amount;

    @Schema(description = "操作后余额快照")
    private Long balanceAfter;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建者")
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createDate;
}
