package xiaozhi.modules.star.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.star.entity.StarTransactionEntity;

import java.util.Date;

@Data
@Schema(description = "星星罐流水")
public class StarTransactionVO {

    @Schema(description = "流水ID")
    private Long id;

    @Schema(description = "类型: earn-赚取, consume-消费")
    private String type;

    @Schema(description = "业务类型(渠道/场景)")
    private String bizType;

    @Schema(description = "变动数量(earn为正,consume为负)")
    private Long amount;

    @Schema(description = "操作后余额")
    private Long balanceAfter;

    @Schema(description = "宠物原型(仅 travel)")
    private String petPrototype;

    @Schema(description = "履约状态: pending/fulfilled")
    private String fulfillStatus;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createDate;

    public static StarTransactionVO toVO(StarTransactionEntity entity) {
        StarTransactionVO vo = new StarTransactionVO();
        vo.setId(entity.getId());
        vo.setType(entity.getType());
        vo.setBizType(entity.getBizType());
        vo.setAmount(entity.getAmount());
        vo.setBalanceAfter(entity.getBalanceAfter());
        vo.setPetPrototype(entity.getPetPrototype());
        vo.setFulfillStatus(entity.getFulfillStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateDate(entity.getCreateDate());
        return vo;
    }
}
