package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteRecordDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteRecordEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcWriteResultImporterImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdcNfcWriteResultImporter 导入器测试")
class PdcNfcWriteResultImporterTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        lenient().when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(java.util.Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PdcNfcWriteJobDao jobDao;
    @Mock private PdcNfcWriteJobItemDao jobItemDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcBatchDao batchDao;
    @Mock private PdcNfcWriteRecordDao writeRecordDao;
    @Mock private PdcNfcOperationLogDao operationLogDao;

    private PdcNfcWriteJobStateMachine writeJobStateMachine;
    private PdcNfcAssetStateMachine assetStateMachine;
    private PdcNfcWriteResultImporterImpl importer;

    @BeforeEach
    void setUp() {
        writeJobStateMachine = new PdcNfcWriteJobStateMachine();
        assetStateMachine = new PdcNfcAssetStateMachine();
        importer = new PdcNfcWriteResultImporterImpl(
                jobDao, jobItemDao, assetDao, batchDao,
                writeRecordDao, operationLogDao,
                writeJobStateMachine, assetStateMachine);
    }

    // --- 测试数据构建 ---

    private PdcNfcWriteJobEntity makeJob(Long id, String jobNo, String status) {
        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setId(id);
        job.setJobNo(jobNo);
        job.setStatus(status);
        job.setBatchId(1L);
        job.setFormatVersion("V1");
        job.setTotalCount(2);
        job.setCreateDate(new Date());
        return job;
    }

    private PdcNfcWriteJobItemEntity makeItem(Long id, Long jobId, Long assetId,
                                               int seq, String assetNo, String wechatSn,
                                               String uriSha256) {
        PdcNfcWriteJobItemEntity item = new PdcNfcWriteJobItemEntity();
        item.setId(id);
        item.setJobId(jobId);
        item.setAssetId(assetId);
        item.setSequenceNo(seq);
        item.setAssetNo(assetNo);
        item.setWechatSn(wechatSn);
        item.setSkuCode("SKU-KOI");
        item.setPrototype("锦鲤");
        item.setUriSha256(uriSha256);
        item.setCreateDate(new Date());
        return item;
    }

    private PdcNfcAssetEntity makeAsset(Long id, String assetNo, String status) {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(id);
        asset.setAssetNo(assetNo);
        asset.setStatus(status);
        asset.setVersion(1);
        asset.setActiveWriteJobId(100L);
        asset.setCreateDate(new Date());
        return asset;
    }

    private byte[] loadValidCsv() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/pdc/nfc/PDC_NFC_RESULT_V1.valid.csv")) {
            assertThat(is).as("valid CSV must exist").isNotNull();
            return is.readAllBytes();
        }
    }

    private String sha256(String value) {
        return PdcNfcWriteCsvExporter.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    // --- CSV 解析和校验测试 ---

    @Test
    @DisplayName("空文件 → CSV_FORMAT_ERROR")
    void emptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "result.csv",
                "text/csv", new byte[0]);
        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 1L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("超大文件 → CSV_FORMAT_ERROR")
    void oversizedFile() {
        byte[] bigData = new byte[11 * 1024 * 1024]; // 11 MiB
        MockMultipartFile file = new MockMultipartFile("file", "result.csv",
                "text/csv", bigData);
        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 1L))
                .isInstanceOf(RenException.class);
    }

    static Stream<MockMultipartFile> invalidFiles() {
        return Stream.of(
                // 错误 header
                new MockMultipartFile("file", "bad-header.csv", "text/csv",
                        "wrong_header\r\n".getBytes(StandardCharsets.UTF_8)),
                // 只有 header 无数据行
                new MockMultipartFile("file", "no-rows.csv", "text/csv",
                        (PdcNfcWriteResultImporterImpl.EXPECTED_HEADER + "\r\n")
                                .getBytes(StandardCharsets.UTF_8)),
                // 列数不足
                new MockMultipartFile("file", "short-row.csv", "text/csv",
                        (PdcNfcWriteResultImporterImpl.EXPECTED_HEADER + "\r\n"
                                + "\"a\",\"b\",\"c\"\r\n")
                                .getBytes(StandardCharsets.UTF_8))
        );
    }

    @ParameterizedTest(name = "无效文件: {0}")
    @MethodSource("invalidFiles")
    @DisplayName("无效 CSV 文件 → CSV_FORMAT_ERROR")
    void invalidCsvFiles(MockMultipartFile file) {
        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 1L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("重复 assetNo+wechatSn → CSV_FORMAT_ERROR")
    void duplicateKeys() {
        String sha = sha256("weixin://wxpay/test-scheme-1");
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        String row = "\"V1\",\"WRT-1\",\"B1\",\"1\",\"A-001\",\"SN1\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"uri\",\"04\",\"type\",\"pkg\","
                + "\"true\",\"true\",\"2\",\"" + sha + "\",\"com.tencent.mm\",\"true\"";
        // 两行相同的 key
        String csv = header + "\r\n" + row + "\r\n" + row + "\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "dup.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 1L))
                .isInstanceOf(RenException.class);
    }

    // --- 完整导入流程测试 ---

    @Test
    @DisplayName("全部验证通过：所有资产 → VERIFIED，任务 → COMPLETED")
    void allVerified() throws IOException {
        byte[] csvBytes = loadValidCsv();
        MockMultipartFile file = new MockMultipartFile("file", "result.csv",
                "text/csv", csvBytes);

        String uriSha1 = sha256("weixin://wxpay/test-scheme-1");
        String uriSha2 = sha256("weixin://wxpay/test-scheme-2");

        // 模拟任务
        PdcNfcWriteJobEntity job = makeJob(100L, "WRT-100-1",
                PdcNfcWriteJobStatus.EXPORTED.name());
        when(jobDao.selectById(100L)).thenReturn(job);

        // 模拟快照
        PdcNfcWriteJobItemEntity item1 = makeItem(1L, 100L, 1001L, 1,
                "B20260729001-000001", "EB00000000000000000000000001", uriSha1);
        PdcNfcWriteJobItemEntity item2 = makeItem(2L, 100L, 1002L, 2,
                "B20260729001-000002", "EB00000000000000000000000002", uriSha2);
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item1, item2));

        // 模拟资产（FOR UPDATE 返回）
        PdcNfcAssetEntity asset1 = makeAsset(1001L, "B20260729001-000001",
                PdcNfcAssetStatus.SCHEME_GENERATED.name());
        PdcNfcAssetEntity asset2 = makeAsset(1002L, "B20260729001-000002",
                PdcNfcAssetStatus.SCHEME_GENERATED.name());
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1, asset2));

        // 模拟 DAO 写入
        lenient().when(jobDao.updateById(any(PdcNfcWriteJobEntity.class))).thenReturn(1);
        lenient().when(assetDao.updateById(any(PdcNfcAssetEntity.class))).thenReturn(1);
        lenient().when(writeRecordDao.insert(any(PdcNfcWriteRecordEntity.class))).thenReturn(1);
        lenient().when(operationLogDao.insert(any(PdcNfcOperationLogEntity.class))).thenReturn(1);

        // 执行
        UUID requestId = UUID.randomUUID();
        PdcNfcWriteImportVO vo = importer.importResult(100L, requestId, file, 99L);

        // 验证结果
        assertThat(vo.jobId()).isEqualTo(100L);
        assertThat(vo.verifiedCount()).isEqualTo(2);
        assertThat(vo.writtenCount()).isEqualTo(0);
        assertThat(vo.failureCount()).isEqualTo(0);
        assertThat(vo.resultFileSha256()).hasSize(64);
        assertThat(vo.requestId()).isEqualTo(requestId);

        // 验证任务状态
        assertThat(job.getStatus()).isEqualTo(PdcNfcWriteJobStatus.COMPLETED.name());
        assertThat(job.getCompletedAt()).isNotNull();

        // 验证资产状态
        assertThat(asset1.getStatus()).isEqualTo(PdcNfcAssetStatus.VERIFIED.name());
        assertThat(asset2.getStatus()).isEqualTo(PdcNfcAssetStatus.VERIFIED.name());
        assertThat(asset1.getActiveWriteJobId()).isNull();
        assertThat(asset2.getActiveWriteJobId()).isNull();

        // 验证写卡记录被创建
        verify(writeRecordDao, times(2)).insert(any(PdcNfcWriteRecordEntity.class));
    }

    @Test
    @DisplayName("部分验证失败：部分 WRITTEN + 部分 VERIFIED → 任务 RESULT_IMPORTED")
    void partialVerification() throws IOException {
        String uriSha1 = sha256("weixin://wxpay/test-scheme-1");
        String uriSha2 = sha256("weixin://wxpay/test-scheme-2");

        // 构建 CSV：第一行全部通过，第二行 verify_success=false
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        String row1 = "\"V1\",\"WRT-100-1\",\"B20260729001\",\"1\","
                + "\"B20260729001-000001\",\"EB00000000000000000000000001\",\"SKU-KOI\",\"锦鲤\","
                + "\"01\",\"55\",\"weixin://wxpay/test-scheme-1\",\"04\",\"android.com:pkg\",\"com.tencent.mm\","
                + "\"true\",\"true\",\"2\",\"" + uriSha1 + "\",\"com.tencent.mm\",\"true\"";
        String row2 = "\"V1\",\"WRT-100-1\",\"B20260729001\",\"2\","
                + "\"B20260729001-000002\",\"EB00000000000000000000000002\",\"SKU-KOI\",\"玉兔\","
                + "\"01\",\"55\",\"weixin://wxpay/test-scheme-2\",\"04\",\"android.com:pkg\",\"com.tencent.mm\","
                + "\"true\",\"false\",\"2\",\"" + uriSha2 + "\",\"com.tencent.mm\",\"true\"";
        String csv = header + "\r\n" + row1 + "\r\n" + row2 + "\r\n";

        MockMultipartFile file = new MockMultipartFile("file", "partial.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        // 模拟
        PdcNfcWriteJobEntity job = makeJob(100L, "WRT-100-1",
                PdcNfcWriteJobStatus.EXPORTED.name());
        when(jobDao.selectById(100L)).thenReturn(job);

        PdcNfcWriteJobItemEntity item1 = makeItem(1L, 100L, 1001L, 1,
                "B20260729001-000001", "EB00000000000000000000000001", uriSha1);
        PdcNfcWriteJobItemEntity item2 = makeItem(2L, 100L, 1002L, 2,
                "B20260729001-000002", "EB00000000000000000000000002", uriSha2);
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item1, item2));

        PdcNfcAssetEntity asset1 = makeAsset(1001L, "B20260729001-000001",
                PdcNfcAssetStatus.SCHEME_GENERATED.name());
        PdcNfcAssetEntity asset2 = makeAsset(1002L, "B20260729001-000002",
                PdcNfcAssetStatus.SCHEME_GENERATED.name());
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1, asset2));

        lenient().when(jobDao.updateById(any(PdcNfcWriteJobEntity.class))).thenReturn(1);
        lenient().when(assetDao.updateById(any(PdcNfcAssetEntity.class))).thenReturn(1);
        lenient().when(writeRecordDao.insert(any(PdcNfcWriteRecordEntity.class))).thenReturn(1);
        lenient().when(operationLogDao.insert(any(PdcNfcOperationLogEntity.class))).thenReturn(1);

        PdcNfcWriteImportVO vo = importer.importResult(
                100L, UUID.randomUUID(), file, 99L);

        assertThat(vo.verifiedCount()).isEqualTo(1);
        assertThat(vo.writtenCount()).isEqualTo(1);
        assertThat(vo.failureCount()).isEqualTo(0);

        assertThat(asset1.getStatus()).isEqualTo(PdcNfcAssetStatus.VERIFIED.name());
        assertThat(asset2.getStatus()).isEqualTo(PdcNfcAssetStatus.WRITTEN.name());
        assertThat(job.getStatus()).isEqualTo(PdcNfcWriteJobStatus.RESULT_IMPORTED.name());
    }

    @Test
    @DisplayName("写卡失败：write_success=false → 资产保持 SCHEME_GENERATED")
    void writeFailure() {
        String uriSha1 = sha256("weixin://wxpay/test-scheme-1");
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        String row = "\"V1\",\"WRT-1\",\"B1\",\"1\","
                + "\"A-001\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"weixin://wxpay/test-scheme-1\",\"04\",\"android.com:pkg\",\"com.tencent.mm\","
                + "\"false\",\"false\",\"0\",\"\",\"\",\"false\"";
        String csv = header + "\r\n" + row + "\r\n";

        MockMultipartFile file = new MockMultipartFile("file", "fail.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        PdcNfcWriteJobEntity job = makeJob(1L, "WRT-1",
                PdcNfcWriteJobStatus.EXPORTED.name());
        when(jobDao.selectById(1L)).thenReturn(job);

        PdcNfcWriteJobItemEntity item = makeItem(1L, 1L, 101L, 1,
                "A-001", "SN-001", uriSha1);
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item));

        PdcNfcAssetEntity asset = makeAsset(101L, "A-001",
                PdcNfcAssetStatus.SCHEME_GENERATED.name());
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        lenient().when(jobDao.updateById(any(PdcNfcWriteJobEntity.class))).thenReturn(1);
        lenient().when(assetDao.updateById(any(PdcNfcAssetEntity.class))).thenReturn(1);
        lenient().when(writeRecordDao.insert(any(PdcNfcWriteRecordEntity.class))).thenReturn(1);
        lenient().when(operationLogDao.insert(any(PdcNfcOperationLogEntity.class))).thenReturn(1);

        PdcNfcWriteImportVO vo = importer.importResult(
                1L, UUID.randomUUID(), file, 99L);

        assertThat(vo.verifiedCount()).isEqualTo(0);
        assertThat(vo.writtenCount()).isEqualTo(0);
        assertThat(vo.failureCount()).isEqualTo(1);

        // 资产保持 SCHEME_GENERATED（写卡失败不改变状态）
        assertThat(asset.getStatus()).isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(job.getStatus()).isEqualTo(PdcNfcWriteJobStatus.RESULT_IMPORTED.name());
    }

    @Test
    @DisplayName("行数不匹配 → CSV_CONTENT_MISMATCH")
    void rowCountMismatch() {
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        // 只有一行数据
        String row = "\"V1\",\"WRT-1\",\"B1\",\"1\","
                + "\"A-001\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"uri\",\"04\",\"type\",\"pkg\","
                + "\"true\",\"true\",\"2\",\"sha\",\"com.tencent.mm\",\"true\"";
        String csv = header + "\r\n" + row + "\r\n";

        MockMultipartFile file = new MockMultipartFile("file", "mismatch.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        // 模拟任务有 2 个快照行
        PdcNfcWriteJobEntity job = makeJob(1L, "WRT-1",
                PdcNfcWriteJobStatus.EXPORTED.name());
        when(jobDao.selectById(1L)).thenReturn(job);

        PdcNfcWriteJobItemEntity item1 = makeItem(1L, 1L, 101L, 1,
                "A-001", "SN-001", "sha1");
        PdcNfcWriteJobItemEntity item2 = makeItem(2L, 1L, 102L, 2,
                "A-002", "SN-002", "sha2");
        when(jobItemDao.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item1, item2));

        assertThatThrownBy(() -> importer.importResult(
                1L, UUID.randomUUID(), file, 99L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("任务非 EXPORTED 状态 → INVALID_STATE")
    void wrongJobStatus() {
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        String row = "\"V1\",\"WRT-1\",\"B1\",\"1\","
                + "\"A-001\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"uri\",\"04\",\"type\",\"pkg\","
                + "\"true\",\"true\",\"2\",\"sha\",\"com.tencent.mm\",\"true\"";
        String csv = header + "\r\n" + row + "\r\n";

        MockMultipartFile file = new MockMultipartFile("file", "wrong-status.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        PdcNfcWriteJobEntity job = makeJob(1L, "WRT-1",
                PdcNfcWriteJobStatus.CREATED.name());
        when(jobDao.selectById(1L)).thenReturn(job);

        assertThatThrownBy(() -> importer.importResult(
                1L, UUID.randomUUID(), file, 99L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("任务不存在 → JOB_NOT_FOUND")
    void jobNotFound() {
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        String row = "\"V1\",\"WRT-1\",\"B1\",\"1\","
                + "\"A-001\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"uri\",\"04\",\"type\",\"pkg\","
                + "\"true\",\"true\",\"2\",\"sha\",\"com.tencent.mm\",\"true\"";
        String csv = header + "\r\n" + row + "\r\n";

        MockMultipartFile file = new MockMultipartFile("file", "notfound.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(jobDao.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> importer.importResult(
                999L, UUID.randomUUID(), file, 99L))
                .isInstanceOf(RenException.class);
    }

    // --- CSV 解析单元测试 ---

    @Test
    @DisplayName("RFC 4180 引号字段解析: 双引号转义正确")
    void rfc4180QuotedFields() {
        List<String> fields = PdcNfcWriteResultImporterImpl.parseCsvFields(
                "\"hello\",\"he\"\"llo\",\"world\"");
        assertThat(fields).containsExactly("hello", "he\"llo", "world");
    }

    @Test
    @DisplayName("非引号字段解析")
    void unquotedFields() {
        List<String> fields = PdcNfcWriteResultImporterImpl.parseCsvFields("a,b,c");
        assertThat(fields).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("空引号字段")
    void emptyQuotedFields() {
        List<String> fields = PdcNfcWriteResultImporterImpl.parseCsvFields("\"\",\"\",\"\"");
        assertThat(fields).containsExactly("", "", "");
    }

    // --- Issue 1: format_version 校验 ---

    @Test
    @DisplayName("format_version 不匹配 → CSV_FORMAT_ERROR")
    void wrongFormatVersion() {
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        String row = "\"WRONG_VERSION\",\"WRT-1\",\"B1\",\"1\","
                + "\"A-001\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"uri\",\"04\",\"type\",\"pkg\","
                + "\"true\",\"true\",\"2\",\"sha\",\"com.tencent.mm\",\"true\"";
        String csv = header + "\r\n" + row + "\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "bad-version.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 99L))
                .isInstanceOf(RenException.class);
    }

    // --- Issue 2: job_no 校验 ---

    @Test
    @DisplayName("job_no 不匹配 → CSV_CONTENT_MISMATCH")
    void wrongJobNo() {
        String uriSha = sha256("weixin://wxpay/test-scheme-1");
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        // CSV 中的 job_no 是 "WRONG-JOB"，但任务是 "WRT-1"
        String row = "\"V1\",\"WRONG-JOB\",\"B1\",\"1\","
                + "\"A-001\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"weixin://wxpay/test-scheme-1\",\"04\",\"android.com:pkg\",\"com.tencent.mm\","
                + "\"true\",\"true\",\"2\",\"" + uriSha + "\",\"com.tencent.mm\",\"true\"";
        String csv = header + "\r\n" + row + "\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "wrong-job.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        PdcNfcWriteJobEntity job = makeJob(1L, "WRT-1",
                PdcNfcWriteJobStatus.EXPORTED.name());
        when(jobDao.selectById(1L)).thenReturn(job);

        // job_no 校验在加载快照之前抛出，因此以下 stub 不需要
        // （无需模拟 jobItemDao 和 assetDao）

        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 99L))
                .isInstanceOf(RenException.class);
    }

    // --- Issue 3: 行长度检查 ---

    @Test
    @DisplayName("行超过 4096 字符 → CSV_FORMAT_ERROR")
    void lineTooLong() {
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        // 构造一个超长字段
        String longField = "x".repeat(5000);
        String row = "\"V1\",\"WRT-1\",\"B1\",\"1\","
                + "\"" + longField + "\",\"SN-001\",\"SKU\",\"proto\","
                + "\"01\",\"55\",\"uri\",\"04\",\"type\",\"pkg\","
                + "\"true\",\"true\",\"2\",\"sha\",\"com.tencent.mm\",\"true\"";
        String csv = header + "\r\n" + row + "\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "long-line.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 99L))
                .isInstanceOf(RenException.class);
    }

    // --- Issue 4: 非 UTF-8 文件检测 ---

    @Test
    @DisplayName("非 UTF-8 字节 → CSV_FORMAT_ERROR")
    void nonUtf8File() {
        String header = PdcNfcWriteResultImporterImpl.EXPECTED_HEADER;
        byte[] headerBytes = (header + "\r\n").getBytes(StandardCharsets.UTF_8);
        // 构造包含无效 UTF-8 字节 (0xC0 0x80 = overlong NUL) 的数据行
        byte[] badBytes = new byte[]{(byte) 0xC0, (byte) 0x80};
        byte[] rowPrefix = "\"V1\",\"WRT-1\",\"B1\",\"1\",\"".getBytes(StandardCharsets.UTF_8);
        byte[] rowSuffix = "\",\"SN\",\"SKU\",\"p\",\"01\",\"55\",\"u\",\"04\",\"t\",\"p\",\"true\",\"true\",\"2\",\"s\",\"c\",\"true\"\r\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] dataRow = new byte[rowPrefix.length + badBytes.length + rowSuffix.length];
        System.arraycopy(rowPrefix, 0, dataRow, 0, rowPrefix.length);
        System.arraycopy(badBytes, 0, dataRow, rowPrefix.length, badBytes.length);
        System.arraycopy(rowSuffix, 0, dataRow, rowPrefix.length + badBytes.length, rowSuffix.length);
        byte[] fullCsv = new byte[headerBytes.length + dataRow.length];
        System.arraycopy(headerBytes, 0, fullCsv, 0, headerBytes.length);
        System.arraycopy(dataRow, 0, fullCsv, headerBytes.length, dataRow.length);

        MockMultipartFile file = new MockMultipartFile("file", "bad-utf8.csv",
                "text/csv", fullCsv);
        assertThatThrownBy(() -> importer.importResult(1L, UUID.randomUUID(), file, 99L))
                .isInstanceOf(RenException.class);
    }
}
