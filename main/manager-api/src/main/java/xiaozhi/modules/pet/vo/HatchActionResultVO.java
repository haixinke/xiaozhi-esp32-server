package xiaozhi.modules.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "孵化修炼动作结果")
public class HatchActionResultVO {

    @Schema(description = "本次新增的加速分钟数(已做过则为0)")
    private Integer addedMinutes;

    @Schema(description = "是否今日已做过该动作(幂等命中)")
    private boolean alreadyDone;

    @Schema(description = "是否已达到破壳时间")
    private boolean readyToHatch;

    @Schema(description = "动作后的宠物最新视图")
    private PetVO pet;
}
