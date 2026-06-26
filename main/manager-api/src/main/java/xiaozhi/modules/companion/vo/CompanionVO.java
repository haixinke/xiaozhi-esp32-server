package xiaozhi.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.companion.entity.CompanionEntity;
import xiaozhi.modules.companion.util.MenstrualCycleUtil;
import xiaozhi.modules.companion.util.MenstrualPhase;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

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
    private Date birthday;

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

    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "修改时间")
    private Date updatedAt;

    @Schema(description = "经期状态")
    private MenstrualStatusVO menstrualStatus;

    public static CompanionVO toVO(CompanionEntity entity) {
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
        vo.setQuirksText(entity.getQuirksText());
        vo.setSoulTraits(entity.getSoulTraits());
        vo.setSoulQuirk(entity.getSoulQuirk());
        vo.setRelationType(entity.getRelationType());
        vo.setIntimacy(entity.getIntimacy());
        vo.setPetType(entity.getPetType());
        vo.setPetName(entity.getPetName());
        vo.setMood(entity.getMood());
        vo.setPastLifeSecret(entity.getPastLifeSecret());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setMenstrualStatus(buildMenstrualStatus(entity));
        return vo;
    }

    private static MenstrualStatusVO buildMenstrualStatus(CompanionEntity entity) {
        if (!"gf".equals(entity.getType())
                || entity.getMenstrualCycleStart() == null
                || entity.getMenstrualCycleLength() == null
                || entity.getMenstrualPeriodLength() == null) {
            return null;
        }

        LocalDate start = entity.getMenstrualCycleStart().toInstant()
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        MenstrualPhase phase = MenstrualCycleUtil.computePhase(
                start, entity.getMenstrualCycleLength(), entity.getMenstrualPeriodLength(), today);

        MenstrualStatusVO status = new MenstrualStatusVO();
        status.setPhase(phase.name());
        status.setPhaseLabel(phase.getLabel());
        status.setCycleDay(MenstrualCycleUtil.cycleDay(start, entity.getMenstrualCycleLength(), today));
        status.setDaysUntilNextPeriod(MenstrualCycleUtil.daysUntilNextPeriod(start, entity.getMenstrualCycleLength(), today));
        return status;
    }

    @Data
    @Schema(description = "经期状态")
    public static class MenstrualStatusVO {
        @Schema(description = "阶段编码")
        private String phase;

        @Schema(description = "阶段中文")
        private String phaseLabel;

        @Schema(description = "周期第几天")
        private Integer cycleDay;

        @Schema(description = "距离下次经期天数")
        private Integer daysUntilNextPeriod;
    }
}
