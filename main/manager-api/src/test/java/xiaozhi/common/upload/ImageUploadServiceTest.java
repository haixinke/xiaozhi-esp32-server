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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @DisplayName("小物件场景：允许 png/jpeg/webp，不允许 svg，上限 2MB，路径前缀 small-object")
    void uploadScene_smallObject_allowsRasterOnly() {
        UploadScene scene = UploadScene.fromCode("small-object");

        assertThat(scene).isEqualTo(UploadScene.SMALL_OBJECT);
        assertThat(scene.getPathPrefix()).isEqualTo("small-object");
        assertThat(scene.getAllowedTypes()).containsExactlyInAnyOrder("image/png", "image/jpeg", "image/webp");
        // 不收 svg：扩展名映射不支持，会产出缺扩展名的 OSS key
        assertThat(scene.getAllowedTypes()).doesNotContain("image/svg+xml");
        assertThat(scene.getMaxSize()).isEqualTo(2 * 1024 * 1024L);
    }

    @Test
    @DisplayName("小物件场景：正常上传 key 落在 small-object/{userId}/ 下")
    void uploadImage_smallObjectScene_usesDedicatedPath() throws Exception {
        when(file.getBytes()).thenReturn(new byte[] { 1, 2, 3 });

        String url = imageUploadService.uploadImage(42L, file, UploadScene.SMALL_OBJECT);

        assertThat(url).startsWith("https://oss.eggbabe.com/small-object/42/");
        assertThat(url).endsWith(".png");
    }

    @Test
    @DisplayName("小物件场景：svg 被拒绝")
    void uploadImage_smallObjectScene_rejectsSvg() {
        when(file.getContentType()).thenReturn("image/svg+xml");

        assertThatThrownBy(() -> imageUploadService.uploadImage(1L, file, UploadScene.SMALL_OBJECT))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.IMAGE_FILE_TYPE_ERROR));
    }

    @Test
    @DisplayName("AI 写真旧场景码上传仍写入 ai-gen 目录")
    void uploadImage_legacyAiGenScene_usesDedicatedPath() throws Exception {
        when(file.getBytes()).thenReturn(new byte[] { 1, 2, 3 });

        // 复用小程序实际传参，避免只验证枚举而遗漏上传入口的兼容性。
        UploadScene scene = UploadScene.fromCode("ai_gen");
        assertThat(scene).isEqualTo(UploadScene.AI_GEN);
        String url = imageUploadService.uploadImage(42L, file, scene);

        assertThat(url).startsWith("https://oss.eggbabe.com/ai-gen/42/");
        assertThat(url).endsWith(".png");
    }

    @ParameterizedTest
    @ValueSource(strings = { "ai_gen", "AI_GEN", "  Ai_Gen  ", "ai-gen", "AI-GEN" })
    @DisplayName("fromCode - AI 写真兼容枚举名与路径前缀")
    void fromCode_aiGenAliases_resolveSameScene(String code) {
        // 已发布小程序使用下划线，路径前缀使用连字符，两种协议均需保留。
        assertThat(UploadScene.fromCode(code)).isEqualTo(UploadScene.AI_GEN);
    }

    @Test
    @DisplayName("fromCode - 现有场景保留路径前缀并兼容枚举名")
    void fromCode_existingScenes_resolveBothFormats() {
        // 共享解析器不得因兼容写真旧参数而破坏管理后台的上传场景。
        assertThat(UploadScene.fromCode("game-icon")).isEqualTo(UploadScene.GAME_ICON);
        assertThat(UploadScene.fromCode("GAME_ICON")).isEqualTo(UploadScene.GAME_ICON);
        assertThat(UploadScene.fromCode("small-object")).isEqualTo(UploadScene.SMALL_OBJECT);
        assertThat(UploadScene.fromCode("SMALL_OBJECT")).isEqualTo(UploadScene.SMALL_OBJECT);
    }

    @ParameterizedTest
    @ValueSource(strings = { " ", "unknown", "ai_gen/42", "../ai-gen", "ai gen" })
    @DisplayName("fromCode - 场景别名兼容仍限制在白名单内")
    void fromCode_invalidScene_returnsNull(String code) {
        // 不接受任意路径或额外拼写，防止兼容逻辑放宽上传目录白名单。
        assertThat(UploadScene.fromCode(code)).isNull();
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
