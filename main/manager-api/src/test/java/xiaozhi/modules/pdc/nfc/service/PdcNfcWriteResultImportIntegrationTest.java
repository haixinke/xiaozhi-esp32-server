package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
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
import xiaozhi.modules.pdc.nfc.support.MySqlContainerSupport;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
    @Autowired private PdcNfcBatchDao batchDao;
    @Autowired private PdcNfcAssetDao assetDao;
    @Autowired private PdcNfcWriteJobDao writeJobDao;
    @Autowired private PdcNfcWriteJobItemDao writeJobItemDao;
    @Autowired private PdcNfcWriteRecordDao writeRecordDao;
    @Autowired private PdcNfcOperationLogDao operationLogDao;

    @BeforeEach
    void setUpDatabase() {
        new ResourceDatabasePopulator(
                new ClassPathResource("db/changelog/202607291000.sql"))
                .execute(dataSource);
        insertBatch();
        insertAsset(FIRST_ASSET_ID, "A-001", "000001", "SN-001", "1".repeat(64));
        insertAsset(SECOND_ASSET_ID, "A-002", "000002", "SN-002", "2".repeat(64));
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

    private void insertAsset(Long id, String assetNo, String itemNo,
                             String wechatSn, String claimHash) {
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

    private void createSecondRecordFailureTrigger() {
        try (var connection = dataSource.getConnection();
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
                ""
        );
        return new ValidatedWriteResult(
                row,
                writeJobItemDao.selectById(itemId),
                assetDao.selectById(assetId),
                PdcNfcAssetStatus.VERIFIED,
                true
        );
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
    }
}
