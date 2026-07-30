package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcWriteResultImporterImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdcNfcWriteResultImporter 14 列解析与完整预检")
class PdcNfcWriteResultImporterTest {

    private static final String JOB_NO = "WRT-100-1";
    private static final String URI_SHA_1 =
            "2a1f0db626c8a9226f6937c0b1663fff098e38fba612cd32a351a28ad15e56a2";
    private static final String URI_SHA_2 =
            "dc9f23a3c2ffcdf05ac8983468b2d9110843f189b8e10d8dd7219f9b7333814c";

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        lenient().when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        lenient().when(messageSource.getMessage(
                        anyString(), any(), anyString(), any(java.util.Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PdcNfcWriteJobDao jobDao;
    @Mock private PdcNfcWriteJobItemDao jobItemDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcWriteResultTransactionService transactionService;

    private PdcNfcWriteResultImporterImpl importer;

    @BeforeEach
    void setUp() {
        importer = new PdcNfcWriteResultImporterImpl(
                jobDao, jobItemDao, assetDao, transactionService);
    }

    @Test
    @DisplayName("固定 14 列 PDC_NFC_RESULT_V1 被完整预检并传给事务服务")
    void acceptsCanonicalFourteenColumnResult() throws IOException {
        byte[] csvBytes = loadValidCsv();
        UUID requestId = UUID.randomUUID();
        stubExpectedSnapshot();
        PdcNfcWriteImportVO expected = new PdcNfcWriteImportVO(
                100L, JOB_NO, 2, 0, 0,
                PdcNfcWriteCsvExporter.sha256Hex(csvBytes), requestId);
        when(transactionService.apply(
                anyLong(), any(), anyString(), anyLong(), any(UUID.class)))
                .thenReturn(expected);

        PdcNfcWriteImportVO actual = importer.importResult(
                100L, requestId, multipart(csvBytes), 99L);

        assertThat(actual).isSameAs(expected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ValidatedWriteResult>> rowsCaptor =
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> shaCaptor = ArgumentCaptor.forClass(String.class);
        verify(transactionService).apply(
                org.mockito.ArgumentMatchers.eq(100L),
                rowsCaptor.capture(),
                shaCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq(requestId));
        assertThat(shaCaptor.getValue())
                .isEqualTo(PdcNfcWriteCsvExporter.sha256Hex(csvBytes));
        assertThat(rowsCaptor.getValue()).hasSize(2).allSatisfy(validated -> {
            assertThat(validated.fullyVerified()).isTrue();
            assertThat(validated.targetStatus()).isEqualTo(PdcNfcAssetStatus.VERIFIED);
        });
        assertThat(rowsCaptor.getValue().get(0).row().writtenAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 10, 20, 30));
    }

    @Test
    @DisplayName("旧 20 列 V1 在任何数据库读取前被拒绝")
    void rejectsLegacyTwentyColumnV1BeforeDatabaseLookup() {
        String legacyHeader =
                "format_version,job_no,batch_no,item_no,asset_no,wechat_sn,sku_code,"
                        + "prototype,uri_tnf,uri_type,uri_payload,aar_tnf,aar_type,aar_payload,"
                        + "write_success,verify_success,ndef_record_count,uri_sha256,aar_package,read_only";
        String legacyRow = "\"PDC_NFC_RESULT_V1\",\"WRT-1\",\"B1\",\"1\","
                + "\"A-001\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"weixin://wxpay/secret\",\"04\",\"android.com:pkg\","
                + "\"com.tencent.mm\",\"true\",\"true\",\"2\",\"sha\",\"com.tencent.mm\",\"true\"";

        assertThatThrownBy(() -> importer.importResult(
                1L, UUID.randomUUID(),
                multipart((legacyHeader + "\r\n" + legacyRow + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)),
                99L))
                .isInstanceOf(RenException.class);

        verifyNoInteractions(jobDao, jobItemDao, assetDao, transactionService);
    }

    @Test
    @DisplayName("重复结果行在数据库写入前被拒绝")
    void rejectsDuplicateRowsBeforeTransaction() {
        String row = validRow("A-001", "SN-001", URI_SHA_1);

        assertRejectedBeforeTransaction(csv(row, row));
    }

    @Test
    @DisplayName("缺失结果行在数据库写入前被拒绝")
    void rejectsMissingRowsBeforeTransaction() {
        stubExpectedSnapshot();

        assertRejectedBeforeTransaction(csv(
                validRow("A-001", "SN-001", URI_SHA_1)));
    }

    @Test
    @DisplayName("额外结果行在数据库写入前被拒绝")
    void rejectsExtraRowsBeforeTransaction() {
        stubExpectedSnapshot();

        assertRejectedBeforeTransaction(csv(
                validRow("A-001", "SN-001", URI_SHA_1),
                validRow("A-002", "SN-002", URI_SHA_2),
                validRow("A-003", "SN-003", "3".repeat(64))));
    }

    @Test
    @DisplayName("job_no 或 asset_no/wechat_sn 配对不匹配时拒绝整文件")
    void rejectsCrossJobOrMismatchedAssetPairBeforeTransaction() {
        stubExpectedSnapshot();
        String wrongJob = validRow("A-001", "SN-001", URI_SHA_1)
                .replace(JOB_NO, "WRT-OTHER");
        assertRejectedBeforeTransaction(csv(
                wrongJob, validRow("A-002", "SN-002", URI_SHA_2)));

        org.mockito.Mockito.reset(transactionService);
        String mismatchedPair = validRow("A-001", "SN-002", URI_SHA_1);
        assertRejectedBeforeTransaction(csv(
                mismatchedPair, validRow("A-002", "SN-001", URI_SHA_2)));
    }

    static Stream<Arguments> integrityMismatches() {
        return Stream.of(
                Arguments.of("URI 摘要", 8, "f".repeat(64)),
                Arguments.of("AAR 包名", 9, "com.example.invalid"),
                Arguments.of("NDEF Record 数量", 7, "1"),
                Arguments.of("只读标志", 10, "false")
        );
    }

    @ParameterizedTest(name = "{0}不匹配时拒绝整文件")
    @MethodSource("integrityMismatches")
    void rejectsIntegrityMismatchBeforeTransaction(
            String description, int fieldIndex, String invalidValue) {
        stubOneItemSnapshot();
        String[] fields = validFields("A-001", "SN-001", URI_SHA_1);
        fields[fieldIndex] = invalidValue;

        assertRejectedBeforeTransaction(csv(toCsvRow(fields)));
    }

    @Test
    @DisplayName("非法格式、时间和 UTF-8 在数据库读取前被拒绝")
    void rejectsMalformedInputBeforeDatabaseLookup() {
        String[] invalidTime = validFields("A-001", "SN-001", URI_SHA_1);
        invalidTime[11] = "not-a-time";
        assertThatThrownBy(() -> importer.importResult(
                1L, UUID.randomUUID(), multipart(csv(toCsvRow(invalidTime))), 99L))
                .isInstanceOf(RenException.class);

        byte[] header = (PdcNfcWriteResultImporterImpl.EXPECTED_HEADER + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] invalidUtf8 = new byte[header.length + 2];
        System.arraycopy(header, 0, invalidUtf8, 0, header.length);
        invalidUtf8[header.length] = (byte) 0xC0;
        invalidUtf8[header.length + 1] = (byte) 0x80;
        assertThatThrownBy(() -> importer.importResult(
                1L, UUID.randomUUID(), multipart(invalidUtf8), 99L))
                .isInstanceOf(RenException.class);

        verifyNoInteractions(jobDao, jobItemDao, assetDao, transactionService);
    }

    @Test
    @DisplayName("空文件和超限文件被拒绝")
    void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> importer.importResult(
                1L, UUID.randomUUID(), multipart(new byte[0]), 99L))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> importer.importResult(
                1L, UUID.randomUUID(), multipart(new byte[11 * 1024 * 1024]), 99L))
                .isInstanceOf(RenException.class);
        verifyNoInteractions(jobDao, jobItemDao, assetDao, transactionService);
    }

