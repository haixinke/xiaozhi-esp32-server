package xiaozhi.modules.companion.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_companion")
@Schema(description = "AI伴侣")
public class CompanionEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "归属用户ID")
    private Long userId;

    @Schema(description = "关联设备ID")
    private String deviceId;

    @Schema(description = "伴侣类型: gf=女友, bf=男友")
    private String type;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "默认图片URL")
    private String defaultImage;

    @Schema(description = "出生日期")
    private LocalDateTime birthday;

    @Schema(description = "星座英文编码")
    private String zodiac;

    @Schema(description = "属相英文编码")
    private String chineseZodiac;

    @Schema(description = "八字JSON")
    private String bazi;

    @Schema(description = "五行JSON")
    private String wuxing;

    @Schema(description = "角色编码")
    private String character;

    @Schema(description = "职业编码")
    private String occupation;

    @Schema(description = "音色编码")
    private String voice;

    @Schema(description = "职业病描述")
    private String quirksText;

    @Schema(description = "灵魂特质编码,逗号分隔")
    private String soulTraits;

    @Schema(description = "小任性编码")
    private String soulQuirk;

    @Schema(description = "关系类型编码: childhood/bickering/loveAtFirst")
    private String relationType;

    @Schema(description = "亲密程度: 0.0~1.0")
    private Float intimacy;

    @Schema(description = "宠物类型编码: cat/dog")
    private String petType;

    @Schema(description = "宠物名")
    private String petName;

    @Schema(description = "今日心情")
    private String mood;

    @Schema(description = "前世秘密")
    private String pastLifeSecret;

    @Schema(description = "创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "修改人ID")
    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;
}
