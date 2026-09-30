package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcVerifySource;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobMode;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.support.MySqlContainerSupport;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcTrustReleaseVO;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 免检放行（ADR 0005）真实事务集成测试。
 * 基建与 PdcNfcWriteResultImportIntegrationTest 同一 seam：Testcontainers MySQL + 真实事务。
 */
@SpringJUnitConfig(PdcNfcTrustReleaseIntegrationTest.TestConfig.class)
@DisplayName("PdcNfcTrustRelease 真实事务集成测试")
class PdcNfcTrustReleaseIntegrationTest extends MySqlContainerSupport {

    private static final Long JOB_ID = 100L;
    private static final Long FIRST_ASSET_ID = 1001L;
    private static final Long SECOND_ASSET_ID = 1002L;
    private static final Long OPERATOR_ID = 99L;

    @Autowired private DataSource dataSource;
    @Autowired private PdcNfcTrustReleaseService trustReleaseService;
    @Autowired private PdcNfcBatchDao batchDao;
    @Autowired private PdcNfcAssetDao assetDao;
    @Autowired private PdcNfcWriteJobDao writeJobDao;
    @Autowired private PdcNfcWriteJobItemDao writeJobItemDao;
    @Autowired private PdcNfcOperationLogDao operationLogDao;

    @BeforeAll
    static void initMessageSource() {
        org.springframework.context.ApplicationContext applicationContext =
                org.mockito.Mockito.mock(
                        org.springframework.context.ApplicationContext.class);
        org.springframework.context.MessageSource messageSource =
                org.mockito.Mockito.mock(
                        org.springframework.context.MessageSource.class);
        org.mockito.Mockito.lenient()
                .when(applicationContext.getBean("messageSource"))
                .thenReturn(messageSource);
        org.mockito.Mockito.lenient()
                .when(messageSource.getMessage(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.util.Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        xiaozhi.common.utils.SpringContextUtils.applicationContext =
                applicationContext;
    }

    @BeforeEach
    void setUpDatabase() {
        resetSchema();
        new ResourceDatabasePopulator(
                new ClassPathResource("db/changelog/202607291000.sql"),
                new ClassPathResource("db/changelog/202607301000.sql"),
                new ClassPathResource("db/changelog/202608271000.sql"))
                .execute(dataSource);
        insertBatch();
        insertAsset(FIRST_ASSET_ID, "A-001", "000001", "SN-001", "1".repeat(64), "a".repeat(64));
        insertAsset(SECOND_ASSET_ID, "A-002", "000002", "SN-002", "2".repeat(64), "b".repeat(64));
        insertJob(PdcNfcWriteJobMode.FACTORY_CSV.name(), PdcNfcWriteJobStatus.EXPORTED.name());
        insertJobItem(1L, FIRST_ASSET_ID, 1, "A-001", "SN-001", "a".repeat(64));
        insertJobItem(2L, SECOND_ASSET_ID, 2, "A-002", "SN-002", "b".repeat(64));
    }

    @Test
    @DisplayName("正常放行：资产 VERIFIED + FACTORY_TRUST，任务 COMPLETED，批次 READY_FOR_STOCK，审计落库")
    void releasesExportedFactoryJob() {
        assertThat(AopUtils.isAopProxy(trustReleaseService)).isTrue();
        UUID requestId = UUID.randomUUID();

        PdcNfcTrustReleaseVO vo = trustReleaseService.trustRelease(
                JOB_ID, true, OPERATOR_ID, requestId);

        assertThat(vo.jobId()).isEqualTo(JOB_ID);
        assertThat(vo.jobNo()).isEqualTo("WRT-100-1");
        assertThat(vo.releasedCount()).isEqualTo(2);
        assertThat(vo.requestId()).isEqualTo(requestId);

        for (Long assetId : List.of(FIRST_ASSET_ID, SECOND_ASSET_ID)) {
            PdcNfcAssetEntity asset = assetDao.selectById(assetId);
            assertThat(asset.getStatus()).isEqualTo(PdcNfcAssetStatus.VERIFIED.name());
            assertThat(asset.getVerifySource()).isEqualTo(PdcNfcVerifySource.FACTORY_TRUST.name());
            assertThat(asset.getVerifiedAt()).isNotNull();
            // 免检通道不知道工厂真实写卡时间，writtenAt 不虚记
            assertThat(asset.getWrittenAt()).isNull();
            assertThat(asset.getActiveWriteJobId()).isNull();
        }

        PdcNfcWriteJobEntity job = writeJobDao.selectById(JOB_ID);
        assertThat(job.getStatus()).isEqualTo(PdcNfcWriteJobStatus.COMPLETED.name());
        assertThat(job.getSuccessCount()).isEqualTo(2);
        assertThat(job.getFailureCount()).isEqualTo(0);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getResultResponseJson()).contains("trustRelease");

        assertThat(batchDao.selectById(1L).getStatus())
                .isEqualTo(PdcNfcBatchStatus.READY_FOR_STOCK.name());

        List<PdcNfcOperationLogEntity> logs = operationLogDao.selectList(
                new LambdaQueryWrapper<PdcNfcOperationLogEntity>()
                        .eq(PdcNfcOperationLogEntity::getOperationType, "TRUST_RELEASE"));
        assertThat(logs).hasSize(1);
        PdcNfcOperationLogEntity entry = logs.get(0);
        assertThat(entry.getOperatorUserId()).isEqualTo(OPERATOR_ID);
        assertThat(entry.getObjectId()).isEqualTo(JOB_ID);
        assertThat(entry.getQuantity()).isEqualTo(2);
        assertThat(entry.getBeforeStatus()).isEqualTo(PdcNfcWriteJobStatus.EXPORTED.name());
        assertThat(entry.getAfterStatus()).isEqualTo(PdcNfcWriteJobStatus.COMPLETED.name());
        assertThat(entry.getRequestId()).isEqualTo(requestId.toString());
        assertThat(entry.getDetailJson()).contains("lockConfirmed");
    }

    @Test
    @DisplayName("缺锁卡声明拒绝且不留任何数据库变更")
    void rejectsWhenLockNotDeclared() {
        assertThatThrownBy(() -> trustReleaseService.trustRelease(
                JOB_ID, false, OPERATOR_ID, UUID.randomUUID()))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(xiaozhi.common.exception.ErrorCode.PDC_NFC_LOCK_DECLARATION_REQUIRED);

        assertInitialDatabaseState();
    }

    @Test
    @DisplayName("手动模式任务拒绝免检放行（10525 模式互斥）")
    void rejectsManualModeJob() {
        PdcNfcWriteJobEntity job = writeJobDao.selectById(JOB_ID);
        job.setMode(PdcNfcWriteJobMode.MANUAL.name());
        writeJobDao.updateById(job);

        assertThatThrownBy(() -> trustReleaseService.trustRelease(
                JOB_ID, true, OPERATOR_ID, UUID.randomUUID()))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(xiaozhi.common.exception.ErrorCode.PDC_NFC_JOB_MODE_MISMATCH);

        // 手动任务状态未被改动，资产保持 SCHEME_GENERATED
        assertThat(writeJobDao.selectById(JOB_ID).getStatus())
                .isEqualTo(PdcNfcWriteJobStatus.EXPORTED.name());
        assertThat(assetDao.selectById(FIRST_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
    }

    @Test
    @DisplayName("非 EXPORTED 状态任务拒绝（CREATED 未导出不可放行）")
    void rejectsJobNotExported() {
        PdcNfcWriteJobEntity job = writeJobDao.selectById(JOB_ID);
        job.setStatus(PdcNfcWriteJobStatus.CREATED.name());
        writeJobDao.updateById(job);

        assertThatThrownBy(() -> trustReleaseService.trustRelease(
                JOB_ID, true, OPERATOR_ID, UUID.randomUUID()))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(xiaozhi.common.exception.ErrorCode.PDC_NFC_INVALID_STATE);

        // 任务保持 CREATED，资产/批次/审计均不变
        assertThat(writeJobDao.selectById(JOB_ID).getStatus())
                .isEqualTo(PdcNfcWriteJobStatus.CREATED.name());
        assertThat(assetDao.selectById(FIRST_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(assetDao.selectById(SECOND_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(batchDao.selectById(1L).getStatus())
                .isEqualTo(PdcNfcBatchStatus.WRITING.name());
        assertThat(operationLogDao.selectCount(null)).isZero();
    }

    @Test
    @DisplayName("已导入结果的任务拒绝免检（与导入互斥）")
    void rejectsResultImportedJob() {
        PdcNfcWriteJobEntity job = writeJobDao.selectById(JOB_ID);
        job.setStatus(PdcNfcWriteJobStatus.RESULT_IMPORTED.name());
        writeJobDao.updateById(job);

        assertThatThrownBy(() -> trustReleaseService.trustRelease(
                JOB_ID, true, OPERATOR_ID, UUID.randomUUID()))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(xiaozhi.common.exception.ErrorCode.PDC_NFC_INVALID_STATE);

        assertThat(writeJobDao.selectById(JOB_ID).getStatus())
                .isEqualTo(PdcNfcWriteJobStatus.RESULT_IMPORTED.name());
        assertThat(assetDao.selectById(FIRST_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
    }

    @Test
    @DisplayName("任务内任一资产非待写卡状态则整体回滚（全有或全无）")
    void rollsBackWhenAnyAssetNotSchemeGenerated() {
        // 模拟第一张卡已被报废
        PdcNfcAssetEntity scrapped = assetDao.selectById(FIRST_ASSET_ID);
        scrapped.setStatus(PdcNfcAssetStatus.SCRAPPED.name());
        assetDao.updateById(scrapped);

        assertThatThrownBy(() -> trustReleaseService.trustRelease(
                JOB_ID, true, OPERATOR_ID, UUID.randomUUID()))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(xiaozhi.common.exception.ErrorCode.PDC_NFC_INVALID_STATE);

        // 第二张卡不被部分放行，任务/批次/审计均不变
        assertThat(assetDao.selectById(SECOND_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(writeJobDao.selectById(JOB_ID).getStatus())
                .isEqualTo(PdcNfcWriteJobStatus.EXPORTED.name());
        assertThat(batchDao.selectById(1L).getStatus())
                .isEqualTo(PdcNfcBatchStatus.WRITING.name());
        assertThat(operationLogDao.selectCount(null)).isZero();
    }

    @Test
    @DisplayName("资产租约被其他任务持有时整体回滚（核心并发前置）")
    void rollsBackWhenLeaseHeldByAnotherJob() {
        // 模拟第二张卡的写卡租约被另一个任务持有（如历史数据漂移）
        PdcNfcAssetEntity drifted = assetDao.selectById(SECOND_ASSET_ID);
        drifted.setActiveWriteJobId(999L);
        assetDao.updateById(drifted);

        assertThatThrownBy(() -> trustReleaseService.trustRelease(
                JOB_ID, true, OPERATOR_ID, UUID.randomUUID()))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(xiaozhi.common.exception.ErrorCode.PDC_NFC_INVALID_STATE);

        assertInitialDatabaseState();
        assertThat(assetDao.selectById(SECOND_ASSET_ID).getActiveWriteJobId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("任务不存在拒绝（10510）")
    void rejectsMissingJob() {
        assertThatThrownBy(() -> trustReleaseService.trustRelease(
                999L, true, OPERATOR_ID, UUID.randomUUID()))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(xiaozhi.common.exception.ErrorCode.PDC_NFC_JOB_NOT_FOUND);
    }

    private void insertBatch() {
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(1L);
        batch.setBatchNo("B-001");
        batch.setProductTypeId(1L);
        batch.setSkuCode("SKU-KOI");
        batch.setPrototype("锦鲤");
        batch.setPlannedQuantity(2);
        batch.setStatus(PdcNfcBatchStatus.WRITING.name());
        batch.setCreateDate(new Date());
        batchDao.insert(batch);
    }

    private void insertAsset(Long id, String assetNo, String itemNo, String wechatSn,
                             String claimHash, String schemeSha256) {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(id);
        asset.setAssetNo(assetNo);
        asset.setBatchId(1L);
        asset.setItemNo(itemNo);
        asset.setSkuCode("SKU-KOI");
        asset.setPrototype("锦鲤");
        asset.setWechatSn(wechatSn);
        asset.setClaimRefHash(claimHash);
        asset.setClaimRefHashVersion("v1");
        asset.setClaimRefKeyVersion("v1");
        asset.setClaimRefNonce(new byte[12]);
        asset.setClaimRefCiphertext(new byte[32]);
        asset.setSchemeSha256(schemeSha256);
        asset.setStatus(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        asset.setVersion(1);
        asset.setActiveWriteJobId(JOB_ID);
        asset.setCreateDate(new Date());
        assetDao.insert(asset);
    }

    private void insertJob(String mode, String status) {
        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setId(JOB_ID);
        job.setJobNo("WRT-100-1");
        job.setBatchId(1L);
        job.setFormatVersion("PDC_NFC_WRITE_V1");
        job.setMode(mode);
        job.setStatus(status);
        job.setTotalCount(2);
        job.setRowCount(2);
        job.setSuccessCount(0);
        job.setFailureCount(0);
        job.setCreateDate(new Date());
        writeJobDao.insert(job);
    }

    private void insertJobItem(Long id, Long assetId, int sequenceNo,
                               String assetNo, String wechatSn, String uriSha256) {
        PdcNfcWriteJobItemEntity item = new PdcNfcWriteJobItemEntity();
        item.setId(id);
        item.setJobId(JOB_ID);
        item.setAssetId(assetId);
        item.setSequenceNo(sequenceNo);
        item.setAssetNo(assetNo);
        item.setBatchNo("B-001");
        item.setWechatSn(wechatSn);
        item.setSkuCode("SKU-KOI");
        item.setPrototype("锦鲤");
        item.setUriSha256(uriSha256);
        item.setUriTnf("0x01");
        item.setUriType("U");
        item.setAarTnf("0x04");
        item.setAarType("android.com:pkg");
        item.setAarPayload("com.tencent.mm");
        item.setCreateDate(new Date());
        writeJobItemDao.insert(item);
    }

    /**
     * 每个用例前清空由 Liquibase 已创建的 NFC 表，
     * 让脚本可以无冲突重建初始数据。
     */
    private void resetSchema() {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            var tables = statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name LIKE 'pdc\\_nfc\\_%'");
            List<String> names = new java.util.ArrayList<>();
            while (tables.next()) {
                names.add(tables.getString(1));
            }
            tables.close();
            for (String name : names) {
                statement.execute("DROP TABLE IF EXISTS `" + name + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to reset NFC test schema", exception);
        }
    }

    private void assertInitialDatabaseState() {
        assertThat(assetDao.selectById(FIRST_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(assetDao.selectById(SECOND_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(writeJobDao.selectById(JOB_ID).getStatus())
                .isEqualTo(PdcNfcWriteJobStatus.EXPORTED.name());
        assertThat(batchDao.selectById(1L).getStatus())
                .isEqualTo(PdcNfcBatchStatus.WRITING.name());
        assertThat(operationLogDao.selectCount(null)).isZero();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("xiaozhi.modules.pdc.nfc.dao")
    @Import({
            PdcNfcAssetStateMachine.class,
            PdcNfcWriteJobStateMachine.class,
            PdcNfcBatchStateMachine.class
    })
    @ComponentScan(
            basePackages = "xiaozhi.modules.pdc.nfc.service.impl",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "xiaozhi\\.modules\\.pdc\\.nfc\\.service\\.impl\\."
                            + "PdcNfcTrustReleaseServiceImpl"))
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    static class TestConfig {

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            return interceptor;
        }
    }
}
