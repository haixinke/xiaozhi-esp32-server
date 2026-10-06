package xiaozhi.modules.pet.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 宠物明信片：AI 宠物主动寄给用户的明信片（照片 + 文字）。
 *
 * <p>对外公开分享走 share_id（随机串，不可枚举），好友免授权查看；
 * id 仅内部主键，不下发到公开接口。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_pet_postcard")
@Schema(description = "宠物明信片")
public class PetPostcardEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "明信片ID(内部主键，不下发)")
    private String id;

    @Schema(description = "对外分享随机串(不可枚举)")
    private String shareId;

    @Schema(description = "关联宠物ID")
    private String petId;

    @Schema(description = "收件用户ID")
    private Long userId;

    @Schema(description = "明信片照片URL(已过审)")
    private String imageUrl;

    @Schema(description = "明信片文字")
    private String caption;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.UPDATE)
    private Long updater;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private Date updateDate;
}
