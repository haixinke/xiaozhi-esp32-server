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
@TableName("ai_story_big_scene")
@Schema(description = "故事引擎-大场景")
public class BigSceneEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "ID")
    private String id;

    @Schema(description = "大场景名称(如:在家、旅行、上学、打工)")
    private String name;

    @Schema(description = "排序序号,越小越靠前")
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
