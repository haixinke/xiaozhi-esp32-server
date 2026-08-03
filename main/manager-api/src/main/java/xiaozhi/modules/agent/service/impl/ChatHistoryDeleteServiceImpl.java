package xiaozhi.modules.agent.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xiaozhi.modules.agent.dao.AgentChatTitleDao;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.ChatHistoryDeleteService;
import xiaozhi.modules.sys.enums.OperationType;
import xiaozhi.modules.sys.service.OperationLogService;

/**
 * 聊天记录删除服务实现
 * <p>
 * 逐智能体删除：先按会话删除标题（ai_agent_chat_title），再删除文本记录
 * （ai_agent_chat_history）。蛋宝宝音频不落 OSS，故不删音频。
 * 单个智能体删除失败不中断，记录日志后继续处理其余智能体。
 */
@Slf4j
@Service
@AllArgsConstructor
public class ChatHistoryDeleteServiceImpl implements ChatHistoryDeleteService {

    private final AgentService agentService;
    private final AgentChatHistoryService agentChatHistoryService;
    private final AiAgentChatHistoryDao chatHistoryDao;
    private final AgentChatTitleDao agentChatTitleDao;
    private final OperationLogService operationLogService;

    @Override
    public void deleteAllByUserId(Long userId) {
        List<AgentDTO> agents = agentService.getUserAgents(userId, null, null);
        if (agents == null || agents.isEmpty()) {
            log.info("用户无智能体，无需删除聊天记录，userId={}", userId);
            return;
        }

        int success = 0;
        int failed = 0;
        for (AgentDTO agent : agents) {
            try {
                deleteByAgent(agent.getId());
                success++;
            } catch (Exception e) {
                failed++;
                log.error("删除智能体聊天记录失败，userId={}, agentId={}", userId, agent.getId(), e);
            }
        }
        log.info("用户聊天记录删除完成，userId={}, 智能体总数={}, 成功={}, 失败={}",
                userId, agents.size(), success, failed);
        boolean allSuccess = failed == 0;
        operationLogService.record(OperationType.CHAT_HISTORY_DELETE, userId, allSuccess,
                "{\"agentTotal\":" + agents.size() + ",\"success\":" + success + ",\"failed\":" + failed + "}",
                allSuccess ? null : failed + "个智能体删除失败");
    }

    /**
     * 删除单个智能体的会话标题与聊天文本记录
     */
    private void deleteByAgent(String agentId) {
        deleteTitlesByAgent(agentId);
        // 仅删文本，不删音频（蛋宝宝音频不上传 OSS）
        agentChatHistoryService.deleteByAgentId(agentId, false, true);
    }

    /**
     * 按会话批量删除该智能体的标题记录
     */
    private void deleteTitlesByAgent(String agentId) {
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT session_id").eq("agent_id", agentId);
        List<String> sessionIds = chatHistoryDao.selectList(wrapper).stream()
                .map(AgentChatHistoryEntity::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (sessionIds.isEmpty()) {
            return;
        }
        QueryWrapper<xiaozhi.modules.agent.entity.AgentChatTitleEntity> titleWrapper = new QueryWrapper<>();
        titleWrapper.in("session_id", sessionIds);
        agentChatTitleDao.delete(titleWrapper);
    }
}
