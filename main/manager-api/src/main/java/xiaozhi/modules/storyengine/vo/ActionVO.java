package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.storyengine.entity.ActionEntity;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "故事引擎-动作视图对象")
public class ActionVO {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "所属小场景ID")
    private String smallSceneId;

    @Schema(description = "动作名称(如:小憩、看书、故宫红墙前散步)")
    private String name;

    @Schema(description = "最短时长(小时)")
    private Integer durationMin;

    @Schema(description = "最长时长(小时)")
    private Integer durationMax;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "状态: 1=启用 0=禁用")
    private Integer status;

    @Schema(description = "动作图片列表,按原型+时段+序号排序")
    private List<ActionImageVO> images = new ArrayList<>();

    public static ActionVO toVO(ActionEntity entity) {
        ActionVO vo = new ActionVO();
        vo.setId(entity.getId());
        vo.setSmallSceneId(entity.getSmallSceneId());
        vo.setName(entity.getName());
        vo.setDurationMin(entity.getDurationMin());
        vo.setDurationMax(entity.getDurationMax());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
