package xiaozhi.modules.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import com.baomidou.mybatisplus.spring.repository.CrudRepository;

import com.aliyun.oss.ClientException;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatAudioDao;
import xiaozhi.modules.agent.entity.AgentChatAudioEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentChatAudioServiceImpl 测试")
class AgentChatAudioServiceImplTest {

    private static final String MAC_ADDRESS = "AA:BB:CC:DD:EE:FF";
    private static final String OSS_KEY = "chat-audio/" + MAC_ADDRESS + "/audio-123.wav";

    @Mock
    private AiAgentChatAudioDao baseMapper;

    @Mock
    private OssService ossService;

    private AgentChatAudioServiceImpl audioService;

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
        audioService = new AgentChatAudioServiceImpl(ossService);
        Field baseMapperField = CrudRepository.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(audioService, baseMapper);

        doAnswer(invocation -> {
            AgentChatAudioEntity entity = invocation.getArgument(0);
            entity.setId("audio-123");
            return 1;
        }).when(baseMapper).insert(any(AgentChatAudioEntity.class));
        when(baseMapper.updateById(any(AgentChatAudioEntity.class))).thenReturn(1);
    }

    @Test
    @DisplayName("saveAudio - OSS启用时先写DB、上传OSS、更新oss_key")
    void saveAudio_ossEnabled_uploadsToOss() {
        byte[] audioData = new byte[] { 1, 2, 3 };
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.upload(any(), eq(audioData))).thenReturn(OSS_KEY);

        String audioId = audioService.saveAudio(audioData, MAC_ADDRESS);

        assertThat(audioId).isEqualTo("audio-123");
        verify(baseMapper).insert(any(AgentChatAudioEntity.class));
        verify(ossService).upload(OSS_KEY, audioData);
        verify(baseMapper).updateById(any(AgentChatAudioEntity.class));
    }

    @Test
    @DisplayName("saveAudio - OSS上传失败时回退到BLOB")
    void saveAudio_ossUploadFailed_fallsBackToDb() {
        byte[] audioData = new byte[] { 1, 2, 3 };
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.upload(any(), eq(audioData))).thenThrow(new ClientException("upload failed"));

        String audioId = audioService.saveAudio(audioData, MAC_ADDRESS);

        assertThat(audioId).isEqualTo("audio-123");
        verify(baseMapper).updateById(any(AgentChatAudioEntity.class));
    }

    @Test
    @DisplayName("saveAudio - OSS未启用时直接存BLOB")
    void saveAudio_ossDisabled_storesInDb() {
        byte[] audioData = new byte[] { 1, 2, 3 };
        when(ossService.isEnabled()).thenReturn(false);

        String audioId = audioService.saveAudio(audioData, MAC_ADDRESS);

        assertThat(audioId).isEqualTo("audio-123");
        verify(ossService, never()).upload(any(), any());
        verify(baseMapper, never()).updateById(any(AgentChatAudioEntity.class));
    }

    @Test
    @DisplayName("saveAudio - null数据抛出异常")
    void saveAudio_nullData_throwsException() {
        assertThatThrownBy(() -> audioService.saveAudio(null, MAC_ADDRESS))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.VOICEPRINT_AUDIO_EMPTY));
    }

    @Test
    @DisplayName("saveAudio - 空数组抛出异常")
    void saveAudio_emptyData_throwsException() {
        assertThatThrownBy(() -> audioService.saveAudio(new byte[0], MAC_ADDRESS))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.VOICEPRINT_AUDIO_EMPTY));
    }

    @Test
    @DisplayName("saveAudio - 空macAddress抛出异常")
    void saveAudio_blankMacAddress_throwsException() {
        assertThatThrownBy(() -> audioService.saveAudio(new byte[] { 1, 2, 3 }, "  "))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.VOICEPRINT_AUDIO_EMPTY));
    }

    @Test
    @DisplayName("getAudio - 实体不存在返回null")
    void getAudio_nonExistentId_returnsNull() {
        when(baseMapper.selectById("missing")).thenReturn(null);

        byte[] result = audioService.getAudio("missing");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getAudio - 有oss_key且OSS启用时从OSS下载")
    void getAudio_ossKeyEnabled_downloadsFromOss() {
        byte[] ossData = new byte[] { 4, 5, 6 };
        AgentChatAudioEntity entity = createEntity("audio-123", OSS_KEY, null);
        when(baseMapper.selectById("audio-123")).thenReturn(entity);
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.download(OSS_KEY)).thenReturn(ossData);

        byte[] result = audioService.getAudio("audio-123");

        assertThat(result).isEqualTo(ossData);
    }

    @Test
    @DisplayName("getAudio - 无oss_key时回退到BLOB")
    void getAudio_noOssKey_returnsBlob() {
        byte[] blobData = new byte[] { 7, 8, 9 };
        AgentChatAudioEntity entity = createEntity("audio-123", null, blobData);
        when(baseMapper.selectById("audio-123")).thenReturn(entity);

        byte[] result = audioService.getAudio("audio-123");

        assertThat(result).isEqualTo(blobData);
        verify(ossService, never()).download(any());
    }

    @Test
    @DisplayName("getAudio - OSS下载失败时回退到BLOB")
    void getAudio_ossDownloadFailed_fallsBackToBlob() {
        byte[] blobData = new byte[] { 7, 8, 9 };
        AgentChatAudioEntity entity = createEntity("audio-123", OSS_KEY, blobData);
        when(baseMapper.selectById("audio-123")).thenReturn(entity);
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.download(OSS_KEY)).thenThrow(new ClientException("download failed"));

        byte[] result = audioService.getAudio("audio-123");

        assertThat(result).isEqualTo(blobData);
    }

    @Test
    @DisplayName("getAudio - OSS禁用时返回BLOB")
    void getAudio_ossDisabled_returnsBlob() {
        byte[] blobData = new byte[] { 7, 8, 9 };
        AgentChatAudioEntity entity = createEntity("audio-123", OSS_KEY, blobData);
        when(baseMapper.selectById("audio-123")).thenReturn(entity);
        when(ossService.isEnabled()).thenReturn(false);

        byte[] result = audioService.getAudio("audio-123");

        assertThat(result).isEqualTo(blobData);
        verify(ossService, never()).download(any());
    }

    private AgentChatAudioEntity createEntity(String id, String ossKey, byte[] audio) {
        AgentChatAudioEntity entity = new AgentChatAudioEntity();
        entity.setId(id);
        entity.setOssKey(ossKey);
        entity.setAudio(audio);
        return entity;
    }
}
