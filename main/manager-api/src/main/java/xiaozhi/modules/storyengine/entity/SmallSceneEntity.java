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
@TableName("ai_story_small_scene")
@Schema(description = "故事引擎-小场景")
public class SmallSceneEntity {

    @TableId(type = IdType.ASSIGN_UUID)
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
