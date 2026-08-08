package xiaozhi.modules.storyengine.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 宠物原型共享故事历史快照。只保存真正运行过的 ACTIVE 状态，只追加不修改。
 */
@Data
@TableName("ai_pet_story_history")
@Schema(description = "宠物原型共享故事历史快照")
public class PetStoryHistoryEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "历史ID")
    private String id;

    @Schema(description = "宠物原型(如:锦鲤、玉兔)")
    private String petPrototype;

    @Schema(description = "大场景ID")
    private String bigSceneId;

    @Schema(description = "大场景名称快照")
    private String bigSceneName;

    @Schema(description = "小场景ID")
    private String smallSceneId;

    @Schema(description = "小场景名称快照")
    private String smallSceneName;

    @Schema(description = "动作ID")
    private String actionId;

    @Schema(description = "动作名称快照")
    private String actionName;

    @Schema(description = "选中的动作图片ID")
    private String actionImageId;

    @Schema(description = "权重时段: NIGHT/MORNING/AFTERNOON/EVENING")
    private String weightPeriod;

    @Schema(description = "图片时段: 白天/落日/黑夜")
    private String imageTimeOfDay;

    @Schema(description = "动作图片URL快照")
    private String imageUrl;

    @Schema(description = "选中的单条配文快照，可空")
    private String caption;

    @Schema(description = "本次随机生成的整数持续小时数")
    private Integer durationHours;

    @Schema(description = "实际开始时间")
    private Date startedAt;

    @Schema(description = "预计结束时间")
    private Date expectedEndAt;

    @Schema(description = "被下一状态实际替换的时间")
    private Date archivedAt;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
