package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;

@Data
@Schema(description = "故事引擎-动作图片视图对象")
public class ActionImageVO {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "所属动作ID")
    private String actionId;

    @Schema(description = "宠物原型: 锦鲤/玉兔")
    private String petPrototype;

    @Schema(description = "时段类型: 白天/落日/黑夜")
    private String timeOfDay;

    @Schema(description = "图片OSS完整URL")
    private String imageUrl;

    @Schema(description = "图片配文,多句用|分隔,前端随机展示一句")
    private String captions;

    @Schema(description = "图片标签(管理端分类标注,单标签,最长64字符)")
    private String tag;

    @Schema(description = "排序序号(同组多图排序)")
    private Integer sortOrder;

    public static ActionImageVO toVO(ActionImageEntity entity) {
        ActionImageVO vo = new ActionImageVO();
        vo.setId(entity.getId());
        vo.setActionId(entity.getActionId());
        vo.setPetPrototype(entity.getPetPrototype());
        vo.setTimeOfDay(entity.getTimeOfDay());
        vo.setImageUrl(entity.getImageUrl());
        vo.setCaptions(entity.getCaptions());
        vo.setTag(entity.getTag());
        vo.setSortOrder(entity.getSortOrder());
        return vo;
    }
}
