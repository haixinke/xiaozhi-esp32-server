package xiaozhi.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "AI伴侣视图对象")
public class CompanionVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "归属用户ID")
    private Long userId;

    @Schema(description = "关联设备ID")
    private String deviceId;

    @Schema(description = "伴侣类型")
    private String type;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "默认图片URL")
    private String defaultImage;

    @Schema(description = "出生日期")
    private LocalDateTime birthday;

    @Schema(description = "星座")
    private String zodiac;

    @Schema(description = "属相")
    private String chineseZodiac;

    @Schema(description = "八字")
    private String bazi;

    @Schema(description = "五行")
    private String wuxing;

    @Schema(description = "角色")
    private String character;

    @Schema(description = "职业")
    private String occupation;

    @Schema(description = "音色")
    private String voice;

    @Schema(description = "性格描述")
    private String personality;

    @Schema(description = "职业病描述")
    private String quirksText;

    @Schema(description = "灵魂特质")
    private String soulTraits;

    @Schema(description = "小任性")
    private String soulQuirk;

    @Schema(description = "关系类型")
    private String relationType;

    @Schema(description = "亲密程度: 0.0~1.0")
    private Float intimacy;

    @Schema(description = "宠物类型")
    private String petType;

    @Schema(description = "宠物名")
    private String petName;

    @Schema(description = "今日心情")
    private String mood;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "修改时间")
    private LocalDateTime updatedAt;

    public static CompanionVO toVO(xiaozhi.modules.companion.entity.CompanionEntity entity) {
        CompanionVO vo = new CompanionVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setDeviceId(entity.getDeviceId());
        vo.setType(entity.getType());
        vo.setAvatar(entity.getAvatar());
        vo.setDefaultImage(entity.getDefaultImage());
        vo.setBirthday(entity.getBirthday());
        vo.setZodiac(entity.getZodiac());
        vo.setChineseZodiac(entity.getChineseZodiac());
        vo.setBazi(entity.getBazi());
        vo.setWuxing(entity.getWuxing());
        vo.setCharacter(entity.getCharacter());
        vo.setOccupation(entity.getOccupation());
        vo.setVoice(entity.getVoice());
        vo.setPersonality(entity.getPersonality());
        vo.setQuirksText(entity.getQuirksText());
        vo.setSoulTraits(entity.getSoulTraits());
        vo.setSoulQuirk(entity.getSoulQuirk());
        vo.setRelationType(entity.getRelationType());
        vo.setIntimacy(entity.getIntimacy());
        vo.setPetType(entity.getPetType());
        vo.setPetName(entity.getPetName());
        vo.setMood(entity.getMood());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
