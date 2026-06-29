package xiaozhi.modules.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "聊天配额检查结果")
public class ChatQuotaResultVO {

    @Schema(description = "是否允许本次对话")
    private Boolean allowed;

    @Schema(description = "剩余次数（-1表示无限）")
    private Integer remaining;

    @Schema(description = "每日总配额")
    private Integer total;

    @Schema(description = "提示语（超限时返回）")
    private String message;
}
