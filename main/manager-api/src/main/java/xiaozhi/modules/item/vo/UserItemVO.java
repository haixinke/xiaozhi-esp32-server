package xiaozhi.modules.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.item.entity.UserItemEntity;

@Data
@Schema(description = "用户道具库存")
public class UserItemVO {

    @Schema(description = "道具编码")
    private String skuCode;

    @Schema(description = "道具名称")
    private String skuName;

    @Schema(description = "道具大类")
    private String category;

    @Schema(description = "累计获得总数")
    private Integer totalCount;

    @Schema(description = "已消耗数量")
    private Integer usedCount;

    @Schema(description = "剩余数量")
    private Integer remainCount;

    /** 将 Entity 转换为 VO，补充 SKU 元信息 */
    public static UserItemVO toVO(UserItemEntity entity, String skuName, String category) {
        UserItemVO vo = new UserItemVO();
        vo.setSkuCode(entity.getSkuCode());
        vo.setSkuName(skuName);
        vo.setCategory(category);
        vo.setTotalCount(entity.getTotalCount());
        vo.setUsedCount(entity.getUsedCount());
        vo.setRemainCount(entity.getRemainCount());
        return vo;
    }
}
