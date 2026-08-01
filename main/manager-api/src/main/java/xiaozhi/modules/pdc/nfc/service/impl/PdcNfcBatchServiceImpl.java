package xiaozhi.modules.pdc.nfc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcPrototype;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.crypto.PdcNfcIdentifierGenerator;
import xiaozhi.modules.pdc.nfc.crypto.ProtectedClaimRef;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcSchemeJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dto.CreatePdcNfcBatchDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcBatchQueryDTO;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcBatchStateMachine;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBatchVO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcBatchServiceImpl implements PdcNfcBatchService {

    private static final int ASSET_INSERT_CHUNK_SIZE = 500;

    private final PdcNfcBatchDao batchDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcProductTypeDao productTypeDao;
    private final PdcNfcSchemeJobDao schemeJobDao;
    private final PdcNfcWriteJobDao writeJobDao;
    private final PdcNfcProperties properties;
    private final PdcNfcIdentifierGenerator identifiers;
    private final ClaimRefProtection claimRefs;
    private final PdcNfcBatchStateMachine batchStateMachine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcBatchVO create(CreatePdcNfcBatchDTO dto, Long operatorId) {
        // 1. 验证功能开启
        requireEnabled();

        // 2. 验证商品类型存在
        PdcNfcProductTypeEntity productType = productTypeDao.selectById(dto.getProductTypeId());
        if (productType == null) {
            throw new RenException(ErrorCode.PDC_NFC_BATCH_NOT_FOUND);
        }

        // 3. 验证批次号唯一
        QueryWrapper<PdcNfcBatchEntity> batchQw = new QueryWrapper<>();
        batchQw.eq("batch_no", dto.getBatchNo());
        if (batchDao.selectCount(batchQw) > 0) {
            throw new RenException(ErrorCode.PDC_NFC_IDEMPOTENCY_CONFLICT);
        }

        // 4. 验证原型合法
        if (!PdcNfcPrototype.isValid(dto.getPrototype())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_PROTOTYPE);
        }

        // 5. 验证数量范围
        if (dto.getPlannedQuantity() < 1 || dto.getPlannedQuantity() > properties.getMaxBatchQuantity()) {
            throw new RenException(ErrorCode.PDC_NFC_BULK_LIMIT_EXCEEDED);
        }

        // 6. 创建批次
        Date now = new Date();
        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setBatchNo(dto.getBatchNo());
        batch.setProductTypeId(dto.getProductTypeId());
        batch.setSkuCode(dto.getSkuCode());
        batch.setPrototype(dto.getPrototype());
        batch.setPlannedQuantity(dto.getPlannedQuantity());
        batch.setStatus(PdcNfcBatchStatus.DRAFT.name());
        batch.setRemark(dto.getRemark());
        batch.setCreator(operatorId);
        batch.setCreateDate(now);
        batchDao.insert(batch);

        // 7. 原子分配资产
        List<PdcNfcAssetEntity> assets = new ArrayList<>();
        for (int index = 1; index <= dto.getPlannedQuantity(); index++) {
            long assetId = IdWorker.getId();
            String claimRef = identifiers.newClaimRef();
            ProtectedClaimRef protectedRef = claimRefs.protect(assetId, claimRef);

            PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
            asset.setId(assetId);
            asset.setAssetNo(formatAssetNo(dto.getBatchNo(), index));
            asset.setBatchId(batch.getId());
            asset.setItemNo(String.format("%06d", index));
            asset.setSkuCode(dto.getSkuCode());
            asset.setPrototype(dto.getPrototype());
            asset.setWechatSn(identifiers.newWechatSn());
            asset.setClaimRefHash(protectedRef.lookupHash());
            asset.setClaimRefHashVersion(properties.getClaimRef().getActiveVersion());
            asset.setClaimRefKeyVersion(protectedRef.encrypted().keyVersion());
            asset.setClaimRefNonce(protectedRef.encrypted().nonce());
            asset.setClaimRefCiphertext(protectedRef.encrypted().ciphertext());
            asset.setStatus(PdcNfcAssetStatus.CREATED.name());
            asset.setVersion(0);
            asset.setCreator(operatorId);
            asset.setCreateDate(now);
            assets.add(asset);
        }

        // 8. 分块插入资产
        Lists.partition(assets, ASSET_INSERT_CHUNK_SIZE).forEach(assetDao::insertBatch);

        log.info("批次创建成功 batchNo={}, plannedQuantity={}, operatorId={}",
                dto.getBatchNo(), dto.getPlannedQuantity(), operatorId);

        return toVO(batch, dto.getPlannedQuantity());
    }

    @Override
    public List<PdcNfcBatchVO> list(PdcNfcBatchQueryDTO query) {
        QueryWrapper<PdcNfcBatchEntity> qw = new QueryWrapper<>();
        if (query != null) {
            if (query.getBatchNo() != null) qw.eq("batch_no", query.getBatchNo());
            if (query.getProductTypeId() != null) qw.eq("product_type_id", query.getProductTypeId());
            if (query.getStatus() != null) qw.eq("status", query.getStatus());
            if (query.getPrototype() != null) qw.eq("prototype", query.getPrototype());
        }
        qw.orderByDesc("create_date");
        List<PdcNfcBatchEntity> batches = batchDao.selectList(qw);
        return batches.stream().map(b -> {
            int assetCount = countAssetsByBatchId(b.getId());
            PdcNfcSchemeJobEntity schemeJob = schemeJobDao.selectLatestByBatchId(b.getId());
            PdcNfcWriteJobEntity writeJob = selectLatestWriteJob(b.getId());
            return toVO(b, assetCount, schemeJob, writeJob);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long batchId, Long operatorId) {
        requireEnabled();
        PdcNfcBatchEntity batch = batchDao.selectById(batchId);
        if (batch == null) {
            throw new RenException(ErrorCode.PDC_NFC_BATCH_NOT_FOUND);
        }
        PdcNfcBatchStatus currentStatus = PdcNfcBatchStatus.valueOf(batch.getStatus());
        batchStateMachine.requireTransition(currentStatus, PdcNfcBatchStatus.CANCELLED);

        batch.setStatus(PdcNfcBatchStatus.CANCELLED.name());
        batch.setUpdater(operatorId);
        batch.setUpdateDate(new Date());
        batchDao.updateById(batch);
        log.info("批次已取消 batchId={}, operatorId={}", batchId, operatorId);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
    }

    private String formatAssetNo(String batchNo, int index) {
        return batchNo + "-" + String.format("%06d", index);
    }

    private int countAssetsByBatchId(Long batchId) {
        QueryWrapper<PdcNfcAssetEntity> qw = new QueryWrapper<>();
        qw.eq("batch_id", batchId);
        return Math.toIntExact(assetDao.selectCount(qw));
    }

    private PdcNfcWriteJobEntity selectLatestWriteJob(Long batchId) {
        QueryWrapper<PdcNfcWriteJobEntity> qw = new QueryWrapper<>();
        qw.eq("batch_id", batchId).orderByDesc("create_date").last("LIMIT 1");
        return writeJobDao.selectOne(qw);
    }

    private PdcNfcBatchVO toVO(PdcNfcBatchEntity batch, int assetCount) {
        return toVO(batch, assetCount, null, null);
    }

    private PdcNfcBatchVO toVO(PdcNfcBatchEntity batch, int assetCount,
                               PdcNfcSchemeJobEntity schemeJob, PdcNfcWriteJobEntity writeJob) {
        return new PdcNfcBatchVO(
                batch.getId(),
                batch.getBatchNo(),
                batch.getProductTypeId(),
                batch.getSkuCode(),
                batch.getPrototype(),
                batch.getPlannedQuantity(),
                batch.getStatus(),
                batch.getRemark(),
                assetCount,
                schemeJob != null ? schemeJob.getId() : null,
                writeJob != null ? writeJob.getId() : null,
                writeJob != null ? writeJob.getStatus() : null,
                batch.getCreator(),
                batch.getCreateDate()
        );
    }
}
