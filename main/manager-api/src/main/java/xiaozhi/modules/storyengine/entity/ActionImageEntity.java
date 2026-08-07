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
@TableName("ai_story_action_image")
@Schema(description = "故事引擎-动作图片")
public class ActionImageEntity {

    @TableId(type = IdType.ASSIGN_UUID)
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

    @Schema(description = "排序序号(同组多图排序)")
    private Integer sortOrder;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
