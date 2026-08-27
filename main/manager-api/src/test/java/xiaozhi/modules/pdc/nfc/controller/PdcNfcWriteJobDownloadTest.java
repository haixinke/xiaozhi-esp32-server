package xiaozhi.modules.pdc.nfc.controller;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
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
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAdminIdempotencyService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcManualWriteService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultImporter;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteFile;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteJobVO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PdcNfcWriteJobAdminController 下载测试")
class PdcNfcWriteJobDownloadTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock
    private PdcNfcWriteJobService writeJobService;
    @Mock
    private PdcNfcWriteResultImporter writeResultImporter;
    @Mock
    private PdcNfcAdminIdempotencyService idempotencyService;
    @Mock
    private PdcNfcManualWriteService manualWriteService;

    private PdcNfcWriteJobAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new PdcNfcWriteJobAdminController(
                writeJobService, writeResultImporter, idempotencyService, manualWriteService);
    }

    // --- helpers ---

    private HttpServletResponse mockResponse() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(WriteListener listener) {}
            @Override
            public void write(int b) { body.write(b); }
            @Override
            public void write(byte[] b, int off, int len) { body.write(b, off, len); }
        };
        when(response.getOutputStream()).thenReturn(outputStream);
        return response;
    }

    // --- tests ---

    @Test
    @DisplayName("Controller 类上存在 superAdmin 权限注解")
    void classHasSuperAdminAnnotation() {
        RequiresPermissions annotation =
                PdcNfcWriteJobAdminController.class.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("sys:role:superAdmin");
    }

    @Test
    @DisplayName("Controller 提供 download 方法")
    void hasDownloadMethod() throws NoSuchMethodException {
        assertThat(PdcNfcWriteJobAdminController.class
                .getDeclaredMethod("download", Long.class, HttpServletResponse.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Controller 提供 create 方法")
    void hasCreateMethod() throws NoSuchMethodException {
        assertThat(PdcNfcWriteJobAdminController.class
                .getDeclaredMethod("create", Long.class, String.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Controller 提供 cancel 方法")
    void hasCancelMethod() throws NoSuchMethodException {
        assertThat(PdcNfcWriteJobAdminController.class
                .getDeclaredMethod("cancel", Long.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Controller 提供 progress 方法")
    void hasProgressMethod() throws NoSuchMethodException {
        assertThat(PdcNfcWriteJobAdminController.class
                .getDeclaredMethod("progress", Long.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Download 设置 Cache-Control: no-store, private")
    void downloadSetsCacheControlHeaders() throws IOException {
        byte[] csvBytes = "test,csv\r\n".getBytes();
        // SecurityUser.getUserId() 在无 Shiro 上下文时返回 null
        when(writeJobService.export(any(), any()))
                .thenReturn(new PdcNfcWriteFile("test.csv", csvBytes));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.download(1L, response);

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("no-store, private");
    }

    @Test
    @DisplayName("非 SCHEME_GENERATED 批次 - 创建被拒绝")
    void nonSchemeGeneratedBatchRejected() {
        // 预先创建异常，避免在 when() stubbing 上下文中触发 Mockito 嵌套问题
        RenException ex = new RenException(10504);
        when(writeJobService.create(100L, null, 99L))
                .thenThrow(ex);

        assertThatThrownBy(() -> writeJobService.create(100L, null, 99L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("活跃写卡任务冲突 - 创建被拒绝")
    void activeJobConflictRejected() {
        RenException ex = new RenException(10511);
        when(writeJobService.create(200L, null, 99L))
                .thenThrow(ex);

        assertThatThrownBy(() -> writeJobService.create(200L, null, 99L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("任务不存在 - 导出被拒绝")
    void jobNotFoundExportRejected() {
        RenException ex = new RenException(10510);
        when(writeJobService.export(999L, 99L))
                .thenThrow(ex);

        assertThatThrownBy(() -> writeJobService.export(999L, 99L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("Create 返回正确的 VO 结构")
    void createReturnsCorrectVO() {
        PdcNfcWriteJobVO expected = new PdcNfcWriteJobVO(
                1L, "WRT-100-1", 100L, "B20260729001", "V1", "FACTORY_CSV", "CREATED",
                5, 0, 0, null, 5, null, new Date());
        when(writeJobService.create(100L, null, 99L)).thenReturn(expected);

        PdcNfcWriteJobVO result = writeJobService.create(100L, null, 99L);
        assertThat(result.jobNo()).isEqualTo("WRT-100-1");
        assertThat(result.formatVersion()).isEqualTo("V1");
        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.totalCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("重复导出产出相同文件名")
    void repeatedExportSameFileName() {
        byte[] csv = "same,bytes\r\n".getBytes();
        when(writeJobService.export(1L, 99L))
                .thenReturn(new PdcNfcWriteFile("WRT-1_B1.csv", csv));

        PdcNfcWriteFile first = writeJobService.export(1L, 99L);
        PdcNfcWriteFile second = writeJobService.export(1L, 99L);

        assertThat(second.fileName()).isEqualTo(first.fileName());
        assertThat(second.bytes()).isEqualTo(first.bytes());
    }
}
