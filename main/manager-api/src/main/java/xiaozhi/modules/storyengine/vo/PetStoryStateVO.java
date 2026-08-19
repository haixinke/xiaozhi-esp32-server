package xiaozhi.modules.storyengine.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "宠物原型共享故事当前状态")
public class PetStoryStateVO {

    @Schema(description = "宠物原型")
    private String petPrototype;

    @Schema(description = "大场景ID")
    private String bigSceneId;

    @Schema(description = "大场景名称")
    private String bigSceneName;

    @Schema(description = "小场景ID")
    private String smallSceneId;

    @Schema(description = "小场景名称")
    private String smallSceneName;

    @Schema(description = "动作ID")
    private String actionId;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "动作图片ID")
    private String actionImageId;

    @Schema(description = "权重时段")
    private String weightPeriod;

    @Schema(description = "图片时段")
    private String imageTimeOfDay;

    @Schema(description = "图片URL")
    private String imageUrl;

    @Schema(description = "窗户标签图URL")
    private String tagImageUrl;

    @Schema(description = "配文快照（多条用|分隔，客户端拆分随机展示）")
    private String caption;

    @Schema(description = "持续时长（小时）")
    private Integer durationHours;

    @Schema(description = "开始时间")
    private Date startedAt;

    @Schema(description = "预计结束时间")
    private Date expectedEndAt;
}
