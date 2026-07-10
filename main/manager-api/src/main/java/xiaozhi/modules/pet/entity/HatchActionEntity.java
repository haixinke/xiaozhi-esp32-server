package xiaozhi.modules.pet.entity;

import java.time.LocalDate;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_pet_hatch_action")
@Schema(description = "蛋宝宝孵化修炼动作明细")
public class HatchActionEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "宠物ID")
    private String petId;

    @Schema(description = "动作类型: NICKNAME/CUDDLE/WISH/LESSON/DOODLE")
    private String actionType;

    @Schema(description = "动作载荷JSON")
    private String payload;

    @Schema(description = "动作日期(Asia/Shanghai,幂等用)")
    private LocalDate actionDate;

    @Schema(description = "本次加速分钟数")
    private Integer acceleratedMinutes;

    @Schema(description = "创建者")
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
