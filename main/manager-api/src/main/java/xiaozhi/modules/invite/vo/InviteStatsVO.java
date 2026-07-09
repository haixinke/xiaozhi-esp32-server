package xiaozhi.modules.invite.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码统计")
public class InviteStatsVO {

    @Schema(description = "邀请码总数")
    private int totalCodes;
    @Schema(description = "总消耗次数")
    private int totalConsumed;
    @Schema(description = "个人码数")
    private int personalCount;
    @Schema(description = "企业码数")
    private int enterpriseCount;
}
