package xiaozhi.modules.pdc.nfc.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.utils.SpringContextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PdcNfcWriteCsvExporter CSV 导出测试")
class PdcNfcWriteCsvExporterTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(java.util.Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    private PdcNfcWriteCsvExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new PdcNfcWriteCsvExporter();
    }

    // --- helpers ---

    private PdcNfcWriteCsvRow makeItem(int seq, String assetNo, String wechatSn,
                                       String skuCode, String prototype,
                                       String uriPayload) {
        return new PdcNfcWriteCsvRow(
                seq, assetNo, wechatSn, skuCode, prototype, uriPayload);
    }

    private List<PdcNfcWriteCsvRow> goldenItems() {
        return List.of(
                makeItem(1, "B20260729001-000001", "EB00000000000000000000000001",
                        "SKU-KOI", "锦鲤", "weixin://wxpay/test-scheme-1"),
                makeItem(2, "B20260729001-000002", "EB00000000000000000000000002",
                        "SKU-KOI", "玉兔", "weixin://wxpay/test-scheme-2")
        );
    }

    private byte[] loadGolden() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/pdc/nfc/PDC_NFC_WRITE_V1.golden.csv")) {
            assertThat(is).as("golden file must exist").isNotNull();
            return is.readAllBytes();
        }
    }

    // --- tests ---

    @Test
    @DisplayName("Golden file match: 导出字节与 golden CSV 完全一致")
    void goldenFileMatch() throws IOException {
        byte[] golden = loadGolden();
        byte[] generated = exporter.generate("WRT-100-1", "B20260729001", goldenItems());
        assertThat(generated).isEqualTo(golden);
    }

    @Test
    @DisplayName("规范格式: 固定 14 列和 NDEF 常量")
    void canonicalWriteFormat() {
        byte[] generated = exporter.generate("WRT-100-1", "B20260729001", goldenItems());
        String csv = new String(generated, StandardCharsets.UTF_8);

        assertThat(csv).contains("PDC_NFC_WRITE_V1");
        assertThat(csv).contains(",0x01,U,");
        assertThat(csv).contains(",0x04,android.com:pkg,com.tencent.mm");
        String header = csv.substring(csv.indexOf("format_version"), csv.indexOf("\r\n"));
        assertThat(header.split(",", -1)).hasSize(14);
    }

    @Test
    @DisplayName("UTF-8 BOM: 前 3 字节为 EF BB BF")
    void utf8Bom() {
        byte[] csv = exporter.generate("WRT-1", "B1", List.of(
                makeItem(1, "A-001", "SN1", "SKU", "锦鲤", "weixin://test")));

        assertThat(csv.length).isGreaterThanOrEqualTo(3);
        assertThat(csv[0] & 0xFF).isEqualTo(0xEF);
        assertThat(csv[1] & 0xFF).isEqualTo(0xBB);
        assertThat(csv[2] & 0xFF).isEqualTo(0xBF);
    }

    @Test
    @DisplayName("CRLF: 所有行以 \\r\\n 结尾，无孤立 \\n")
    void crlfLineEndings() {
        byte[] csv = exporter.generate("WRT-1", "B1", List.of(
                makeItem(1, "A-001", "SN1", "SKU", "锦鲤", "weixin://test")));
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text).contains("\r\n");
        assertThat(text).doesNotContain("\r\r");
        // 没有孤立的 \n（所有 \n 前面都是 \r）
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                assertThat(i).isGreaterThan(0);
                assertThat(text.charAt(i - 1)).isEqualTo('\r');
            }
        }
    }

    @Test
    @DisplayName("公式注入防护: =+@- 前缀被添加单引号")
    void formulaInjectionProtection() {
        // 测试 formulaGuard 静态方法
        assertThat(PdcNfcWriteCsvExporter.formulaGuard("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(PdcNfcWriteCsvExporter.formulaGuard("+cmd")).isEqualTo("'+cmd");
        assertThat(PdcNfcWriteCsvExporter.formulaGuard("-test")).isEqualTo("'-test");
        assertThat(PdcNfcWriteCsvExporter.formulaGuard("@import")).isEqualTo("'@import");
        // 安全值不变
        assertThat(PdcNfcWriteCsvExporter.formulaGuard("safe-value")).isEqualTo("safe-value");
        assertThat(PdcNfcWriteCsvExporter.formulaGuard("ABC123")).isEqualTo("ABC123");
        assertThat(PdcNfcWriteCsvExporter.formulaGuard("")).isEqualTo("");
        assertThat(PdcNfcWriteCsvExporter.formulaGuard(null)).isEqualTo("");
    }

    @Test
    @DisplayName("RFC 4180 双引号转义: 包含双引号的值正确转义")
    void rfc4180Escaping() {
        assertThat(PdcNfcWriteCsvExporter.quote("hello")).isEqualTo("\"hello\"");
        assertThat(PdcNfcWriteCsvExporter.quote("he\"llo")).isEqualTo("\"he\"\"llo\"");
        assertThat(PdcNfcWriteCsvExporter.quote("")).isEqualTo("\"\"");
        assertThat(PdcNfcWriteCsvExporter.quote(null)).isEqualTo("\"\"");
        // 包含逗号的值
        assertThat(PdcNfcWriteCsvExporter.quote("a,b")).isEqualTo("\"a,b\"");
    }

    @Test
    @DisplayName("重复导出相同字节: 两次 generate 产出完全相同的 byte[]")
    void repeatedExportSameBytes() {
        List<PdcNfcWriteCsvRow> items = goldenItems();

        byte[] first = exporter.generate("WRT-100-1", "B20260729001", items);
        byte[] second = exporter.generate("WRT-100-1", "B20260729001", items);

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("SHA-256 摘要稳定: 相同输入产出相同摘要")
    void sha256Stable() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        String hash1 = PdcNfcWriteCsvExporter.sha256Hex(data);
        String hash2 = PdcNfcWriteCsvExporter.sha256Hex(data);
        assertThat(hash2).isEqualTo(hash1);
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("空行列表: 仅输出 BOM + header + CRLF")
    void emptyItems() {
        byte[] csv = exporter.generate("WRT-E", "B-E", List.of());
        String text = new String(csv, StandardCharsets.UTF_8);
        // 去掉 BOM 后应该只有 header 行（BOM 在 UTF-8 String 中是单个字符 \uFEFF）
        String withoutBom = text.replace("\uFEFF", "");
        String[] lines = withoutBom.split("\r\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).contains("format_version");
    }
}
