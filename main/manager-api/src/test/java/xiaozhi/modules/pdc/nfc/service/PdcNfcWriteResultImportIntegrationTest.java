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
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteRecordDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcWriteResultRow;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteRecordEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcWriteJobServiceImpl;
import xiaozhi.modules.pdc.nfc.support.MySqlContainerSupport;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(PdcNfcWriteResultImportIntegrationTest.TestConfig.class)
@DisplayName("PdcNfcWriteResultImport 真实事务集成测试")
class PdcNfcWriteResultImportIntegrationTest extends MySqlContainerSupport {

    private static final Long JOB_ID = 100L;
    private static final Long FIRST_ASSET_ID = 1001L;
    private static final Long SECOND_ASSET_ID = 1002L;

    @Autowired private DataSource dataSource;
    @Autowired private PdcNfcWriteResultTransactionService transactionService;
    @Autowired private PdcNfcWriteJobService writeJobService;
    @Autowired private CancelJobReadGate cancelJobReadGate;
    @Autowired private PdcNfcBatchDao batchDao;
    @Autowired private PdcNfcAssetDao assetDao;
    @Autowired private PdcNfcWriteJobDao writeJobDao;
    @Autowired private PdcNfcWriteJobItemDao writeJobItemDao;
    @Autowired private PdcNfcWriteRecordDao writeRecordDao;
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
        insertAsset(
                FIRST_ASSET_ID, "A-001", "000001", "SN-001",
                "1".repeat(64), "a".repeat(64));
        insertAsset(
                SECOND_ASSET_ID, "A-002", "000002", "SN-002",
                "2".repeat(64), "b".repeat(64));
        insertJob();
        insertJobItem(1L, FIRST_ASSET_ID, 1, "A-001", "SN-001", "a".repeat(64));
        insertJobItem(2L, SECOND_ASSET_ID, 2, "A-002", "SN-002", "b".repeat(64));
        createSecondRecordFailureTrigger();
    }

    @Test
    @DisplayName("第二行写卡记录数据库异常时，通过 Spring 代理回滚全部 NFC 写入")
    void rollsBackEveryDatabaseWriteWhenSecondRecordInsertFails() {
        assertThat(AopUtils.isAopProxy(transactionService)).isTrue();
        assertThatThrownBy(() -> transactionService.apply(
                JOB_ID, validatedRows(), "f".repeat(64), 99L, UUID.randomUUID()))
                .hasRootCauseInstanceOf(SQLException.class);

        assertThat(assetDao.selectById(FIRST_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(assetDao.selectById(SECOND_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(writeRecordDao.selectCount(
                new LambdaQueryWrapper<PdcNfcWriteRecordEntity>()
                        .eq(PdcNfcWriteRecordEntity::getJobId, JOB_ID)))
                .isZero();
        assertThat(writeJobDao.selectById(JOB_ID).getStatus())
                .isEqualTo(PdcNfcWriteJobStatus.EXPORTED.name());
        assertThat(batchDao.selectById(1L).getStatus()).isEqualTo("WRITING");
        assertThat(operationLogDao.selectCount(null)).isZero();
    }

    @Test
    @DisplayName("事务入口拒绝 Scheme 泄露且不留下任何数据库变更")
    void transactionEntryRejectsSensitiveErrorMessageWithoutDatabaseChanges() {
        ValidatedWriteResult sensitive = validatedRow(
                1L,
                FIRST_ASSET_ID,
                "A-001",
                "SN-001",
                "a".repeat(64),
                "reader leaked WEIXIN://dl/business/?t=secret");

        assertThatThrownBy(() -> transactionService.apply(
                JOB_ID, List.of(sensitive), "e".repeat(64), 99L, UUID.randomUUID()))
                .isInstanceOf(xiaozhi.common.exception.RenException.class)
                .hasMessageNotContaining("WEIXIN://");

        assertInitialDatabaseState();
    }

    @Test
    @DisplayName("导入与取消并发时只允许一个完整终态")
    void serializesImportAgainstCancel() throws Exception {
        dropSecondRecordFailureTrigger();
        cancelJobReadGate.arm();
        ExecutorService cancelExecutor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "nfc-cancel"));
        ExecutorService importExecutor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "nfc-import"));
        Future<Throwable> cancelFuture = cancelExecutor.submit(
                () -> captureFailure(() -> writeJobService.cancel(JOB_ID, 77L)));
        Future<Throwable> importFuture = null;
        Throwable earlyImportFailure = null;
        boolean importCompletedBeforeCancelRelease = false;
        try {
            assertThat(cancelJobReadGate.awaitJobRead(10, TimeUnit.SECONDS)).isTrue();
            importFuture = importExecutor.submit(() -> captureFailure(
                    () -> transactionService.apply(
                            JOB_ID,
                            validatedRows(),
                            "d".repeat(64),
                            99L,
                            UUID.randomUUID())));
            try {
                earlyImportFailure = importFuture.get(2, TimeUnit.SECONDS);
                importCompletedBeforeCancelRelease = true;
            } catch (TimeoutException expectedWhenJobIsLocked) {
                // Fixed path blocks until the cancelling transaction releases the job row.
            }
        } finally {
            cancelJobReadGate.releaseCancel();
        }

        Throwable cancelFailure = cancelFuture.get(10, TimeUnit.SECONDS);
        Throwable importFailure = importCompletedBeforeCancelRelease
                ? earlyImportFailure
                : importFuture.get(10, TimeUnit.SECONDS);
        cancelExecutor.shutdownNow();
        importExecutor.shutdownNow();

        PdcNfcWriteJobEntity job = writeJobDao.selectById(JOB_ID);
        PdcNfcAssetEntity first = assetDao.selectById(FIRST_ASSET_ID);
        PdcNfcAssetEntity second = assetDao.selectById(SECOND_ASSET_ID);
        long recordCount = writeRecordDao.selectCount(
                new LambdaQueryWrapper<PdcNfcWriteRecordEntity>()
                        .eq(PdcNfcWriteRecordEntity::getJobId, JOB_ID));

        boolean completeImport =
                PdcNfcWriteJobStatus.COMPLETED.name().equals(job.getStatus())
                        && PdcNfcAssetStatus.VERIFIED.name().equals(first.getStatus())
                        && PdcNfcAssetStatus.VERIFIED.name().equals(second.getStatus())
                        && recordCount == 2;
        // 取消完整生效后，导入只剩两种安全结果：被任务锁挡住而未执行，
        // 或重新读取到 CANCELLED 状态后拒绝；两者都不留任何导入痕迹。
        boolean completeCancel =
                PdcNfcWriteJobStatus.CANCELLED.name().equals(job.getStatus())
                        && PdcNfcAssetStatus.SCHEME_GENERATED.name().equals(first.getStatus())
                        && PdcNfcAssetStatus.SCHEME_GENERATED.name().equals(second.getStatus())
                        && first.getActiveWriteJobId() == null
                        && second.getActiveWriteJobId() == null
                        && recordCount == 0;
        boolean importRejectedAfterCancel =
                completeCancel && importFailure != null;

        assertThat(completeImport || completeCancel || importRejectedAfterCancel)
                .as("cancelFailure=%s, importFailure=%s, job=%s, records=%s",
                        cancelFailure, importFailure, job.getStatus(), recordCount)
                .isTrue();
    }

    private void insertBatch() {
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(1L);
        batch.setBatchNo("B-001");
        batch.setProductTypeId(1L);
        batch.setSkuCode("SKU-KOI");
        batch.setPrototype("锦鲤");
        batch.setPlannedQuantity(2);
        batch.setStatus("WRITING");
        batch.setCreateDate(new Date());
        batchDao.insert(batch);
    }

    private void insertAsset(
            Long id,
            String assetNo,
            String itemNo,
            String wechatSn,
            String claimHash,
            String schemeSha256) {
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

    private void insertJob() {
        PdcNfcWriteJobEntity job = new PdcNfcWriteJobEntity();
        job.setId(JOB_ID);
        job.setJobNo("WRT-100-1");
        job.setBatchId(1L);
        job.setFormatVersion("PDC_NFC_WRITE_V1");
        job.setStatus(PdcNfcWriteJobStatus.EXPORTED.name());
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
     * 每个用例前清空由 Liquibase 已创建的 NFC 表和残留触发器，
     * 让脚本可以无冲突重建初始数据。
     */
    private void resetSchema() {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS fail_second_write_record");
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

    private void createSecondRecordFailureTrigger() {        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER fail_second_write_record
                    BEFORE INSERT ON pdc_nfc_write_record
                    FOR EACH ROW
                    BEGIN
                      IF NEW.asset_id = 1002 THEN
                        SIGNAL SQLSTATE '45000'
                          SET MESSAGE_TEXT = 'second write record rejected';
                      END IF;
                    END
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create transaction test trigger", exception);
        }
    }

    private void dropSecondRecordFailureTrigger() {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_second_write_record");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to drop transaction test trigger", exception);
        }
    }

    private List<ValidatedWriteResult> validatedRows() {
        return List.of(
                validatedRow(
                        1L, FIRST_ASSET_ID, "A-001", "SN-001", "a".repeat(64)),
                validatedRow(
                        2L, SECOND_ASSET_ID, "A-002", "SN-002", "b".repeat(64))
        );
    }

    private ValidatedWriteResult validatedRow(
            Long itemId, Long assetId, String assetNo,
            String wechatSn, String uriSha256) {
        return validatedRow(itemId, assetId, assetNo, wechatSn, uriSha256, "");
    }

    private ValidatedWriteResult validatedRow(
            Long itemId,
            Long assetId,
            String assetNo,
            String wechatSn,
            String uriSha256,
            String errorMessage) {
        PdcNfcWriteResultRow row = new PdcNfcWriteResultRow(
                "PDC_NFC_RESULT_V1",
                "WRT-100-1",
                assetNo,
                wechatSn,
                "SUCCESS",
                "SUCCESS",
                "04AABBCC",
                2,
                uriSha256,
                "com.tencent.mm",
                true,
                LocalDateTime.of(2026, 7, 29, 10, 20, 30),
                "",
                errorMessage
        );
        return new ValidatedWriteResult(
                row,
                writeJobItemDao.selectById(itemId),
                assetDao.selectById(assetId),
                PdcNfcAssetStatus.VERIFIED,
                true
        );
    }

    private Throwable captureFailure(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void assertInitialDatabaseState() {
        assertThat(assetDao.selectById(FIRST_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(assetDao.selectById(SECOND_ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
        assertThat(writeRecordDao.selectCount(
                new LambdaQueryWrapper<PdcNfcWriteRecordEntity>()
                        .eq(PdcNfcWriteRecordEntity::getJobId, JOB_ID)))
                .isZero();
        assertThat(writeJobDao.selectById(JOB_ID).getStatus())
                .isEqualTo(PdcNfcWriteJobStatus.EXPORTED.name());
        assertThat(batchDao.selectById(1L).getStatus()).isEqualTo("WRITING");
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
                            + "PdcNfcWriteResultTransactionServiceImpl"))
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

        @Bean
        CancelJobReadGate cancelJobReadGate() {
            return new CancelJobReadGate();
        }

        @Bean
        PdcNfcWriteJobService writeJobService(
                PdcNfcWriteJobDao writeJobDao,
                PdcNfcWriteJobItemDao writeJobItemDao,
                PdcNfcAssetDao assetDao,
                PdcNfcBatchDao batchDao,
                PdcNfcOperationLogDao operationLogDao,
                PdcNfcBatchStateMachine batchStateMachine,
                PdcNfcWriteJobStateMachine writeJobStateMachine,
                CancelJobReadGate gate) {
            PdcNfcWriteJobDao gatedWriteJobDao =
                    (PdcNfcWriteJobDao) Proxy.newProxyInstance(
                            PdcNfcWriteJobDao.class.getClassLoader(),
                            new Class<?>[]{PdcNfcWriteJobDao.class},
                            (proxy, method, args) -> {
                                try {
                                    Object result = method.invoke(writeJobDao, args);
                                    if (method.getName().startsWith("selectById")) {
                                        gate.afterJobRead();
                                    }
                                    return result;
                                } catch (InvocationTargetException exception) {
                                    throw exception.getCause();
                                }
                            });
            return new PdcNfcWriteJobServiceImpl(
                    gatedWriteJobDao,
                    writeJobItemDao,
                    assetDao,
                    batchDao,
                    operationLogDao,
                    null,
                    batchStateMachine,
                    writeJobStateMachine,
                    null,
                    null);
        }
    }

    static final class CancelJobReadGate {

        private volatile boolean armed;
        private volatile CountDownLatch jobRead = new CountDownLatch(1);
        private volatile CountDownLatch allowCancel = new CountDownLatch(1);

        void arm() {
            jobRead = new CountDownLatch(1);
            allowCancel = new CountDownLatch(1);
            armed = true;
        }

        void afterJobRead() throws InterruptedException {
            if (armed && "nfc-cancel".equals(Thread.currentThread().getName())) {
                jobRead.countDown();
                allowCancel.await();
            }
        }

        boolean awaitJobRead(long timeout, TimeUnit unit) throws InterruptedException {
            return jobRead.await(timeout, unit);
        }

        void releaseCancel() {
            armed = false;
            allowCancel.countDown();
        }
    }
}
