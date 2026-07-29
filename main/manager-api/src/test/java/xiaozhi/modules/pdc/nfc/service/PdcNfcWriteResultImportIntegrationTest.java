package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAdminOperationType;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.crypto.RequestFingerprint;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAdminRequestDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteRecordDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcAdminIdempotencyServiceImpl;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcWriteResultImporterImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdcNfcWriteResultImport 集成测试（幂等 + 导入）")
class PdcNfcWriteResultImportIntegrationTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        lenient().when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(java.util.Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PdcNfcAdminRequestDao adminRequestDao;
    @Mock private PdcNfcWriteJobDao jobDao;
    @Mock private PdcNfcWriteJobItemDao jobItemDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcBatchDao batchDao;
    @Mock private PdcNfcWriteRecordDao writeRecordDao;
    @Mock private PdcNfcOperationLogDao operationLogDao;

    private PdcNfcAdminIdempotencyServiceImpl idempotencyService;
    private PdcNfcWriteResultImporterImpl importer;

    @BeforeEach
    void setUp() {
        RequestFingerprint fingerprint = new RequestFingerprint();
        ObjectMapper objectMapper = new ObjectMapper();
        idempotencyService = new PdcNfcAdminIdempotencyServiceImpl(
                adminRequestDao, fingerprint, objectMapper);
        importer = new PdcNfcWriteResultImporterImpl(
                jobDao, jobItemDao, assetDao, batchDao,
                writeRecordDao, operationLogDao,
                new PdcNfcWriteJobStateMachine(),
                new PdcNfcAssetStateMachine());
    }

    private String sha256(String value) {
        return PdcNfcWriteCsvExporter.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("完整流程：幂等服务包裹导入器，首次执行成功")
    void fullFlowFirstExecution() throws IOException {
        byte[] csvBytes;
        try (InputStream is = getClass().getResourceAsStream("/pdc/nfc/PDC_NFC_RESULT_V1.valid.csv")) {
            assertThat(is).isNotNull();
            csvBytes = is.readAllBytes();
        }

        MockMultipartFile file = new MockMultipartFile("file", "result.csv",
                "text/csv", csvBytes);

        UUID requestId = UUID.randomUUID();
        Long jobId = 100L;
        Long operatorId = 99L;

        // 幂等服务：无已有记录
        when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        lenient().when(adminRequestDao.insert(any(xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity.class))).thenReturn(1);

        // 导入器模拟
        String uriSha1 = sha256("weixin://wxpay/test-scheme-1");
        String uriSha2 = sha256("weixin://wxpay/test-scheme-2");

        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setId(jobId);
        job.setJobNo("WRT-100-1");
        job.setStatus(PdcNfcWriteJobStatus.EXPORTED.name());
        job.setBatchId(1L);
        job.setTotalCount(2);
        job.setCreateDate(new Date());
        when(jobDao.selectById(jobId)).thenReturn(job);

        PdcNfcWriteJobItemEntity item1 = new PdcNfcWriteJobItemEntity();
        item1.setId(1L); item1.setJobId(jobId); item1.setAssetId(1001L);
        item1.setSequenceNo(1); item1.setAssetNo("B20260729001-000001");
        item1.setWechatSn("EB00000000000000000000000001"); item1.setUriSha256(uriSha1);
        item1.setCreateDate(new Date());

        PdcNfcWriteJobItemEntity item2 = new PdcNfcWriteJobItemEntity();
        item2.setId(2L); item2.setJobId(jobId); item2.setAssetId(1002L);
        item2.setSequenceNo(2); item2.setAssetNo("B20260729001-000002");
        item2.setWechatSn("EB00000000000000000000000002"); item2.setUriSha256(uriSha2);
        item2.setCreateDate(new Date());

        when(jobItemDao.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item1, item2));

        PdcNfcAssetEntity asset1 = new PdcNfcAssetEntity();
        asset1.setId(1001L); asset1.setAssetNo("B20260729001-000001");
        asset1.setStatus(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        asset1.setVersion(1); asset1.setActiveWriteJobId(jobId);
        asset1.setCreateDate(new Date());

        PdcNfcAssetEntity asset2 = new PdcNfcAssetEntity();
        asset2.setId(1002L); asset2.setAssetNo("B20260729001-000002");
        asset2.setStatus(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        asset2.setVersion(1); asset2.setActiveWriteJobId(jobId);
        asset2.setCreateDate(new Date());

        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1, asset2));

        lenient().when(jobDao.updateById(any(PdcNfcWriteJobEntity.class))).thenReturn(1);
        lenient().when(assetDao.updateById(any(PdcNfcAssetEntity.class))).thenReturn(1);
        lenient().when(writeRecordDao.insert(any(xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteRecordEntity.class))).thenReturn(1);
        lenient().when(operationLogDao.insert(any(xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity.class))).thenReturn(1);

        // 通过幂等服务执行
        String canonicalRequest = jobId + ":" + requestId;
        PdcNfcWriteImportVO vo = idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                canonicalRequest,
                PdcNfcWriteImportVO.class,
                () -> importer.importResult(jobId, requestId, file, operatorId)
        );

        // 验证
        assertThat(vo.jobId()).isEqualTo(jobId);
        assertThat(vo.verifiedCount()).isEqualTo(2);
        assertThat(vo.writtenCount()).isEqualTo(0);
        assertThat(vo.failureCount()).isEqualTo(0);
        assertThat(vo.resultFileSha256()).hasSize(64);
        assertThat(vo.requestId()).isEqualTo(requestId);

        // 验证幂等记录被存储
        verify(adminRequestDao).insert(any(xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity.class));

        // 验证任务状态推进
        assertThat(job.getStatus()).isEqualTo(PdcNfcWriteJobStatus.COMPLETED.name());
    }

    @Test
    @DisplayName("幂等重放：第二次调用返回缓存结果，不重新执行导入")
    void idempotentReplay() {
        UUID requestId = UUID.randomUUID();
        Long jobId = 100L;

        // 已存在的幂等记录
        String canonicalRequest = jobId + ":" + requestId;
        RequestFingerprint fp = new RequestFingerprint();
        String fingerprint = fp.sha256Canonical(canonicalRequest);

        PdcNfcWriteImportVO cachedVo = new PdcNfcWriteImportVO(
                jobId, "WRT-100-1", 2, 0, 0,
                "abc123def456", requestId);
        ObjectMapper om = new ObjectMapper();
        String cachedJson;
        try {
            cachedJson = om.writeValueAsString(cachedVo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var existing = new xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity();
        existing.setOperationType(PdcNfcAdminOperationType.WRITE_RESULT_IMPORT.name());
        existing.setRequestId(requestId.toString());
        existing.setRequestFingerprint(fingerprint);
        existing.setResponseJson(cachedJson);
        existing.setStatus("SUCCESS");

        when(adminRequestDao.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        // 使用空文件（不应被解析）
        MockMultipartFile file = new MockMultipartFile("file", "result.csv",
                "text/csv", "should-not-be-parsed".getBytes(StandardCharsets.UTF_8));

        // 通过幂等服务执行
        PdcNfcWriteImportVO vo = idempotencyService.execute(
                PdcNfcAdminOperationType.WRITE_RESULT_IMPORT,
                requestId,
                canonicalRequest,
                PdcNfcWriteImportVO.class,
                () -> { throw new AssertionError("Should not be called for replay"); }
        );

        assertThat(vo.jobId()).isEqualTo(jobId);
        assertThat(vo.verifiedCount()).isEqualTo(2);
        assertThat(vo.requestId()).isEqualTo(requestId);

        // 导入器不应被调用
        verify(jobDao, never()).selectById(any());
    }
}
