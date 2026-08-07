package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.storyengine.entity.BigSceneEntity;

@Data
@Schema(description = "故事引擎-大场景视图对象")
public class BigSceneVO {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "大场景名称(如:在家、旅行、上学、打工)")
    private String name;

    @Schema(description = "排序序号,越小越靠前")
    private Integer sortOrder;

    @Schema(description = "状态: 1=启用 0=禁用")
    private Integer status;

    public static BigSceneVO toVO(BigSceneEntity entity) {
        BigSceneVO vo = new BigSceneVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
