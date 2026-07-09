package xiaozhi.modules.invite.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邀请码消耗结果")
public class InviteConsumeVO {

    @Schema(description = "邀请码ID")
    private Long codeId;
    @Schema(description = "剩余数量")
    private Integer remaining;
    @Schema(description = "邀请码状态")
    private Integer status;
    @Schema(description = "消息：success / 已使用过该邀请码")
    private String message;
}
