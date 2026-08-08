package xiaozhi.modules.storyengine.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_story_action")
@Schema(description = "故事引擎-蛋宝宝动作")
public class ActionEntity {

    @TableId(type = IdType.ASSIGN_UUID)
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
