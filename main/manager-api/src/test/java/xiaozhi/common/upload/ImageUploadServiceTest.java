package xiaozhi.common.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.web.multipart.MultipartFile;

import com.aliyun.oss.model.CannedAccessControlList;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.utils.SpringContextUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImageUploadService 测试")
class ImageUploadServiceTest {

    @Mock
    private OssService ossService;

    @Mock
    private MultipartFile file;

    private ImageUploadService imageUploadService;

    @BeforeAll
    static void initMessageSource() {
        // RenException 构造时会经 MessageUtils 反查错误码文案，需注入消息源
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        imageUploadService = new ImageUploadService(ossService);
        // 默认返回一个合法的 png 文件，单个用例可按需覆盖
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1L);
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.buildPublicUrl(any(String.class))).thenAnswer(inv -> "https://oss.eggbabe.com/" + inv.getArgument(0));
    }

    @Test
    @DisplayName("userId 为空时抛 USER_NOT_LOGIN")
    void uploadImage_nullUser_throwsNotLogin() {
        assertThatThrownBy(() -> imageUploadService.uploadImage(null, file, UploadScene.DOODLE))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.USER_NOT_LOGIN));
    }

    @Test
    @DisplayName("文件为空时抛 UPLOAD_FILE_EMPTY")
    void uploadImage_emptyFile_throwsEmpty() {
        when(file.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> imageUploadService.uploadImage(1L, file, UploadScene.DOODLE))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.UPLOAD_FILE_EMPTY));
    }

    @Test
    @DisplayName("content-type 不在白名单时抛 IMAGE_FILE_TYPE_ERROR")
    void uploadImage_invalidType_throwsType() {
        when(file.getContentType()).thenReturn("image/gif");
        assertThatThrownBy(() -> imageUploadService.uploadImage(1L, file, UploadScene.DOODLE))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.IMAGE_FILE_TYPE_ERROR));
    }

    @Test
    @DisplayName("content-type 为 null 时抛 IMAGE_FILE_TYPE_ERROR")
    void uploadImage_nullType_throwsType() {
        when(file.getContentType()).thenReturn(null);
        assertThatThrownBy(() -> imageUploadService.uploadImage(1L, file, UploadScene.DOODLE))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.IMAGE_FILE_TYPE_ERROR));
    }

    @Test
    @DisplayName("文件超限时抛 FILE_SIZE_OVER_LIMIT")
    void uploadImage_oversize_throwsSize() {
        when(file.getSize()).thenReturn(UploadScene.DOODLE.getMaxSize() + 1);
        assertThatThrownBy(() -> imageUploadService.uploadImage(1L, file, UploadScene.DOODLE))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.FILE_SIZE_OVER_LIMIT));
    }

    @Test
    @DisplayName("OSS 未启用时抛 OSS_UPLOAD_FILE_ERROR")
    void uploadImage_ossDisabled_throwsOss() {
        when(ossService.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> imageUploadService.uploadImage(1L, file, UploadScene.DOODLE))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_UPLOAD_FILE_ERROR));
    }

    @Test
    @DisplayName("正常上传返回公网URL并按场景路径前缀生成key")
    void uploadImage_validFile_returnsPublicUrl() throws Exception {
        byte[] data = new byte[] { 1, 2, 3 };
        when(file.getBytes()).thenReturn(data);

        String url = imageUploadService.uploadImage(42L, file, UploadScene.DOODLE);

        // key 形如 doodle/{userId}/{uuid}.png
        assertThat(url).startsWith("https://oss.eggbabe.com/doodle/42/");
        assertThat(url).endsWith(".png");
        verify(ossService).upload(
                eq(url.substring("https://oss.eggbabe.com/".length())),
                eq(data),
                eq(CannedAccessControlList.PublicRead));
    }

    @Test
    @DisplayName("getBytes 抛 IOException 时转为 OSS_UPLOAD_FILE_ERROR")
    void uploadImage_ioException_throwsOss() throws Exception {
        when(file.getBytes()).thenThrow(new java.io.IOException("disk gone"));

        assertThatThrownBy(() -> imageUploadService.uploadImage(1L, file, UploadScene.DOODLE))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.OSS_UPLOAD_FILE_ERROR));
    }

    @Test
    @DisplayName("fromCode - 大小写不敏感解析场景码")
    void fromCode_caseInsensitive_resolvesEnum() {
        assertThat(UploadScene.fromCode("doodle")).isEqualTo(UploadScene.DOODLE);
        assertThat(UploadScene.fromCode("DOODLE")).isEqualTo(UploadScene.DOODLE);
        assertThat(UploadScene.fromCode("  Doodle ")).isEqualTo(UploadScene.DOODLE);
        assertThat(UploadScene.fromCode(null)).isNull();
        assertThat(UploadScene.fromCode("")).isNull();
        assertThat(UploadScene.fromCode("avatar")).isNull();
    }
}
