package xiaozhi.modules.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import xiaozhi.common.oss.OssService;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatAudioDao;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.service.AgentChatTitleService;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentChatHistoryServiceImpl 测试")
class AgentChatHistoryServiceImplTest {

    @Mock
    private AiAgentChatHistoryDao baseMapper;

    @Mock
    private AgentChatTitleService agentChatTitleService;

    @Mock
    private OssService ossService;

    @Mock
    private AiAgentChatAudioDao aiAgentChatAudioDao;

    private AgentChatHistoryServiceImpl historyService;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() throws Exception {
        historyService = new AgentChatHistoryServiceImpl(agentChatTitleService, ossService, aiAgentChatAudioDao);
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(historyService, baseMapper);
    }

    @Test
    @DisplayName("deleteByAgentId - 删除音频时同步清理OSS和DB")
    void deleteByAgentId_deleteAudio_cleansOssAndDb() {
        String agentId = "agent-123";
        List<String> audioIds = Arrays.asList("audio-1", "audio-2");
        List<String> ossKeys = Arrays.asList("chat-audio/AA:BB:CC:DD:EE:FF/audio-1.wav", "chat-audio/AA:BB:CC:DD:EE:FF/audio-2.wav");

        when(baseMapper.getAudioIdsByAgentId(agentId)).thenReturn(audioIds);
        when(ossService.isEnabled()).thenReturn(true);
        when(aiAgentChatAudioDao.getOssKeysByAudioIds(audioIds)).thenReturn(ossKeys);

        historyService.deleteByAgentId(agentId, true, false);

        verify(aiAgentChatAudioDao).getOssKeysByAudioIds(audioIds);
        verify(ossService).deleteBatch(ossKeys);
        verify(baseMapper).deleteAudioByIds(audioIds);
        verify(baseMapper).deleteAudioIdByAgentId(agentId);
        verify(baseMapper, never()).deleteHistoryByAgentId(any());
    }

    @Test
    @DisplayName("deleteByAgentId - 无音频ID时不触发OSS和DB音频删除")
    void deleteByAgentId_noAudioIds_noOssOrDbDeletion() {
        String agentId = "agent-123";
        when(baseMapper.getAudioIdsByAgentId(agentId)).thenReturn(Collections.emptyList());

        historyService.deleteByAgentId(agentId, true, false);

        verify(aiAgentChatAudioDao, never()).getOssKeysByAudioIds(any());
        verify(ossService, never()).deleteBatch(any());
        verify(baseMapper, never()).deleteAudioByIds(any());
        verify(baseMapper).deleteAudioIdByAgentId(agentId);
    }

    @Test
    @DisplayName("deleteByAgentId - OSS未启用时不调用OSS删除")
    void deleteByAgentId_ossDisabled_onlyDeletesDb() {
        String agentId = "agent-123";
        List<String> audioIds = Arrays.asList("audio-1");

        when(baseMapper.getAudioIdsByAgentId(agentId)).thenReturn(audioIds);
        when(ossService.isEnabled()).thenReturn(false);

        historyService.deleteByAgentId(agentId, true, false);

        verify(aiAgentChatAudioDao, never()).getOssKeysByAudioIds(any());
        verify(ossService, never()).deleteBatch(any());
        verify(baseMapper).deleteAudioByIds(audioIds);
    }

    @Test
    @DisplayName("deleteByAgentId - OSS删除失败时仍然删除DB记录")
    void deleteByAgentId_ossDeleteFailed_stillDeletesDb() {
        String agentId = "agent-123";
        List<String> audioIds = Arrays.asList("audio-1");
        List<String> ossKeys = Arrays.asList("chat-audio/AA:BB:CC:DD:EE:FF/audio-1.wav");

        when(baseMapper.getAudioIdsByAgentId(agentId)).thenReturn(audioIds);
        when(ossService.isEnabled()).thenReturn(true);
        when(aiAgentChatAudioDao.getOssKeysByAudioIds(audioIds)).thenReturn(ossKeys);
        doThrow(new RuntimeException("oss delete failed")).when(ossService).deleteBatch(ossKeys);

        historyService.deleteByAgentId(agentId, true, false);

        verify(ossService).deleteBatch(ossKeys);
        verify(baseMapper).deleteAudioByIds(audioIds);
    }

    @Test
    @DisplayName("deleteByAgentId - 只删除文本时不触碰音频")
    void deleteByAgentId_deleteTextOnly_deletesHistoryOnly() {
        String agentId = "agent-123";

        historyService.deleteByAgentId(agentId, false, true);

        verify(baseMapper, never()).getAudioIdsByAgentId(any());
        verify(baseMapper).deleteHistoryByAgentId(agentId);
    }

    @Test
    @DisplayName("deleteByAgentId - 同时删除音频和文本")
    void deleteByAgentId_deleteBoth_deletesAudioAndHistory() {
        String agentId = "agent-123";
        List<String> audioIds = Arrays.asList("audio-1");

        when(baseMapper.getAudioIdsByAgentId(agentId)).thenReturn(audioIds);
        when(ossService.isEnabled()).thenReturn(false);

        historyService.deleteByAgentId(agentId, true, true);

        verify(baseMapper).deleteAudioByIds(audioIds);
        verify(baseMapper, never()).deleteAudioIdByAgentId(any());
        verify(baseMapper).deleteHistoryByAgentId(agentId);
    }
}
