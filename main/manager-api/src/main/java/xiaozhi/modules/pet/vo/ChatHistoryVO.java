package xiaozhi.modules.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "聊天历史视图对象")
public class ChatHistoryVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "消息类型: 1-用户, 2-智能体")
    private Byte chatType;

    @Schema(description = "聊天内容")
    private String content;

    @Schema(description = "音频ID")
    private String audioId;

    @Schema(description = "创建时间（ISO 8601格式）")
    private String createdAt;
}
