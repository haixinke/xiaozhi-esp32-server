package xiaozhi.modules.pdc.nfc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.MessageUtils;
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

    @BeforeAll
    static void initMessageSource() throws Exception {
        MessageSource mockSource = mock(MessageSource.class);
        lenient().when(mockSource.getMessage(anyString(),
                org.mockito.ArgumentMatchers.any(),
                anyString(),
                org.mockito.ArgumentMatchers.any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        Field field = MessageUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockSource);
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
        // 每个用例清场：只清本测试用到的三张表
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

    /** 插入一只未删除宠物；todayMood 置为已生成，跳过 listByUserId 的今日心情懒刷新（不依赖 LLM mock） */
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
        pet.setTodayMood("平静");
        pet.setTodayMoodDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
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
    @Import({PdcNfcClaimServiceImpl.class})
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
            WechatPhoneGate gate = mock(WechatPhoneGate.class);
            lenient().when(gate.hasBoundWechatPhone(org.mockito.ArgumentMatchers.anyLong()))
                    .thenReturn(true);
            return gate;
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

        /**
         * 真实 PetServiceImpl：只有 PetDao 用真实实例（事务语义是本次测试目标），
         * 其余依赖全部 mock——createEgg/deleteByUserId/listByUserId 路径不触达它们
         * （今日心情懒刷新已由测试数据的 todayMood 置为已生成而跳过）。
         */
        @Bean
        @Primary
        PetService petService(PetDao petDao, WechatPhoneGate wechatPhoneGate,
                              PetCollectionCardService petCollectionCardService) {
            return new PetServiceImpl(petDao, null, null, null, null, null, null, null,
                    null, null, null, null, petCollectionCardService, null, wechatPhoneGate, null);
        }
    }
}
