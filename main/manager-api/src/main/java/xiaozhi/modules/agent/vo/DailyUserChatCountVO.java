package xiaozhi.modules.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 每日用户聊天计数视图
 * <p>
 * 蛋宝宝依赖提醒功能使用：统计当前登录用户当日发送的用户消息数（chat_type=1），
 * 并根据年龄区间判断是否触发未成年人保护分支（≤14 周岁当日超限后禁止继续聊天）。
 */
@Data
@Schema(description = "每日用户聊天计数视图")
public class DailyUserChatCountVO {

    @Schema(description = "当日用户发送消息数（chat_type=1，Asia/Shanghai 日界）")
    private long todayCount;

    @Schema(description = "是否为未成年人（年龄区间 = AGE_0_14）")
    private boolean minor;

    @Schema(description = "是否触发聊天限制：minor 且 todayCount 超过阈值时为 true，前端据此弹窗并退出聊天页")
    private boolean chatLimited;
}
