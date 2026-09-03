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
 * 宠物原型共享故事当前状态。每种原型一条记录，petPrototype 唯一。
 * 名称、图片 URL、配文为切换时刻的不可变快照，不依赖基础配置表。
 */
@Data
@TableName("ai_pet_story_state")
@Schema(description = "宠物原型共享故事当前状态")
public class PetStoryStateEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "ID")
    private String id;

    @Schema(description = "宠物原型(如:锦鲤、玉兔)")
    private String petPrototype;

    @Schema(description = "运行状态: UNINITIALIZED/ACTIVE")
    private String runtimeStatus;

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

    @Schema(description = "场景特殊标签图URL快照(选中动作中命中场景特殊标签的图片,取当前时段首张;规则见SpecialSceneTagRegistry)")
    private String tagImageUrl;

    @Schema(description = "场景特殊标签图配文快照（多条用|分隔，客户端拆分随机展示），可空")
    private String tagImageCaption;

    @Schema(description = "配文快照（多条用|分隔，客户端拆分随机展示），可空")
    private String caption;

    @Schema(description = "本次随机生成的整数持续小时数")
    private Integer durationHours;

    @Schema(description = "实际开始时间")
    private Date startedAt;

    @Schema(description = "最早允许下次切换的时间")
    private Date expectedEndAt;

    @Schema(description = "最近完成检查的整点时槽，多实例幂等字段")
    private Date lastEvaluatedHour;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateDate;
}