    @Test
    @DisplayName("RFC 4180 双引号和尾随空字段解析正确")
    void parsesQuotedAndTrailingEmptyFields() {
        assertThat(PdcNfcWriteResultImporterImpl.parseCsvFields(
                "\"hello\",\"he\"\"llo\","))
                .containsExactly("hello", "he\"llo", "");
    }

    private void assertRejectedBeforeTransaction(byte[] csvBytes) {
        assertThatThrownBy(() -> importer.importResult(
                100L, UUID.randomUUID(), multipart(csvBytes), 99L))
                .isInstanceOf(RenException.class);
        verify(transactionService, never()).apply(
                anyLong(), any(), anyString(), anyLong(), any(UUID.class));
    }

    private void stubExpectedSnapshot() {
        PdcNfcWriteJobEntity job = job();
        PdcNfcWriteJobItemEntity item1 =
                item(1L, 1001L, 1,
                        "B20260729001-000001",
                        "EB00000000000000000000000001",
                        URI_SHA_1);
        PdcNfcWriteJobItemEntity item2 =
                item(2L, 1002L, 2,
                        "B20260729001-000002",
                        "EB00000000000000000000000002",
                        URI_SHA_2);
        when(jobDao.selectById(100L)).thenReturn(job);
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item1, item2));
        lenient().when(assetDao.selectBatchIds(any()))
                .thenReturn(List.of(
                        asset(1001L, "B20260729001-000001"),
                        asset(1002L, "B20260729001-000002")));
    }

    private void stubOneItemSnapshot() {
        PdcNfcWriteJobEntity job = job();
        job.setTotalCount(1);
        when(jobDao.selectById(100L)).thenReturn(job);
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item(
                        1L, 1001L, 1, "A-001", "SN-001", URI_SHA_1)));
        lenient().when(assetDao.selectBatchIds(any()))
                .thenReturn(List.of(asset(1001L, "A-001")));
    }

    private PdcNfcWriteJobEntity job() {
        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setId(100L);
        job.setJobNo(JOB_NO);
        job.setBatchId(1L);
        job.setStatus(PdcNfcWriteJobStatus.EXPORTED.name());
        job.setTotalCount(2);
        return job;
    }

    private PdcNfcWriteJobItemEntity item(
            Long id, Long assetId, int sequence,
            String assetNo, String wechatSn, String uriSha256) {
        PdcNfcWriteJobItemEntity item = new PdcNfcWriteJobItemEntity();
        item.setId(id);
        item.setJobId(100L);
        item.setAssetId(assetId);
        item.setSequenceNo(sequence);
        item.setAssetNo(assetNo);
        item.setWechatSn(wechatSn);
        item.setUriSha256(uriSha256);
        return item;
    }

    private PdcNfcAssetEntity asset(Long id, String assetNo) {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(id);
        asset.setAssetNo(assetNo);
        asset.setStatus(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        asset.setVersion(1);
        asset.setActiveWriteJobId(100L);
        asset.setCreateDate(new Date());
        return asset;
    }

    private byte[] loadValidCsv() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
                "/pdc/nfc/PDC_NFC_RESULT_V1.valid.csv")) {
            assertThat(stream).isNotNull();
            return stream.readAllBytes();
        }
    }

    private byte[] csv(String... rows) {
        return (PdcNfcWriteResultImporterImpl.EXPECTED_HEADER + "\r\n"
                + String.join("\r\n", rows) + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String validRow(String assetNo, String wechatSn, String uriSha256) {
        return toCsvRow(validFields(assetNo, wechatSn, uriSha256));
    }

    private String[] validFields(String assetNo, String wechatSn, String uriSha256) {
        return new String[]{
                "PDC_NFC_RESULT_V1", JOB_NO, assetNo, wechatSn,
                "SUCCESS", "SUCCESS", "04AABBCC", "2",
                uriSha256, "com.tencent.mm", "true",
                "2026-07-29T10:20:30", "", ""
        };
    }

    private String toCsvRow(String[] fields) {
        return Stream.of(fields)
                .map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private MockMultipartFile multipart(byte[] bytes) {
        return new MockMultipartFile(
                "file", "result.csv", "text/csv", bytes);
    }
}
