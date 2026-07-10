package xiaozhi.modules.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
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

    @Schema(description = "性格描述")
    private String personality;

    @Schema(description = "今日心情")
    private String todayMood;

    @Schema(description = "孵化状态: EGG-孵化中, HATCHED-已破壳")
    private String hatchStatus;

    @Schema(description = "孵化开始时间(完成首个修炼任务时刻,7天倒计时起点)")
    private Date hatchStartTime;

    @Schema(description = "预计破壳时间")
    private Date expectedHatchTime;

    @Schema(description = "实际破壳时间(分享卡片生日)")
    private Date hatchedAt;

    @Schema(description = "累计已加速孵化时长(分钟)")
    private Integer acceleratedMinutes;

    @Schema(description = "IP形象照片/头像URL")
    private String avatarUrl;

    @Schema(description = "IP形象原型(锦鲤/玉兔等)")
    private String prototype;

    @Schema(description = "性别: MALE/FEMALE/OTHER")
    private String gender;

    @Schema(description = "血型")
    private String bloodType;

    @Schema(description = "性格简介(20字以内,卡片展示用)")
    private String personalityBrief;

    @Schema(description = "今日心情对应日期(跨天重算判断)")
    private LocalDate todayMoodDate;

    @Schema(description = "今日心情一句话")
    private String todayMoodSentence;

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
