package xiaozhi.modules.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.item.entity.ItemSkuEntity;

@Data
@Schema(description = "道具SKU")
public class ItemSkuVO {

    @Schema(description = "SKU ID")
    private Long id;

    @Schema(description = "道具编码")
    private String skuCode;

    @Schema(description = "道具名称")
    private String skuName;

    @Schema(description = "道具大类")
    private String category;

    @Schema(description = "原价(分)")
    private Long priceFen;

    @Schema(description = "促销价(分)")
    private Long promoPriceFen;

    @Schema(description = "属性JSON")
    private String attributes;

    @Schema(description = "图标URL")
    private String iconUrl;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "排序")
    private Integer sort;

    /** 将 Entity 转换为 VO（过滤 status 等内部字段） */
    public static ItemSkuVO toVO(ItemSkuEntity entity) {
        ItemSkuVO vo = new ItemSkuVO();
        vo.setId(entity.getId());
        vo.setSkuCode(entity.getSkuCode());
        vo.setSkuName(entity.getSkuName());
        vo.setCategory(entity.getCategory());
        vo.setPriceFen(entity.getPriceFen());
        vo.setPromoPriceFen(entity.getPromoPriceFen());
        vo.setAttributes(entity.getAttributes());
        vo.setIconUrl(entity.getIconUrl());
        vo.setDescription(entity.getDescription());
        vo.setSort(entity.getSort());
        return vo;
    }
}
