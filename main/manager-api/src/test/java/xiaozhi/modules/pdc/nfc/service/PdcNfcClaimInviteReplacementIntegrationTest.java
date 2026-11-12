package xiaozhi.modules.pdc.nfc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.dailynote.service.CompanionDaysService;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcClaimRecordDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcClaimRecordEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcClaimServiceImpl;
import xiaozhi.modules.pdc.nfc.support.MySqlContainerSupport;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimResultVO;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.PetCollectionCardService;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.service.impl.PetServiceImpl;
import xiaozhi.modules.pet.service.impl.PetAdoptionModule;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.wechat.service.WechatPhoneGate;

/**
 * 渠道互斥（ADR 0007）真实事务集成测试：NFC 领养成功同事务静默逻辑删除邀请码宠物。
 * 基建与 PdcNfcTrustReleaseIntegrationTest 同一 seam：Testcontainers MySQL + 真实事务，
 * schema 由 Liquibase 全量变更日志构建（覆盖 ai_pet 最新唯一索引 uk_ai_pet_user_prototype_deleted）。
 */
@SpringJUnitConfig(PdcNfcClaimInviteReplacementIntegrationTest.TestConfig.class)
@DisplayName("NFC 领取渠道互斥真实事务集成测试")
class PdcNfcClaimInviteReplacementIntegrationTest extends MySqlContainerSupport {

    private static final Long USER_ID = 1001L;
    private static final Long ASSET_ID = 9001L;
    private static final String VALID_CLAIM_REF = "abcdefghij1234567890_-";
    private static final String CLAIM_HASH = "f".repeat(64);
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Autowired private DataSource dataSource;
    @Autowired private PdcNfcAssetDao assetDao;
    @Autowired private PdcNfcBatchDao batchDao;
    @Autowired private PdcNfcClaimRecordDao claimRecordDao;
    @Autowired private PetDao petDao;
    @Autowired private PdcNfcClaimService claimService;
    @Autowired private LLMService llmService;
    @Autowired private PetAdoptionModule adoptionModule;
    @Autowired private PlatformTransactionManager transactionManager;

    // MessageUtils 现从 SpringContextUtils 现取 messageSource，改为挂 mock ApplicationContext
    @BeforeAll
    static void initMessageSource() {
        MessageSourceMockSupport.install();
    }

    /** schema 只建一次（静态标记），用例间只清业务行 */
    private static boolean schemaInitialized = false;

    @BeforeEach
    void setUpDatabase() {
        if (!schemaInitialized) {
            // ai_pet 演进链（12 个文件，含最新唯一索引 uk_ai_pet_user_prototype_deleted）
            // + pdc_nfc 链（3 个文件）。全量 Liquibase 在 MySQL 8.4 上会被
            // 202607161200（ai_pet_collection_card 键长超 3072 字节，生产 OceanBase 无此限制）阻断，
            // 故沿用 TrustRelease 集成测试的手动 populator seam，只取本链路需要的文件。
            new ResourceDatabasePopulator(
                    new ClassPathResource("db/changelog/202605071500.sql"),
                    new ClassPathResource("db/changelog/202605091600.sql"),
                    new ClassPathResource("db/changelog/202605091700.sql"),
                    new ClassPathResource("db/changelog/202607101030.sql"),
                    new ClassPathResource("db/changelog/202607101500.sql"),
                    new ClassPathResource("db/changelog/202607111000.sql"),
                    new ClassPathResource("db/changelog/202607111200.sql"),
                    new ClassPathResource("db/changelog/202607161500.sql"),
                    new ClassPathResource("db/changelog/202608061500.sql"),
                    new ClassPathResource("db/changelog/202610011200.sql"),
                    new ClassPathResource("db/changelog/202610021000.sql"),
                    new ClassPathResource("db/changelog/202610041200.sql"),
                    new ClassPathResource("db/changelog/202607291000.sql"),
                    new ClassPathResource("db/changelog/202607301000.sql"),
                    new ClassPathResource("db/changelog/202608271000.sql"))
                    .execute(dataSource);
            // 资产表 batch_id 非空，批次属静态基础数据，随 schema 一次性插入
            PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
            batch.setId(1L);
            batch.setBatchNo("B-901");
            batch.setProductTypeId(1L);
            batch.setSkuCode("SKU-RABBIT");
            batch.setPrototype("玉兔");
            batch.setPlannedQuantity(1);
            batch.setStatus("ON_SALE");
            batch.setCreateDate(new Date());
            batchDao.insert(batch);
            schemaInitialized = true;
        }
        // 用户行锁与生产共用 sys_user 主键；此隔离 schema 只需该列。
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT IGNORE INTO sys_user(id) VALUES (?)", USER_ID);
        reset(llmService);
        // 每个用例只清本测试使用的业务行
        petDao.delete(new QueryWrapper<PetEntity>().eq("user_id", USER_ID));
        claimRecordDao.delete(new QueryWrapper<PdcNfcClaimRecordEntity>()
                .eq("asset_id", ASSET_ID));
        assetDao.deleteById(ASSET_ID);
        insertActiveAsset("玉兔");
    }

