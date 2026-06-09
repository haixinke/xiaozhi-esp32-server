package xiaozhi.modules.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentChatHistoryListVO {
    @Schema(description = "聊天类型：1-用户，2-智能体")
    private Byte chatType;

    @Schema(description = "聊天内容")
    private String content;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "音频ID")
    private String audioId;
}
