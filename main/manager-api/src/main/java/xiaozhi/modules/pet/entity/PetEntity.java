package xiaozhi.modules.pet.entity;

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
@TableName("ai_pet")
@Schema(description = "AI宠物")
public class PetEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "ID")
    private String id;

    @Schema(description = "归属用户ID")
    private Long userId;

    @Schema(description = "关联设备ID")
    private String deviceId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "出生日期时间")
    private Date birthDate;

    @Schema(description = "八字")
    private String bazi;

    @Schema(description = "五行")
    private String wuxing;

    @Schema(description = "星座英文编码")
    private String zodiac;

    @Schema(description = "MBTI人格")
    private String mbti;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.UPDATE)
    private Long updater;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private Date updateDate;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
