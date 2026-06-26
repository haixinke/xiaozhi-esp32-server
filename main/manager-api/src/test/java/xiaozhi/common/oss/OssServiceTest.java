package xiaozhi.common.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.OSSObject;

import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.common.utils.SpringContextUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OssService 测试")
class OssServiceTest {

    @Mock
    private OSS ossClient;

    @Mock
    private AliyunOssProperties ossProperties;

    private OssService ossService;

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
    void setUp() {
        when(ossProperties.getBucketName()).thenReturn("test-bucket");
        ossService = new OssService(ossClient, ossProperties);
    }

    @Test
    @DisplayName("isEnabled - 客户端和配置都有效时返回true")
    void isEnabled_clientAndConfigReady_returnsTrue() {
        when(ossProperties.isConfigured()).thenReturn(true);
        assertThat(ossService.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEnabled - 客户端为null时返回false")
    void isEnabled_nullClient_returnsFalse() {
        OssService service = new OssService(null, ossProperties);
        when(ossProperties.isConfigured()).thenReturn(true);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled - 配置不完整时返回false")
    void isEnabled_configIncomplete_returnsFalse() {
        when(ossProperties.isConfigured()).thenReturn(false);
        assertThat(ossService.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("upload - 上传字节数组成功返回ossKey")
    void upload_validData_returnsOssKey() {
        String ossKey = "chat-audio/test.wav";
        byte[] data = new byte[] { 1, 2, 3 };

        String result = ossService.upload(ossKey, data);

        assertThat(result).isEqualTo(ossKey);
        verify(ossClient).putObject(eq("test-bucket"), eq(ossKey), any(ByteArrayInputStream.class));
    }

    @Test
    @DisplayName("upload - ossKey为空时抛出异常")
    void upload_blankOssKey_throwsException() {
        assertThatThrownBy(() -> ossService.upload(" ", new byte[] { 1 }))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_DELETE_FILE_ERROR));
    }

    @Test
    @DisplayName("upload - 数据为null时抛出异常")
    void upload_nullData_throwsException() {
        assertThatThrownBy(() -> ossService.upload("key", null))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_DELETE_FILE_ERROR));
    }

    @Test
    @DisplayName("download - 下载成功返回字节数组")
    void download_validKey_returnsData() throws IOException {
        String ossKey = "chat-audio/test.wav";
        byte[] expected = new byte[] { 4, 5, 6 };
        OSSObject ossObject = mock(OSSObject.class);
        when(ossClient.getObject("test-bucket", ossKey)).thenReturn(ossObject);
        when(ossObject.getObjectContent()).thenReturn(new ByteArrayInputStream(expected));

        byte[] result = ossService.download(ossKey);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("download - ossKey为空时抛出异常")
    void download_blankOssKey_throwsException() {
        assertThatThrownBy(() -> ossService.download(" "))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_DOWNLOAD_FILE_ERROR));
    }

    @Test
    @DisplayName("download - IO异常时转换为业务异常")
    void download_ioException_throwsRenException() throws IOException {
        String ossKey = "chat-audio/test.wav";
        OSSObject ossObject = mock(OSSObject.class);
        when(ossClient.getObject("test-bucket", ossKey)).thenReturn(ossObject);
        InputStream failingStream = mock(InputStream.class);
        when(ossObject.getObjectContent()).thenReturn(failingStream);
        when(failingStream.readAllBytes()).thenThrow(new IOException("network error"));

        assertThatThrownBy(() -> ossService.download(ossKey))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_DOWNLOAD_FILE_ERROR));
    }

    @Test
    @DisplayName("delete - 删除单个对象")
    void delete_validKey_deletesObject() {
        String ossKey = "chat-audio/test.wav";
        ossService.delete(ossKey);
        verify(ossClient).deleteObject("test-bucket", ossKey);
    }

    @Test
    @DisplayName("delete - ossKey为空时抛出异常")
    void delete_blankOssKey_throwsException() {
        assertThatThrownBy(() -> ossService.delete(" "))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_DELETE_FILE_ERROR));
    }

    @Test
    @DisplayName("deleteBatch - 空列表不调用OSS")
    void deleteBatch_emptyList_noOp() {
        ossService.deleteBatch(Collections.emptyList());
        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("deleteBatch - null列表不调用OSS")
    void deleteBatch_nullList_noOp() {
        ossService.deleteBatch(null);
        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("deleteBatch - 按1000条分批删除")
    void deleteBatch_largeList_splitsBatches() {
        List<String> keys = Collections.nCopies(1500, "chat-audio/test.wav");

        ossService.deleteBatch(keys);

        verify(ossClient, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("buildAudioOssKey - 构造音频OSS键")
    void buildAudioOssKey_validId_returnsPath() {
        String result = OssService.buildAudioOssKey("audio-123", "AA:BB:CC:DD:EE:FF");
        assertThat(result).isEqualTo("chat-audio/AA:BB:CC:DD:EE:FF/audio-123.wav");
    }

    @Test
    @DisplayName("buildAudioOssKey - 空audioId抛出异常")
    void buildAudioOssKey_blankAudioId_throwsException() {
        assertThatThrownBy(() -> OssService.buildAudioOssKey("  ", "AA:BB:CC:DD:EE:FF"))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_DELETE_FILE_ERROR));
    }

    @Test
    @DisplayName("buildAudioOssKey - 空macAddress抛出异常")
    void buildAudioOssKey_blankMacAddress_throwsException() {
        assertThatThrownBy(() -> OssService.buildAudioOssKey("audio-123", "  "))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_DELETE_FILE_ERROR));
    }
}
