package xiaozhi.modules.pdc.nfc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.crypto.RequestFingerprint;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAdminRequestDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcBulkAssetOperationDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcOperationLogQueryDTO;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAdminRequestEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcAdminIdempotencyServiceImpl;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcInventoryServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBulkOperationVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcOperationLogVO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PdcNfcInventoryService 库存流转服务测试")
class PdcNfcInventoryServiceTest {

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        lenient().when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        lenient().when(messageSource.getMessage(any(), any(), any(), any(java.util.Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock
    private PdcNfcAdminRequestDao adminRequestDao;
    @Mock
    private PdcNfcAssetDao assetDao;
    @Mock
    private PdcNfcOperationLogDao operationLogDao;
    @Mock
    private PdcNfcBatchDao batchDao;

    private PdcNfcAdminIdempotencyServiceImpl idempotencyService;
    private PdcNfcInventoryServiceImpl inventoryService;
    private PdcNfcProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RequestFingerprint requestFingerprint = new RequestFingerprint();
        objectMapper = new ObjectMapper();
        idempotencyService = new PdcNfcAdminIdempotencyServiceImpl(
                adminRequestDao, requestFingerprint, objectMapper);
        PdcNfcAssetStateMachine stateMachine = new PdcNfcAssetStateMachine();
        PdcNfcBatchStateMachine batchStateMachine = new PdcNfcBatchStateMachine();
        properties = new PdcNfcProperties();
        properties.setEnabled(true);
        properties.setReleaseReady(true);
        properties.setActivationEnabled(true);
        inventoryService = new PdcNfcInventoryServiceImpl(
                idempotencyService, assetDao, operationLogDao, batchDao,
                stateMachine, batchStateMachine, properties, objectMapper);
    }

    // --- 辅助方法 ---

    private PdcNfcAssetEntity createAsset(Long id, String assetNo, String status) {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(id);
        asset.setAssetNo(assetNo);
        asset.setStatus(status);
        asset.setVersion(0);
        return asset;
    }

    private PdcNfcBulkAssetOperationDTO createRequest(List<Long> assetIds, String businessNo, UUID requestId) {
        PdcNfcBulkAssetOperationDTO request = new PdcNfcBulkAssetOperationDTO();
        request.setAssetIds(assetIds);
        request.setBusinessNo(businessNo);
        request.setRequestId(requestId);
        return request;
    }

    private void stubIdempotencyFirstCall() {
        lenient().when(adminRequestDao.selectOne(any())).thenReturn(null);
    }

    // --- 测试用例 ---

    @Test
    @DisplayName("stockIn: VERIFIED 资产入库成功")
    void stockInSuccess() {
        PdcNfcAssetEntity asset1 = createAsset(1L, "A001", "VERIFIED");
        PdcNfcAssetEntity asset2 = createAsset(2L, "A002", "VERIFIED");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1, asset2));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L, 2L), "BN001", UUID.randomUUID());
        PdcNfcBulkOperationVO result = inventoryService.stockIn(request, 100L);

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(0);
        assertThat(result.businessNo()).isEqualTo("BN001");

        verify(assetDao, times(2)).updateById(any(PdcNfcAssetEntity.class));
        verify(operationLogDao, times(2)).insert(any(PdcNfcOperationLogEntity.class));

        // 验证资产状态已更新
        ArgumentCaptor<PdcNfcAssetEntity> captor = ArgumentCaptor.forClass(PdcNfcAssetEntity.class);
        verify(assetDao, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(a ->
                assertThat(a.getStatus()).isEqualTo("IN_STOCK"));
    }

    @Test
    @DisplayName("stockIn: 手动模式资产未锁卡拒绝入库")
    void stockInRejectsUnlockedManualAsset() {
        // ADR 0003：verify_source 非空 = 手动模式验证的资产，入库前必须已锁卡
        PdcNfcAssetEntity asset = createAsset(1L, "A001", "VERIFIED");
        asset.setVerifySource("TOUCH");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L), "BN001", UUID.randomUUID());
        assertThatThrownBy(() -> inventoryService.stockIn(request, 100L))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PDC_NFC_ASSET_NOT_LOCKED);

        verify(assetDao, never()).updateById(any(PdcNfcAssetEntity.class));
    }

    @Test
    @DisplayName("stockIn: 手动模式资产锁后未复验拒绝入库")
    void stockInRejectsManualAssetWithoutLockVerification() {
        PdcNfcAssetEntity asset = createAsset(1L, "A001", "VERIFIED");
        asset.setVerifySource("MANUAL");
        asset.setLockedAt(new Date());

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L), "BN001", UUID.randomUUID());
        assertThatThrownBy(() -> inventoryService.stockIn(request, 100L))
                .isInstanceOf(RenException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PDC_NFC_LOCK_NOT_VERIFIED);

        verify(assetDao, never()).updateById(any(PdcNfcAssetEntity.class));
    }

    @Test
    @DisplayName("stockIn: 手动模式资产锁卡且复验通过后允许入库")
    void stockInAllowsLockedAndReverifiedManualAsset() {
        PdcNfcAssetEntity asset = createAsset(1L, "A001", "VERIFIED");
        asset.setVerifySource("TOUCH");
        asset.setLockedAt(new Date());
        asset.setLockVerifiedAt(new Date());

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L), "BN001", UUID.randomUUID());
        PdcNfcBulkOperationVO result = inventoryService.stockIn(request, 100L);

        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("stockIn: 工厂模式资产（verify_source 为空）不受锁卡门禁影响")
    void stockInFactoryAssetBypassesLockGate() {
        PdcNfcAssetEntity asset = createAsset(1L, "A001", "VERIFIED");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L), "BN001", UUID.randomUUID());
        PdcNfcBulkOperationVO result = inventoryService.stockIn(request, 100L);

        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("activate: IN_STOCK 资产激活成功")
    void activateSuccess() {
        PdcNfcAssetEntity asset1 = createAsset(1L, "A001", "IN_STOCK");
        PdcNfcAssetEntity asset2 = createAsset(2L, "A002", "IN_STOCK");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1, asset2));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L, 2L), "BN002", UUID.randomUUID());
        PdcNfcBulkOperationVO result = inventoryService.activate(request, 100L);

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);

        ArgumentCaptor<PdcNfcAssetEntity> captor = ArgumentCaptor.forClass(PdcNfcAssetEntity.class);
        verify(assetDao, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(a -> {
            assertThat(a.getStatus()).isEqualTo("ACTIVE");
            assertThat(a.getActivationBusinessNo()).isEqualTo("BN002");
            assertThat(a.getActivatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("activate: 一个资产非 IN_STOCK 时全部回滚（all-or-nothing）")
    void activateRejectsWhenOneNotInStock() {
        PdcNfcAssetEntity asset1 = createAsset(1L, "A001", "IN_STOCK");
        PdcNfcAssetEntity asset2 = createAsset(2L, "A002", "ACTIVE");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1, asset2));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L, 2L), "BN003", UUID.randomUUID());

        assertThatThrownBy(() -> inventoryService.activate(request, 100L))
                .isInstanceOf(RenException.class);

        verify(assetDao, never()).updateById(any(PdcNfcAssetEntity.class));
        verify(operationLogDao, never()).insert(any(PdcNfcOperationLogEntity.class));
    }

    @Test
    @DisplayName("disable: CLAIMED 资产停用时保留 claimedUserId 和 petId")
    void disablePreservesClaimedInfo() {
        PdcNfcAssetEntity asset = createAsset(1L, "A001", "CLAIMED");
        asset.setClaimedUserId(999L);
        asset.setPetId("pet-123");
        asset.setClaimedAt(new java.util.Date());

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L), "BN004", UUID.randomUUID());
        PdcNfcBulkOperationVO result = inventoryService.disable(request, 100L);

        assertThat(result.successCount()).isEqualTo(1);

        ArgumentCaptor<PdcNfcAssetEntity> captor = ArgumentCaptor.forClass(PdcNfcAssetEntity.class);
        verify(assetDao).updateById(captor.capture());
        PdcNfcAssetEntity updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("DISABLED");
        assertThat(updated.getClaimedUserId()).isEqualTo(999L);
        assertThat(updated.getPetId()).isEqualTo("pet-123");
        assertThat(updated.getDisabledAt()).isNotNull();
    }

    @Test
    @DisplayName("scrap: ACTIVE 资产不可作废")
    void scrapRejectsActiveAssets() {
        PdcNfcAssetEntity asset = createAsset(1L, "A001", "ACTIVE");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L), "BN005", UUID.randomUUID());

        assertThatThrownBy(() -> inventoryService.scrap(request, 100L))
                .isInstanceOf(RenException.class);

        verify(assetDao, never()).updateById(any(PdcNfcAssetEntity.class));
    }

    @Test
    @DisplayName("重复 assetId 被拒绝")
    void duplicateAssetIdsRejected() {
        PdcNfcBulkAssetOperationDTO request = createRequest(
                List.of(1L, 1L), "BN006", UUID.randomUUID());

        assertThatThrownBy(() -> inventoryService.stockIn(request, 100L))
                .isInstanceOf(RenException.class);

        verify(assetDao, never()).selectByIdsForUpdate(any());
    }

    @Test
    @DisplayName("超过 500 个 assetId 被拒绝")
    void tooManyAssetsRejected() {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 501; i++) {
            ids.add((long) i);
        }
        PdcNfcBulkAssetOperationDTO request = createRequest(ids, "BN007", UUID.randomUUID());

        assertThatThrownBy(() -> inventoryService.stockIn(request, 100L))
                .isInstanceOf(RenException.class);

        verify(assetDao, never()).selectByIdsForUpdate(any());
    }

    @Test
    @DisplayName("幂等重放返回相同响应")
    void idempotentReplayReturnsSameResponse() throws Exception {
        PdcNfcAssetEntity asset = createAsset(1L, "A001", "VERIFIED");

        UUID requestId = UUID.randomUUID();
        PdcNfcBulkAssetOperationDTO request = createRequest(List.of(1L), "BN008", requestId);

        // 计算与实现中相同的 canonical request 和 fingerprint
        String canonical = "BN008:" + request.getAssetIds().stream()
                .sorted().map(String::valueOf).collect(Collectors.joining(","));
        RequestFingerprint fp = new RequestFingerprint();
        String fingerprint = fp.sha256Canonical(canonical);

        // 构造缓存的幂等记录
        PdcNfcBulkOperationVO cachedVO = new PdcNfcBulkOperationVO(1, 1, 0, "BN008", requestId);
        PdcNfcAdminRequestEntity existing = new PdcNfcAdminRequestEntity();
        existing.setOperationType("STOCK_IN");
        existing.setRequestId(requestId.toString());
        existing.setRequestFingerprint(fingerprint);
        existing.setResponseJson(objectMapper.writeValueAsString(cachedVO));

        // 第一次 selectOne → null（无已有记录），第二次 → existing（缓存命中）
        when(adminRequestDao.selectOne(any())).thenReturn(null).thenReturn(existing);
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset));

        // 第一次调用：执行业务逻辑
        PdcNfcBulkOperationVO result1 = inventoryService.stockIn(request, 100L);

        // 第二次调用：幂等重放
        PdcNfcBulkOperationVO result2 = inventoryService.stockIn(request, 100L);

        // 两次返回的结果相同
        assertThat(result1.businessNo()).isEqualTo("BN008");
        assertThat(result2.businessNo()).isEqualTo("BN008");
        assertThat(result1.processedCount()).isEqualTo(result2.processedCount());
        assertThat(result1.successCount()).isEqualTo(result2.successCount());
        assertThat(result1.requestId()).isEqualTo(result2.requestId());

        // 业务逻辑只执行一次
        verify(assetDao, times(1)).selectByIdsForUpdate(any());
        verify(assetDao, times(1)).updateById(any(PdcNfcAssetEntity.class));
    }

    @Test
    @DisplayName("stockIn: 最后一个 VERIFIED 资产入库后批次进入 COMPLETED")
    void stockInCompletesBatchWhenLastVerifiedAssetStocked() {
        PdcNfcAssetEntity asset1 = createAsset(1L, "A001", "VERIFIED");
        PdcNfcAssetEntity asset2 = createAsset(2L, "A002", "VERIFIED");
        asset1.setBatchId(10L);
        asset2.setBatchId(10L);
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setStatus("READY_FOR_STOCK");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1, asset2));
        when(assetDao.selectById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            PdcNfcAssetEntity stocked = new PdcNfcAssetEntity();
            stocked.setId(id);
            stocked.setBatchId(10L);
            stocked.setStatus("IN_STOCK");
            return stocked;
        });
        when(batchDao.selectById(10L)).thenReturn(batch);
        when(assetDao.countByBatchIdAndStatus(10L, "VERIFIED")).thenReturn(0);
        when(batchDao.updateById(any(PdcNfcBatchEntity.class))).thenReturn(1);

        inventoryService.stockIn(
                createRequest(List.of(1L, 2L), "BN009", UUID.randomUUID()), 100L);

        ArgumentCaptor<PdcNfcBatchEntity> batchCaptor =
                ArgumentCaptor.forClass(PdcNfcBatchEntity.class);
        verify(batchDao).updateById(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("stockIn: 仍有 VERIFIED 资产时批次保持 READY_FOR_STOCK")
    void stockInKeepsReadyForStockWhileVerifiedAssetsRemain() {
        PdcNfcAssetEntity asset1 = createAsset(1L, "A001", "VERIFIED");
        asset1.setBatchId(10L);
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setStatus("READY_FOR_STOCK");

        stubIdempotencyFirstCall();
        when(assetDao.selectByIdsForUpdate(any())).thenReturn(List.of(asset1));
        when(assetDao.selectById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            PdcNfcAssetEntity stocked = new PdcNfcAssetEntity();
            stocked.setId(id);
            stocked.setBatchId(10L);
            stocked.setStatus("IN_STOCK");
            return stocked;
        });
        lenient().when(batchDao.selectById(10L)).thenReturn(batch);
        lenient().when(assetDao.countByBatchIdAndStatus(10L, "VERIFIED")).thenReturn(1);

        inventoryService.stockIn(
                createRequest(List.of(1L), "BN010", UUID.randomUUID()), 100L);

        verify(batchDao, never()).updateById(any(PdcNfcBatchEntity.class));
    }

    @Test
    @DisplayName("queryOperationLogs: VO 携带变更前后状态（前端展开面板依赖）")
    void queryOperationLogsMapsBeforeAfterStatus() {
        // Arrange
        PdcNfcOperationLogEntity entity = new PdcNfcOperationLogEntity();
        entity.setId(1L);
        entity.setObjectType("ASSET");
        entity.setObjectId(10L);
        entity.setOperationType("ACTIVATE");
        entity.setOperatorUserId(100L);
        entity.setBeforeStatus("IN_STOCK");
        entity.setAfterStatus("ACTIVE");
        entity.setCreateDate(new Date());

        Page<PdcNfcOperationLogEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(operationLogDao.selectPage(any(), any())).thenReturn(page);

        // Act
        PageData<PdcNfcOperationLogVO> result =
                inventoryService.queryOperationLogs(new PdcNfcOperationLogQueryDTO());

        // Assert
        assertThat(result.getList()).hasSize(1);
        PdcNfcOperationLogVO vo = result.getList().get(0);
        assertThat(vo.beforeStatus()).isEqualTo("IN_STOCK");
        assertThat(vo.afterStatus()).isEqualTo("ACTIVE");
    }
}
