package xiaozhi.modules.storyengine.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("ai_pet_story_history")
public class PetStoryHistoryEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String petPrototype;
    private String bigSceneId;
    private String bigSceneName;
    private String smallSceneId;
    private String smallSceneName;
    private String actionId;
    private String actionName;
    private String actionImageId;
    private String weightPeriod;
    private String imageTimeOfDay;
    private String imageUrl;
    private String caption;
    private Integer durationHours;
    private Date startedAt;
    private Date expectedEndAt;
    private Date archivedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
