package xiaozhi.modules.subscription.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.subscription.entity.SubscriptionPlanEntity;

import java.util.List;

@Data
@Schema(description = "订阅档位")
public class SubscriptionPlanVO {

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

    @Schema(description = "权益列表")
    private List<String> features;

    @Schema(description = "附赠道具列表")
    private List<BonusItem> bonusItems;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "排序")
    private Integer sort;

    @Data
    @Schema(description = "附赠道具")
    public static class BonusItem {
        @Schema(description = "道具编码")
        private String skuCode;

        @Schema(description = "数量")
        private Integer count;
    }

    /** 将 Entity 转换为 VO，features 和 bonusItems 需预先解析。 */
    public static SubscriptionPlanVO toVO(SubscriptionPlanEntity entity, List<String> features, List<BonusItem> bonusItems) {
        SubscriptionPlanVO vo = new SubscriptionPlanVO();
        vo.setId(entity.getId());
        vo.setPlanCode(entity.getPlanCode());
        vo.setPlanName(entity.getPlanName());
        vo.setDurationDays(entity.getDurationDays());
        vo.setPriceFen(entity.getPriceFen());
        vo.setPromoPriceFen(entity.getPromoPriceFen());
        vo.setFeatures(features);
        vo.setBonusItems(bonusItems);
        vo.setDescription(entity.getDescription());
        vo.setSort(entity.getSort());
        return vo;
    }
}
