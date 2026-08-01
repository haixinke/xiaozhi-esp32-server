package xiaozhi.modules.agent.service;

/**
 * 聊天记录删除服务
 * <p>
 * 删除当前用户全部智能体的聊天记录（ai_agent_chat_history、ai_agent_chat_title）。
 */
public interface ChatHistoryDeleteService {

    /**
     * 删除指定用户的全部聊天记录（物理删除，不可恢复）
     *
     * @param userId 用户ID
     */
    void deleteAllByUserId(Long userId);
}