    @Test
    @DisplayName("有邀请码宠物时 confirm 成功：邀请码宠物逻辑删除 + NFC 宠物创建 + 领取记录 + 资产消耗，同事务成立")
    void confirmWithInvitePetReplacesItAtomically() {
        // 事务代理必须生效，否则回滚语义不成立
        assertThat(AopUtils.isAopProxy(claimService)).isTrue();
        insertPet("pet-invite-yt", "玉兔", "INVITE_CODE");

        PdcNfcClaimResultVO result = claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID);

        assertThat(result.claimStatus()).isEqualTo("CLAIMED");
        assertThat(result.replacedInvitePet()).isTrue();

        // 邀请码宠物逻辑删除：行保留、deleted_at>0
        PetEntity invitePet = petDao.selectById("pet-invite-yt");
        assertThat(invitePet.getDeletedAt()).isGreaterThan(0L);
        // 今日心情故意缺失，领取只能变更领养事实，不能因展示补齐而发出外部调用。
        assertThat(invitePet.getTodayMood()).isNull();
        assertThat(invitePet.getTodayMoodDate()).isNull();
        verifyNoInteractions(llmService);

        // NFC 宠物已建：source=NFC、未删除
        List<PetEntity> alivePets = petDao.selectList(new QueryWrapper<PetEntity>()
                .eq("user_id", USER_ID).eq("deleted_at", 0));
        assertThat(alivePets).hasSize(1);
        assertThat(alivePets.get(0).getSource()).isEqualTo("NFC");
        assertThat(alivePets.get(0).getPrototype()).isEqualTo("玉兔");

        // 领取记录已写、资产已消耗
        assertThat(claimRecordDao.selectCount(new QueryWrapper<PdcNfcClaimRecordEntity>()
                .eq("asset_id", ASSET_ID).eq("user_id", USER_ID))).isEqualTo(1);
        PdcNfcAssetEntity asset = assetDao.selectById(ASSET_ID);
        assertThat(asset.getStatus()).isEqualTo(PdcNfcAssetStatus.CLAIMED.name());
        assertThat(asset.getClaimedUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("confirm 失败回滚：已有同原型 NFC 宠物时 confirm 抛 10206，无任何数据变动、资产不消耗")
    void confirmFailureRollsBackAllChanges() {
        assertThat(AopUtils.isAopProxy(claimService)).isTrue();
        // 用户已有同原型 NFC 宠物（合法存量形态：preview 会提前拦截，confirm 兜底）。
        // confirm 走到 createEgg 时撞唯一索引 uk(user_id, prototype, deleted_at) 抛 10206，
        // 验证失败路径无任何数据变动。
        insertPet("pet-nfc-yt", "玉兔", "NFC");

        assertThatThrownBy(() -> claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_ALREADY_EXISTS));

        // 原有宠物原封不动：未删除、无新行
        assertThat(petDao.selectById("pet-nfc-yt").getDeletedAt()).isEqualTo(0L);
        assertThat(petDao.selectCount(new QueryWrapper<PetEntity>().eq("user_id", USER_ID)))
                .isEqualTo(1);
        // 无领取记录、资产仍 ACTIVE 可给他人领取
        assertThat(claimRecordDao.selectCount(new QueryWrapper<PdcNfcClaimRecordEntity>()
                .eq("asset_id", ASSET_ID))).isZero();
        assertThat(assetDao.selectById(ASSET_ID).getStatus())
                .isEqualTo(PdcNfcAssetStatus.ACTIVE.name());
    }

    @Test
    @DisplayName("心情缺失的已有宠物：本人卡与幂等回放均返回存量数据，不调用 LLM")
    void replayAndClaimedBySelfNeverRefreshMissingMood() {
        PdcNfcClaimResultVO first = claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID);
        String petId = ((xiaozhi.modules.pet.vo.PetVO) first.pet()).getId();

