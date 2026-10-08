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
 * 履约：即时场景(exchange/lottery)写入即 fulfilled；travel 预订写入 pending，
 * 故事引擎在旅行结束时消耗最早一笔 pending 并回填 fulfill_ref_id(日记ID)。
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

    @Schema(description = "宠物原型(仅 travel 消费必填): KOI/RABBIT")
    private String petPrototype;

    @Schema(description = "履约状态: pending-待履约(仅travel), fulfilled-已履约")
    private String fulfillStatus;

    @Schema(description = "履约产物ID(如旅行日记ID),履约时回填")
    private String fulfillRefId;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建者")
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createDate;
}
