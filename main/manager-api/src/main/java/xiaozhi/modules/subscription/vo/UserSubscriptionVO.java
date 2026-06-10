package xiaozhi.modules.subscription.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.subscription.entity.UserSubscriptionEntity;

import java.util.Date;
import java.util.List;

@Data
@Schema(description = "用户订阅信息")
public class UserSubscriptionVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "档位ID")
    private Long planId;

    @Schema(description = "档位编码")
    private String planCode;

    @Schema(description = "权益列表")
    private List<String> features;

    @Schema(description = "生效时间")
    private Date startAt;

    @Schema(description = "到期时间")
    private Date endAt;

    @Schema(description = "状态: 0未生效 1生效中 2已过期 3已退款")
    private Integer status;

    /** 当前距离过期的剩余秒数（已过期为0） */
    @Schema(description = "剩余秒数")
    private Long remainingSeconds;

    /** 将 Entity 转换为 VO，自动计算剩余秒数。 */
    public static UserSubscriptionVO toVO(UserSubscriptionEntity entity, List<String> features) {
        UserSubscriptionVO vo = new UserSubscriptionVO();
        vo.setId(entity.getId());
        vo.setPlanId(entity.getPlanId());
        vo.setPlanCode(entity.getPlanCode());
        vo.setFeatures(features);
        vo.setStartAt(entity.getStartAt());
        vo.setEndAt(entity.getEndAt());
        vo.setStatus(entity.getStatus());
        long remain = (entity.getEndAt().getTime() - System.currentTimeMillis()) / 1000L;
        vo.setRemainingSeconds(Math.max(0L, remain));
        return vo;
    }
}