        PdcNfcClaimResultVO replay = claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID);
        PdcNfcClaimResultVO self = claimService.confirm(USER_ID, VALID_CLAIM_REF, UUID.randomUUID());

        assertThat(((xiaozhi.modules.pet.vo.PetVO) replay.pet()).getId()).isEqualTo(petId);
        assertThat(((xiaozhi.modules.pet.vo.PetVO) self.pet()).getId()).isEqualTo(petId);
        assertThat(replay.replacedInvitePet()).isFalse();
        assertThat(self.claimStatus()).isEqualTo("CLAIMED_BY_SELF");
        assertThat(petDao.selectById(petId).getTodayMoodDate()).isNull();
        verifyNoInteractions(llmService);
    }

    @Test
    @DisplayName("预览重复原型时不刷新心情且不消耗资产")
    void previewOfOwnedPrototypeKeepsStoredMood() {
        insertPet("pet-owned-missing-mood", "玉兔", "NFC");

        var result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo("ALREADY_OWNED");
        assertThat(petDao.selectById("pet-owned-missing-mood").getTodayMoodDate()).isNull();
        assertThat(assetDao.selectById(ASSET_ID).getStatus()).isEqualTo("ACTIVE");
        verifyNoInteractions(llmService);
    }

    @Test
    @DisplayName("领取记录写入失败：邀请码替换与新蛋一起回滚")
    void claimRecordFailureRestoresReplacedInvitePet() {
        insertPet("pet-invite-rollback", "玉兔", "INVITE_CODE");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 在真实数据库中拒绝领取记录，验证此前完成的宠物变更也受同一事务保护。
        jdbc.execute("CREATE TRIGGER reject_test_claim BEFORE INSERT ON pdc_nfc_claim_record "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test claim failure'");
        try {
            assertThatThrownBy(() -> claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID))
                    .isInstanceOf(RuntimeException.class);
            assertThat(petDao.selectById("pet-invite-rollback").getDeletedAt()).isZero();
            assertThat(petDao.selectCount(new QueryWrapper<PetEntity>().eq("user_id", USER_ID)))
                    .isEqualTo(1);
            assertThat(claimRecordDao.selectCount(new QueryWrapper<PdcNfcClaimRecordEntity>()
                    .eq("asset_id", ASSET_ID))).isZero();
            assertThat(assetDao.selectById(ASSET_ID).getStatus()).isEqualTo("ACTIVE");
            verifyNoInteractions(llmService);
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_claim");
        }
    }

    @Test
    @DisplayName("跨渠道并发：NFC 在旧快照下等待邀请码提交，仍须替换后只保留 NFC 宠物")
    void concurrentChannelsUseCurrentReadAfterUserLock() throws Exception {
        CountDownLatch inviteWritten = new CountDownLatch(1);
        CountDownLatch nfcSnapshotReady = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate inviteTx = new TransactionTemplate(transactionManager);
        TransactionTemplate nfcTx = new TransactionTemplate(transactionManager);
        nfcTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            var invite = executor.submit(() -> inviteTx.execute(status -> {
                PetEntity pet = adoptionModule.createInviteEgg(USER_ID, "锦鲤");
                inviteWritten.countDown();
                await(nfcSnapshotReady);
                return pet;
            }));
            var nfc = executor.submit(() -> {
                await(inviteWritten);
                return nfcTx.execute(status -> {
                    // 已插入的邀请码尚未提交：先建立不含该行的 RR 快照，再竞争同一个用户锁。
                    assertThat(petDao.selectCount(new QueryWrapper<PetEntity>().eq("user_id", USER_ID)))
                            .isZero();
                    nfcSnapshotReady.countDown();
                    return adoptionModule.claimNfcEgg(USER_ID, "玉兔");
                });
            });
            PetEntity invitePet = invite.get(20, TimeUnit.SECONDS);
            PetAdoptionModule.NfcEgg outcome = nfc.get(20, TimeUnit.SECONDS);

            assertThat(outcome.replacedInvitePet()).isTrue();
            assertThat(petDao.selectById(invitePet.getId()).getDeletedAt()).isPositive();
            List<PetEntity> active = petDao.selectList(new QueryWrapper<PetEntity>()
                    .eq("user_id", USER_ID).eq("deleted_at", 0));
            assertThat(active).hasSize(1);
            assertThat(active.get(0).getSource()).isEqualTo("NFC");
            assertThat(active.get(0).getPrototype()).isEqualTo("玉兔");
            verifyNoInteractions(llmService);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 并发测试只等待明确事件，超时立即失败，避免失去因果关系的固定 sleep。 */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("adoption transaction coordination timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("adoption transaction interrupted", e);
        }
    }

    /** 插入心情尚未生成的未删除宠物，确保领取无需展示路径也能完成。 */
    private void insertPet(String id, String prototype, String source) {
        PetEntity pet = new PetEntity();
        pet.setId(id);
        pet.setUserId(USER_ID);
        pet.setPrototype(prototype);
        pet.setSource(source);
        pet.setHatchStatus("EGG");
        pet.setDeletedAt(0L);
        pet.setAcceleratedMinutes(0);
        pet.setHatchStartTime(new Date());
        pet.setExpectedHatchTime(new Date());
        pet.setCreator(USER_ID);
        pet.setCreateDate(new Date());
        petDao.insert(pet);
    }

    /** 插入一张 ACTIVE 态 NFC 资产（字段集与 PdcNfcTrustReleaseIntegrationTest 同模式） */
    private void insertActiveAsset(String prototype) {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(ASSET_ID);
        asset.setAssetNo("A-9001");
        asset.setBatchId(1L);
        asset.setItemNo("900001");
        asset.setSkuCode("SKU-RABBIT");
        asset.setPrototype(prototype);
        asset.setWechatSn("SN-9001");
        asset.setClaimRefHash(CLAIM_HASH);
        asset.setClaimRefHashVersion("v1");
        asset.setClaimRefKeyVersion("v1");
        asset.setClaimRefNonce(new byte[12]);
        asset.setClaimRefCiphertext(new byte[32]);
        asset.setSchemeSha256("c".repeat(64));
        asset.setStatus(PdcNfcAssetStatus.ACTIVE.name());
        asset.setVersion(1);
        asset.setCreateDate(new Date());
        assetDao.insert(asset);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan({"xiaozhi.modules.pdc.nfc.dao", "xiaozhi.modules.pet.dao"})
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    @Import({PdcNfcClaimServiceImpl.class, PetAdoptionModule.class})
    static class TestConfig {

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            return interceptor;
        }

        @Bean
        PdcNfcProperties pdcNfcProperties() {
            PdcNfcProperties properties = mock(PdcNfcProperties.class);
            lenient().when(properties.isEnabled()).thenReturn(true);
            lenient().when(properties.isClaimEnabled()).thenReturn(true);
            lenient().when(properties.isReleaseReady()).thenReturn(true);
            return properties;
        }

        @Bean
        WechatPhoneGate wechatPhoneGate() {
            // 领取链路已不再校验手机号，保留 mock 仅满足 PetServiceImpl 的构造依赖。
            return mock(WechatPhoneGate.class);
        }

        @Bean
        ClaimRefProtection claimRefProtection() {
            ClaimRefProtection protection = mock(ClaimRefProtection.class);
            lenient().when(protection.lookupHashes(anyString())).thenReturn(List.of(CLAIM_HASH));
            return protection;
        }

        @Bean
        PdcNfcClaimRateLimiter pdcNfcClaimRateLimiter() {
            // 限流器依赖 Redis，本测试不覆盖限流语义，全部放行
            return mock(PdcNfcClaimRateLimiter.class);
        }

        @Bean
        PdcNfcManualWriteService pdcNfcManualWriteService() {
            // 手动写卡触碰自验证只在 preview 链路，confirm 用不到
            return mock(PdcNfcManualWriteService.class);
        }

        @Bean
        PetCollectionCardService petCollectionCardService() {
            PetCollectionCardService service = mock(PetCollectionCardService.class);
            lenient().when(service.listByPetId(anyString())).thenReturn(List.of());
            return service;
        }

        /** LLM 是外部依赖，测试通过零交互断言阻止领取触发展示生成。 */
        @Bean
        LLMService llmService() {
            return mock(LLMService.class);
        }

        /** 真实宠物与领养 implementation，共享隔离数据库及 Spring 事务代理。 */
        @Bean
        @Primary
        PetService petService(PetDao petDao, WechatPhoneGate wechatPhoneGate,
                              PetCollectionCardService petCollectionCardService,
                              PetAdoptionModule adoptionModule, LLMService llmService) {
            // toVO 会调用 companionDays，NPE 防护：mock 返回 0
            CompanionDaysService companionDaysService = mock(CompanionDaysService.class);
            return new PetServiceImpl(petDao, null, llmService, null, null, null, null, null,
                    null, null, null, null, petCollectionCardService, null, wechatPhoneGate, null,
                    companionDaysService, adoptionModule);
        }
    }
}
