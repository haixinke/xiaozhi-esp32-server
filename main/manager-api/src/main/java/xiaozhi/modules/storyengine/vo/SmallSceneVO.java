package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;

@Data
@Schema(description = "故事引擎-小场景视图对象")
public class SmallSceneVO {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "所属大场景ID")
    private String bigSceneId;

    @Schema(description = "小场景名称(如:卧室、北京-故宫、快餐厅)")
    private String name;

    @Schema(description = "深夜时段(00:00~05:59)权重百分比")
    private Integer weightNight;

    @Schema(description = "上午时段(06:00~11:59)权重百分比")
    private Integer weightMorning;

    @Schema(description = "下午时段(12:00~17:59)权重百分比")
    private Integer weightAfternoon;

    @Schema(description = "傍晚时段(18:00~23:59)权重百分比")
    private Integer weightEvening;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "状态: 1=启用 0=禁用")
    private Integer status;

    public static SmallSceneVO toVO(SmallSceneEntity entity) {
        SmallSceneVO vo = new SmallSceneVO();
        vo.setId(entity.getId());
        vo.setBigSceneId(entity.getBigSceneId());
        vo.setName(entity.getName());
        vo.setWeightNight(entity.getWeightNight());
        vo.setWeightMorning(entity.getWeightMorning());
        vo.setWeightAfternoon(entity.getWeightAfternoon());
        vo.setWeightEvening(entity.getWeightEvening());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
