package xiaozhi.modules.pdc.nfc.service;

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
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.crypto.EncryptedField;
import xiaozhi.modules.pdc.nfc.crypto.PdcNfcIdentifierGenerator;
import xiaozhi.modules.pdc.nfc.crypto.ProtectedClaimRef;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcSchemeJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dto.CreatePdcNfcBatchDTO;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcBatchServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBatchVO;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PdcNfcBatchService 批次创建测试")
class PdcNfcBatchServiceTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PdcNfcBatchDao batchDao;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcProductTypeDao productTypeDao;
    @Mock private PdcNfcSchemeJobDao schemeJobDao;
    @Mock private PdcNfcWriteJobDao writeJobDao;
    @Mock private PdcNfcBatchStateMachine batchStateMachine;

    private PdcNfcProperties properties;
    private PdcNfcIdentifierGenerator identifiers;
    private ClaimRefProtection claimRefs;
    private PdcNfcBatchServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new PdcNfcProperties();
        properties.setEnabled(true);
        properties.setMaxBatchQuantity(10000);
        PdcNfcProperties.ClaimRef cr = new PdcNfcProperties.ClaimRef();
        cr.setActiveVersion("v1");
        cr.setActiveHmacKeyBase64("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=");
        cr.setActiveAesKeyBase64("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        properties.setClaimRef(cr);

        identifiers = new PdcNfcIdentifierGenerator();
        claimRefs = new ClaimRefProtection(properties);

        service = new PdcNfcBatchServiceImpl(
                batchDao, assetDao, productTypeDao, schemeJobDao, writeJobDao, properties,
                identifiers, claimRefs, batchStateMachine);

        // 默认商品类型存在
        PdcNfcProductTypeEntity pt = new PdcNfcProductTypeEntity();
        pt.setId(1L);
        pt.setTypeCode("EGG_BABY_NFC");
        pt.setTypeName("蛋宝宝NFC");
        when(productTypeDao.selectById(1L)).thenReturn(pt);
    }

    @Test
    @DisplayName("创建批次 - 原子分配所有资产")
    void createAllocatesEveryAssetWithFixedPrototypeInOneTransaction() {
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B20260729001", 1L, "SKU-KOI", "锦鲤", 3, "试产");

        PdcNfcBatchVO result = service.create(dto, 7L);

        verify(batchDao).insert((PdcNfcBatchEntity) argThat((PdcNfcBatchEntity batch) -> batch.getPlannedQuantity() == 3));
        verify(assetDao).insertBatch(argThat(assets ->
                ((Collection<?>) assets).size() == 3
                && ((Collection<PdcNfcAssetEntity>) assets).stream().allMatch(a -> "锦鲤".equals(a.getPrototype()))
                && ((Collection<PdcNfcAssetEntity>) assets).stream().map(PdcNfcAssetEntity::getWechatSn).distinct().count() == 3));
        assertThat(result.assetCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("功能关闭 - 拒绝创建")
    void featureDisabledRejects() {
        properties.setEnabled(false);
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B001", 1L, "SKU", "锦鲤", 1, null);

        assertThatThrownBy(() -> service.create(dto, 7L))
                .isInstanceOf(RenException.class);
        verify(batchDao, never()).insert(any(PdcNfcBatchEntity.class));
    }

    @Test
    @DisplayName("数量 0 - 拒绝")
    void quantityZeroRejected() {
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B002", 1L, "SKU", "锦鲤", 0, null);

        // @Min 校验在 DTO 层，但服务层也校验 maxBatchQuantity
        // 实际运行时 DTO 校验会先拦截，这里测试服务层
        assertThatThrownBy(() -> service.create(dto, 7L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("数量超限 - 拒绝")
    void quantityExceedsMaxRejected() {
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B003", 1L, "SKU", "锦鲤", 10001, null);

        assertThatThrownBy(() -> service.create(dto, 7L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("非法原型 - 拒绝")
    void invalidPrototypeRejected() {
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B004", 1L, "SKU", "恐龙", 3, null);

        assertThatThrownBy(() -> service.create(dto, 7L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("重复批次号 - 拒绝")
    void duplicateBatchNoRejected() {
        when(batchDao.selectCount(any())).thenReturn(1L);
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B-DUP", 1L, "SKU", "锦鲤", 1, null);

        assertThatThrownBy(() -> service.create(dto, 7L))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("资产 assetNo 格式为 batchNo-六位itemNo")
    void assetNoFormatCorrect() {
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B-FMT", 1L, "SKU", "玉兔", 2, null);

        service.create(dto, 7L);

        verify(assetDao).insertBatch(argThat(assets -> {
            @SuppressWarnings("unchecked")
            Collection<PdcNfcAssetEntity> list = (Collection<PdcNfcAssetEntity>) assets;
            return list.stream().anyMatch(a -> "B-FMT-000001".equals(a.getAssetNo()))
                    && list.stream().anyMatch(a -> "B-FMT-000002".equals(a.getAssetNo()));
        }));
    }

    @Test
    @DisplayName("资产状态为 CREATED")
    void assetStatusCreated() {
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B-STATUS", 1L, "SKU", "锦鲤", 1, null);

        service.create(dto, 7L);

        verify(assetDao).insertBatch(argThat(assets -> {
            @SuppressWarnings("unchecked")
            Collection<PdcNfcAssetEntity> list = (Collection<PdcNfcAssetEntity>) assets;
            return list.stream().allMatch(a -> "CREATED".equals(a.getStatus()));
        }));
    }

    @Test
    @DisplayName("数据库不包含明文 claimRef")
    void noPlaintextClaimRefInDatabase() {
        CreatePdcNfcBatchDTO dto =
                new CreatePdcNfcBatchDTO("B-ENCRYPT", 1L, "SKU", "锦鲤", 1, null);

        service.create(dto, 7L);

        verify(assetDao).insertBatch(argThat(assets -> {
            @SuppressWarnings("unchecked")
            Collection<PdcNfcAssetEntity> list = (Collection<PdcNfcAssetEntity>) assets;
            return list.stream().allMatch(a ->
                    a.getClaimRefHash() != null
                    && a.getClaimRefCiphertext() != null
                    && a.getClaimRefNonce() != null);
        }));
    }

    @Test
    @DisplayName("批次列表填充最新 Scheme/写卡任务字段")
    void listPopulatesLatestJobFields() {
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setBatchNo("B-LIST");
        batch.setStatus("SCHEME_GENERATING");
        when(batchDao.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(batch));
        when(assetDao.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(5L);

        PdcNfcSchemeJobEntity schemeJob = new PdcNfcSchemeJobEntity();
        schemeJob.setId(777L);
        when(schemeJobDao.selectLatestByBatchId(10L)).thenReturn(schemeJob);

        PdcNfcWriteJobEntity writeJob = new PdcNfcWriteJobEntity();
        writeJob.setId(888L);
        writeJob.setStatus("EXPORTED");
        when(writeJobDao.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(writeJob);

        List<PdcNfcBatchVO> result = service.list(null);

        assertThat(result).hasSize(1);
        PdcNfcBatchVO vo = result.get(0);
        assertThat(vo.assetCount()).isEqualTo(5);
        assertThat(vo.schemeJobId()).isEqualTo(777L);
        assertThat(vo.writeJobId()).isEqualTo(888L);
        assertThat(vo.writeJobStatus()).isEqualTo("EXPORTED");
    }

    @Test
    @DisplayName("批次列表无任务时任务字段为 null")
    void listLeavesJobFieldsNullWhenNoJobs() {
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(11L);
        batch.setBatchNo("B-EMPTY");
        batch.setStatus("DRAFT");
        when(batchDao.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(batch));
        when(assetDao.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0L);
        when(schemeJobDao.selectLatestByBatchId(11L)).thenReturn(null);
        when(writeJobDao.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(null);

        List<PdcNfcBatchVO> result = service.list(null);

        assertThat(result).hasSize(1);
        PdcNfcBatchVO vo = result.get(0);
        assertThat(vo.schemeJobId()).isNull();
        assertThat(vo.writeJobId()).isNull();
        assertThat(vo.writeJobStatus()).isNull();
    }
}
