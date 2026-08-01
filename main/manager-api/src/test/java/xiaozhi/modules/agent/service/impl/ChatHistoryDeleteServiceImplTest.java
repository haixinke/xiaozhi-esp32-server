package xiaozhi.modules.agent.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.modules.agent.dao.AgentChatTitleDao;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.agent.service.AgentService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatHistoryDeleteServiceImpl 测试")
class ChatHistoryDeleteServiceImplTest {

    @Mock
    private AgentService agentService;
    @Mock
    private AgentChatHistoryService agentChatHistoryService;
    @Mock
    private AiAgentChatHistoryDao chatHistoryDao;
    @Mock
    private AgentChatTitleDao agentChatTitleDao;

    @InjectMocks
    private ChatHistoryDeleteServiceImpl service;

    private AgentDTO agent(String id) {
        AgentDTO dto = new AgentDTO();
        dto.setId(id);
        return dto;
    }

    private AgentChatHistoryEntity historyOfSession(String sessionId) {
        AgentChatHistoryEntity entity = new AgentChatHistoryEntity();
        entity.setSessionId(sessionId);
        return entity;
    }

    @Test
    @DisplayName("用户无智能体时不执行任何删除")
    void deleteAllByUserId_noAgents_noDeletion() {
        when(agentService.getUserAgents(1L, null, null)).thenReturn(List.of());

        service.deleteAllByUserId(1L);

        verify(agentChatHistoryService, never()).deleteByAgentId(any(), any(), any());
        verify(agentChatTitleDao, never()).delete(any());
    }

    @Test
    @DisplayName("逐智能体删除文本记录(不删音频)并按会话删除标题")
    void deleteAllByUserId_deletesTextAndTitlesPerAgent() {
        when(agentService.getUserAgents(1L, null, null))
                .thenReturn(List.of(agent("a1"), agent("a2")));
        when(chatHistoryDao.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(historyOfSession("s1"), historyOfSession("s2")))
                .thenReturn(List.of(historyOfSession("s3")));

        service.deleteAllByUserId(1L);

        verify(agentChatHistoryService).deleteByAgentId(eq("a1"), eq(false), eq(true));
        verify(agentChatHistoryService).deleteByAgentId(eq("a2"), eq(false), eq(true));
        // 两个智能体各删一次标题（session_id in 批量）
        verify(agentChatTitleDao, times(2)).delete(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("智能体无历史记录时跳过标题删除")
    void deleteAllByUserId_noHistory_skipsTitleDeletion() {
        when(agentService.getUserAgents(1L, null, null)).thenReturn(List.of(agent("a1")));
        when(chatHistoryDao.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        service.deleteAllByUserId(1L);

        verify(agentChatHistoryService).deleteByAgentId(eq("a1"), eq(false), eq(true));
        verify(agentChatTitleDao, never()).delete(any());
    }

    @Test
    @DisplayName("单个智能体删除失败不影响其余智能体")
    void deleteAllByUserId_singleAgentFailure_continues() {
        when(agentService.getUserAgents(1L, null, null))
                .thenReturn(List.of(agent("a1"), agent("a2")));
        when(chatHistoryDao.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        doThrow(new RuntimeException("db error"))
                .when(agentChatHistoryService).deleteByAgentId(eq("a1"), eq(false), eq(true));

        service.deleteAllByUserId(1L);

        verify(agentChatHistoryService).deleteByAgentId(eq("a2"), eq(false), eq(true));
    }
}
