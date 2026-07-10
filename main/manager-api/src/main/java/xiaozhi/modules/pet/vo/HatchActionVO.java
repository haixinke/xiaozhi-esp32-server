package xiaozhi.modules.pet.vo;

import java.time.LocalDate;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "孵化修炼动作记录")
public class HatchActionVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "宠物ID")
    private String petId;

    @Schema(description = "动作类型: NICKNAME/CUDDLE/WISH/LESSON/DOODLE")
    private String actionType;

    @Schema(description = "动作载荷JSON")
    private String payload;

    @Schema(description = "动作日期(Asia/Shanghai)")
    private LocalDate actionDate;

    @Schema(description = "本次加速分钟数")
    private Integer acceleratedMinutes;

    @Schema(description = "创建时间")
    private Date createDate;
}
