package xiaozhi.modules.pdc.nfc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAdminOperationType;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcAssetQueryDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcBulkAssetOperationDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcOperationLogQueryDTO;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAdminIdempotencyService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAssetStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcInventoryService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcAssetVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBulkOperationVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcOperationLogVO;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.*;

/**
 * NFC 库存流转服务实现。
 * <p>
 * 所有批量写操作通过 {@link PdcNfcAdminIdempotencyService} 保证幂等。
 * 事务边界在公开方法上，包含幂等记录查找、业务执行、幂等记录存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcInventoryServiceImpl implements PdcNfcInventoryService {

    private static final int MAX_BULK_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> DETAIL_JSON_ALLOWLIST = Set.of(
            "assetNo", "beforeStatus", "afterStatus", "businessNo", "reason", "operationType"
    );

    private final PdcNfcAdminIdempotencyService idempotencyService;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcOperationLogDao operationLogDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcAssetStateMachine assetStateMachine;
    private final PdcNfcBatchStateMachine batchStateMachine;
    private final PdcNfcProperties properties;
    private final ObjectMapper objectMapper;

    // ==================== 公开方法 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcBulkOperationVO stockIn(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        validateBulkRequest(request);
        requireFeatureEnabled();
        return idempotencyService.execute(
                PdcNfcAdminOperationType.STOCK_IN,
                request.getRequestId(),
                canonicalBulkRequest(request),
                PdcNfcBulkOperationVO.class,
                () -> doStockIn(request, operatorId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcBulkOperationVO activate(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        validateBulkRequest(request);
        requireActivateReady();
        return idempotencyService.execute(
                PdcNfcAdminOperationType.ACTIVATE,
                request.getRequestId(),
                canonicalBulkRequest(request),
                PdcNfcBulkOperationVO.class,
                () -> doActivate(request, operatorId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcBulkOperationVO disable(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        validateBulkRequest(request);
        requireFeatureEnabled();
        return idempotencyService.execute(
                PdcNfcAdminOperationType.DISABLE,
                request.getRequestId(),
                canonicalBulkRequest(request),
                PdcNfcBulkOperationVO.class,
                () -> doDisable(request, operatorId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcBulkOperationVO scrap(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        validateBulkRequest(request);
        requireFeatureEnabled();
        return idempotencyService.execute(
                PdcNfcAdminOperationType.SCRAP,
                request.getRequestId(),
                canonicalBulkRequest(request),
                PdcNfcBulkOperationVO.class,
                () -> doScrap(request, operatorId)
        );
    }

    // ==================== doXxx 方法 ====================

    private PdcNfcBulkOperationVO doStockIn(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        PdcNfcBulkOperationVO result = doBulkOperation(request, operatorId, "STOCK_IN",
                Set.of(VERIFIED), IN_STOCK,
                PdcNfcAssetEntity::setStockedAt,
                (asset, bn) -> asset.setStockBusinessNo(bn));
        advanceCompletedBatches(request, operatorId);
        return result;
    }

    /**
     * 入库后按受影响批次检查：仅当该批次不再有 VERIFIED 资产且当前为
     * READY_FOR_STOCK 时，推进为 COMPLETED。
     */
    private void advanceCompletedBatches(
            PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        Set<Long> batchIds = new HashSet<>();
        for (Long assetId : request.getAssetIds()) {
            PdcNfcAssetEntity stocked = assetDao.selectById(assetId);
            if (stocked != null && stocked.getBatchId() != null) {
                batchIds.add(stocked.getBatchId());
            }
        }
        Date now = new Date();
        for (Long batchId : batchIds) {
            PdcNfcBatchEntity batch = batchDao.selectById(batchId);
            if (batch == null
                    || !PdcNfcBatchStatus.READY_FOR_STOCK.name().equals(batch.getStatus())
                    || assetDao.countByBatchIdAndStatus(
                            batchId, VERIFIED.name()) != 0) {
                continue;
            }
            batchStateMachine.requireTransition(
                    PdcNfcBatchStatus.READY_FOR_STOCK, PdcNfcBatchStatus.COMPLETED);
            batch.setStatus(PdcNfcBatchStatus.COMPLETED.name());
            batch.setUpdater(operatorId);
            batch.setUpdateDate(now);
            if (batchDao.updateById(batch) != 1) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        }
    }

    private PdcNfcBulkOperationVO doActivate(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        return doBulkOperation(request, operatorId, "ACTIVATE",
                Set.of(IN_STOCK), ACTIVE,
                PdcNfcAssetEntity::setActivatedAt,
                (asset, bn) -> asset.setActivationBusinessNo(bn));
    }

    private PdcNfcBulkOperationVO doDisable(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        return doBulkOperation(request, operatorId, "DISABLE",
                Set.of(IN_STOCK, ACTIVE, CLAIMED), DISABLED,
                PdcNfcAssetEntity::setDisabledAt,
                (asset, bn) -> {});
    }

    private PdcNfcBulkOperationVO doScrap(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
        return doBulkOperation(request, operatorId, "SCRAP",
                Set.of(CREATED, SCHEME_GENERATED, WRITTEN, VERIFIED), SCRAPPED,
                PdcNfcAssetEntity::setScrappedAt,
                (asset, bn) -> {});
    }

    // ==================== 批量操作核心逻辑 ====================

    /**
     * 批量操作通用逻辑：锁定 → 预检 → 更新 → 记日志
     */
    private PdcNfcBulkOperationVO doBulkOperation(
            PdcNfcBulkAssetOperationDTO request,
            Long operatorId,
            String operationType,
            Set<PdcNfcAssetStatus> allowedFromStates,
            PdcNfcAssetStatus targetState,
            BiConsumer<PdcNfcAssetEntity, Date> timestampSetter,
            BiConsumer<PdcNfcAssetEntity, String> businessNoSetter
    ) {
        // 1. 排序并锁定资产（FOR UPDATE）
        List<Long> sortedIds = request.getAssetIds().stream()
                .sorted()
                .collect(Collectors.toList());
        List<PdcNfcAssetEntity> assets = assetDao.selectByIdsForUpdate(sortedIds);

        // 2. 检查所有资产都存在
        if (assets.size() != sortedIds.size()) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }

        // 3. 预检所有状态转换（fail-fast）
        Date now = new Date();
        for (PdcNfcAssetEntity asset : assets) {
            PdcNfcAssetStatus currentStatus = PdcNfcAssetStatus.valueOf(asset.getStatus());
            if (!allowedFromStates.contains(currentStatus)) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
            assetStateMachine.requireTransition(currentStatus, targetState);
        }

        // 4. 批量更新状态、时间戳、业务单号
        for (PdcNfcAssetEntity asset : assets) {
            String beforeStatus = asset.getStatus();
            asset.setStatus(targetState.name());
            timestampSetter.accept(asset, now);
            businessNoSetter.accept(asset, request.getBusinessNo());
            asset.setUpdater(operatorId);
            asset.setUpdateDate(now);
            assetDao.updateById(asset);

            // 5. 记录操作日志
            appendOperationLog(asset, beforeStatus, targetState.name(),
                    operationType, request, operatorId, now);
        }

        return new PdcNfcBulkOperationVO(
                assets.size(), assets.size(), 0,
                request.getBusinessNo(), request.getRequestId()
        );
    }

    // ==================== 校验与规范化 ====================

    private void validateBulkRequest(PdcNfcBulkAssetOperationDTO request) {
        List<Long> assetIds = request.getAssetIds();
        if (assetIds == null || assetIds.isEmpty()) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }
        if (assetIds.size() > MAX_BULK_SIZE) {
            throw new RenException(ErrorCode.PDC_NFC_BULK_LIMIT_EXCEEDED);
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : assetIds) {
            if (id == null) {
                throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
            }
            if (!seen.add(id)) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE,
                        "Duplicate asset ID: " + id);
            }
        }
    }

    /**
     * 规范化请求字符串：businessNo + 排序后的 assetIds。
     * 不移除重复（重复在 validateBulkRequest 中已被拒绝）。
     */
    private String canonicalBulkRequest(PdcNfcBulkAssetOperationDTO request) {
        String sortedIds = request.getAssetIds().stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return request.getBusinessNo() + ":" + sortedIds;
    }

    // ==================== 门控检查 ====================

    private void requireFeatureEnabled() {
        if (!properties.isEnabled()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
    }

    private void requireActivateReady() {
        requireFeatureEnabled();
        if (!properties.isActivationEnabled()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (!properties.isReleaseReady()) {
            throw new RenException(ErrorCode.PDC_NFC_RELEASE_NOT_READY);
        }
    }

    // ==================== 操作日志 ====================

    private void appendOperationLog(
            PdcNfcAssetEntity asset,
            String beforeStatus,
            String afterStatus,
            String operationType,
            PdcNfcBulkAssetOperationDTO request,
            Long operatorId,
            Date now
    ) {
        PdcNfcOperationLogEntity logEntity = new PdcNfcOperationLogEntity();
        logEntity.setOperatorUserId(operatorId);
        logEntity.setRequestId(request.getRequestId().toString());
        logEntity.setSource("ADMIN");
        logEntity.setObjectType("ASSET");
        logEntity.setObjectId(asset.getId());
        logEntity.setOperationType(operationType);
        logEntity.setBeforeStatus(beforeStatus);
        logEntity.setAfterStatus(afterStatus);
        logEntity.setQuantity(1);
        logEntity.setBusinessNo(request.getBusinessNo());
        logEntity.setResult("SUCCESS");
        logEntity.setDetailJson(buildDetailJson(asset, beforeStatus, afterStatus, request.getBusinessNo()));
        logEntity.setCreateDate(now);
        operationLogDao.insert(logEntity);
    }

    private String buildDetailJson(PdcNfcAssetEntity asset, String beforeStatus,
                                    String afterStatus, String businessNo) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("assetNo", asset.getAssetNo());
            node.put("beforeStatus", beforeStatus);
            node.put("afterStatus", afterStatus);
            node.put("businessNo", businessNo);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 查询方法 ====================

    @Override
    public PageData<PdcNfcAssetVO> queryAssets(PdcNfcAssetQueryDTO query) {
        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getLimit() != null && query.getLimit() > 0
                ? Math.min(query.getLimit(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Page<PdcNfcAssetEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PdcNfcAssetEntity> wrapper = new LambdaQueryWrapper<>();

        // productTypeId 在 batch 表上，需要先查 batchId
        if (query.getProductTypeId() != null) {
            List<PdcNfcBatchEntity> batches = batchDao.selectList(
                    new LambdaQueryWrapper<PdcNfcBatchEntity>()
                            .eq(PdcNfcBatchEntity::getProductTypeId, query.getProductTypeId()));
            Set<Long> batchIds = batches.stream()
                    .map(PdcNfcBatchEntity::getId)
                    .collect(Collectors.toSet());
            if (batchIds.isEmpty()) {
                return new PageData<>(Collections.emptyList(), 0);
            }
            wrapper.in(PdcNfcAssetEntity::getBatchId, batchIds);
        }
        if (query.getBatchId() != null) {
            wrapper.eq(PdcNfcAssetEntity::getBatchId, query.getBatchId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PdcNfcAssetEntity::getStatus, query.getStatus());
        }
        if (query.getSkuCode() != null) {
            wrapper.eq(PdcNfcAssetEntity::getSkuCode, query.getSkuCode());
        }
        if (query.getPrototype() != null) {
            wrapper.eq(PdcNfcAssetEntity::getPrototype, query.getPrototype());
        }
        if (query.getAssetNo() != null) {
            wrapper.like(PdcNfcAssetEntity::getAssetNo, query.getAssetNo());
        }
        if (query.getWechatSn() != null) {
            wrapper.eq(PdcNfcAssetEntity::getWechatSn, query.getWechatSn());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(PdcNfcAssetEntity::getCreateDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(PdcNfcAssetEntity::getCreateDate, query.getEndDate());
        }
        wrapper.orderByDesc(PdcNfcAssetEntity::getCreateDate);

        IPage<PdcNfcAssetEntity> result = assetDao.selectPage(page, wrapper);

        Map<Long, String> batchNoMap = loadBatchNoMap(result.getRecords());
        List<PdcNfcAssetVO> voList = result.getRecords().stream()
                .map(entity -> toAssetVO(entity, batchNoMap))
                .collect(Collectors.toList());

        return new PageData<>(voList, result.getTotal());
    }

    @Override
    public PdcNfcAssetVO getAssetDetail(Long id) {
        PdcNfcAssetEntity entity = assetDao.selectById(id);
        if (entity == null) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }
        Map<Long, String> batchNoMap = loadBatchNoMap(List.of(entity));
        return toAssetVO(entity, batchNoMap);
    }

    @Override
    public PageData<PdcNfcOperationLogVO> queryOperationLogs(PdcNfcOperationLogQueryDTO query) {
        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getLimit() != null && query.getLimit() > 0
                ? Math.min(query.getLimit(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Page<PdcNfcOperationLogEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PdcNfcOperationLogEntity> wrapper = new LambdaQueryWrapper<>();

        if (query.getObjectType() != null) {
            wrapper.eq(PdcNfcOperationLogEntity::getObjectType, query.getObjectType());
        }
        if (query.getObjectId() != null) {
            wrapper.eq(PdcNfcOperationLogEntity::getObjectId, query.getObjectId());
        }
        if (query.getOperationType() != null) {
            wrapper.eq(PdcNfcOperationLogEntity::getOperationType, query.getOperationType());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(PdcNfcOperationLogEntity::getCreateDate, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(PdcNfcOperationLogEntity::getCreateDate, query.getEndDate());
        }
        wrapper.orderByDesc(PdcNfcOperationLogEntity::getCreateDate);

        IPage<PdcNfcOperationLogEntity> result = operationLogDao.selectPage(page, wrapper);

        List<PdcNfcOperationLogVO> voList = result.getRecords().stream()
                .map(this::toOperationLogVO)
                .collect(Collectors.toList());

        return new PageData<>(voList, result.getTotal());
    }

    @Override
    public PageData<PdcNfcOperationLogVO> queryLogsByObject(String objectType, Long objectId,
                                                              PdcNfcOperationLogQueryDTO query) {
        if (query == null) {
            query = new PdcNfcOperationLogQueryDTO();
        }
        query.setObjectType(objectType);
        query.setObjectId(objectId);
        return queryOperationLogs(query);
    }

    // ==================== VO 转换 ====================

    private PdcNfcAssetVO toAssetVO(PdcNfcAssetEntity entity, Map<Long, String> batchNoMap) {
        Map<String, Date> timeline = new LinkedHashMap<>();
        if (entity.getSchemeGeneratedAt() != null) timeline.put("schemeGeneratedAt", entity.getSchemeGeneratedAt());
        if (entity.getWrittenAt() != null) timeline.put("writtenAt", entity.getWrittenAt());
        if (entity.getVerifiedAt() != null) timeline.put("verifiedAt", entity.getVerifiedAt());
        if (entity.getStockedAt() != null) timeline.put("stockedAt", entity.getStockedAt());
        if (entity.getActivatedAt() != null) timeline.put("activatedAt", entity.getActivatedAt());
        if (entity.getClaimedAt() != null) timeline.put("claimedAt", entity.getClaimedAt());
        if (entity.getDisabledAt() != null) timeline.put("disabledAt", entity.getDisabledAt());
        if (entity.getScrappedAt() != null) timeline.put("scrappedAt", entity.getScrappedAt());

        return new PdcNfcAssetVO(
                entity.getId(),
                entity.getAssetNo(),
                batchNoMap.get(entity.getBatchId()),
                entity.getItemNo(),
                entity.getSkuCode(),
                entity.getPrototype(),
                entity.getWechatSn(),
                entity.getStatus(),
                entity.getSchemeSha256(),
                timeline
        );
    }

    private PdcNfcOperationLogVO toOperationLogVO(PdcNfcOperationLogEntity entity) {
        return new PdcNfcOperationLogVO(
                entity.getId(),
                entity.getObjectType(),
                entity.getObjectId(),
                entity.getOperationType(),
                entity.getOperatorUserId(),
                entity.getCreateDate(),
                filterDetailJson(entity.getDetailJson())
        );
    }

    private String filterDetailJson(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(detailJson);
            ObjectNode filtered = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (DETAIL_JSON_ALLOWLIST.contains(entry.getKey())) {
                    filtered.set(entry.getKey(), entry.getValue());
                }
            });
            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    private Map<Long, String> loadBatchNoMap(List<PdcNfcAssetEntity> assets) {
        Set<Long> batchIds = assets.stream()
                .map(PdcNfcAssetEntity::getBatchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (batchIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> batchNoMap = new HashMap<>();
        for (Long batchId : batchIds) {
            PdcNfcBatchEntity batch = batchDao.selectById(batchId);
            if (batch != null) {
                batchNoMap.put(batchId, batch.getBatchNo());
            }
        }
        return batchNoMap;
    }
}
