package xiaozhi.modules.pdc.nfc.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcClaimRecordDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcClaimRecordEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcClaimRateLimiter;
import xiaozhi.modules.pdc.nfc.service.PdcNfcClaimService;
import xiaozhi.modules.pdc.nfc.service.PdcNfcManualWriteService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimPreviewVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimResultVO;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.wechat.service.WechatPhoneGate;

@Service
public class PdcNfcClaimServiceImpl implements PdcNfcClaimService {

    private static final Logger log = LoggerFactory.getLogger(PdcNfcClaimServiceImpl.class);
    private static final Pattern CLAIM_REF_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");

    private final PdcNfcProperties properties;
    private final WechatPhoneGate wechatPhoneGate;
    private final ClaimRefProtection claimRefProtection;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcProductTypeDao productTypeDao;
    private final PdcNfcClaimRateLimiter rateLimiter;
    private final PetService petService;
    private final PdcNfcClaimRecordDao claimRecordDao;
    private final PdcNfcManualWriteService manualWriteService;

    public PdcNfcClaimServiceImpl(
            PdcNfcProperties properties,
            WechatPhoneGate wechatPhoneGate,
            ClaimRefProtection claimRefProtection,
            PdcNfcAssetDao assetDao,
            PdcNfcBatchDao batchDao,
            PdcNfcProductTypeDao productTypeDao,
            PdcNfcClaimRateLimiter rateLimiter,
            PetService petService,
            PdcNfcClaimRecordDao claimRecordDao,
            PdcNfcManualWriteService manualWriteService) {
        this.properties = properties;
        this.wechatPhoneGate = wechatPhoneGate;
        this.claimRefProtection = claimRefProtection;
        this.assetDao = assetDao;
        this.batchDao = batchDao;
        this.productTypeDao = productTypeDao;
        this.rateLimiter = rateLimiter;
        this.petService = petService;
        this.claimRecordDao = claimRecordDao;
        this.manualWriteService = manualWriteService;
    }

    @Override
    public PdcNfcClaimPreviewVO preview(Long userId, String claimRef) {
        // 1. Validate claimRef format
        if (claimRef == null || !CLAIM_REF_PATTERN.matcher(claimRef).matches()) {
            log.debug("[NFC-CLAIM] Invalid ref format for userId={}", userId);
            rateLimiter.recordInvalidRef(userId);
            return unavailable();
        }

        // 2. Lookup asset by claimRef hash（提前到所有门禁之前，见下）
        List<String> hashes = claimRefProtection.lookupHashes(claimRef);
        List<PdcNfcAssetEntity> assets = assetDao.selectList(
                new QueryWrapper<PdcNfcAssetEntity>()
                        .in("claim_ref_hash", hashes)
                        .last("LIMIT 1"));

        // ADR 0003 手动写卡模式：触碰自验证 + 锁后复验。
        // 只要资产被查到了就触发，必须在手机号门禁、功能开关、限流之前——
        // 复验证据价值在"卡可读"本身，未授权用户的触碰同样有效；
        // 且写卡验证阶段 release 通常未就绪，功能开关不得挡住验证链路。
        // 无副作用的幂等推进，不核销领取资格，不影响下方状态分支的返回。
        if (assets != null && !assets.isEmpty()) {
            manualWriteService.touchVerify(assets.get(0));
        }

        // 3. 手机号门禁不在 preview 校验：未授权用户也可预览产品信息（先看货再授权），
        // claimRef 只有物理碰卡才能拿到，预览不泄露额外信息；领取确认仍在 confirm 校验。

        // 4. Feature gates: enabled → claimEnabled → releaseReady
        if (!properties.isEnabled()) {
            log.debug("[NFC-CLAIM] NFC feature disabled");
            return unavailable();
        }
        if (!properties.isClaimEnabled()) {
            log.debug("[NFC-CLAIM] NFC claim disabled");
            return unavailable();
        }
        if (!properties.isReleaseReady()) {
            log.debug("[NFC-CLAIM] NFC release not ready");
            return unavailable();
        }

        // 5. Rate limit check
        rateLimiter.checkPreviewUserRate(userId);

        // 6. No asset found → UNAVAILABLE (don't reveal existence)
        if (assets == null || assets.isEmpty()) {
            log.debug("[NFC-CLAIM] No asset found for ref hash, userId={}", userId);
            return unavailable();
        }

        PdcNfcAssetEntity asset = assets.get(0);

        // Rate limit on asset
        rateLimiter.checkPreviewAssetRate(asset.getId());

        // 7. Check asset status
        String status = asset.getStatus();
        if (PdcNfcAssetStatus.ACTIVE.name().equals(status)) {
            // 一人一宠：已领养用户再碰激活卡，提前返回 ALREADY_OWNED，
            // 避免走到 confirm 才失败；CLAIMED_BY_SELF（碰自己的卡）不走此分支，直达行为不变
            if (ownsPet(userId)) {
                return new PdcNfcClaimPreviewVO(
                        null, null, PdcNfcClaimPreviewVO.STATUS_ALREADY_OWNED, null);
            }
            // 8. ACTIVE but release-ready already checked above
            String productName = resolveProductName(asset.getBatchId());
            return new PdcNfcClaimPreviewVO(
                    productName,
                    asset.getPrototype(),
                    PdcNfcClaimPreviewVO.STATUS_CLAIMABLE,
                    null);
        }

        if (PdcNfcAssetStatus.CLAIMED.name().equals(status)) {
            if (userId.equals(asset.getClaimedUserId())) {
                // Claimed by self - include pet info
                Object pet = null;
                if (asset.getPetId() != null) {
                    try {
                        PetVO petVO = petService.getById(userId, asset.getPetId());
                        pet = petVO;
                    } catch (Exception e) {
                        log.warn("[NFC-CLAIM] Failed to fetch pet for assetId={}, petId={}",
                                asset.getId(), asset.getPetId(), e);
                    }
                }
                String productName = resolveProductName(asset.getBatchId());
                return new PdcNfcClaimPreviewVO(
                        productName,
                        asset.getPrototype(),
                        PdcNfcClaimPreviewVO.STATUS_CLAIMED_BY_SELF,
                        pet);
            } else {
                // Claimed by other
                rateLimiter.detectContention(asset.getId(), userId);
                String productName = resolveProductName(asset.getBatchId());
                return new PdcNfcClaimPreviewVO(
                        productName,
                        asset.getPrototype(),
                        PdcNfcClaimPreviewVO.STATUS_CLAIMED_BY_OTHER,
                        null);
            }
        }

        // All other states → UNAVAILABLE
        log.debug("[NFC-CLAIM] Asset status {} is not claimable, assetId={}", status, asset.getId());
        return unavailable();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdcNfcClaimResultVO confirm(Long userId, String claimRef, UUID requestId) {
        // 1. Auth: check phone binding + feature gates
        requireClaimEnabledAndPhone(userId);

        // 2. Validate claimRef format
        if (claimRef == null || !CLAIM_REF_PATTERN.matcher(claimRef).matches()) {
            log.debug("[NFC-CLAIM-CONFIRM] Invalid ref format for userId={}", userId);
            rateLimiter.recordInvalidRef(userId);
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }

        // 3. Rate limit
        rateLimiter.checkConfirmUserRate(userId);

        // 4. HMAC lookup + FOR UPDATE
        List<String> hashes = claimRefProtection.lookupHashes(claimRef);
        List<PdcNfcAssetEntity> candidates = assetDao.selectByClaimHashesForUpdate(hashes);
        PdcNfcAssetEntity asset = requireExactlyOne(candidates);

        // 5. Rate limit on asset
        rateLimiter.checkConfirmAssetRate(asset.getId());

        // 6. Idempotency check
        String fingerprint = computeFingerprint(asset.getId(), requestId);
        Optional<PdcNfcClaimRecordEntity> replay = claimRecordDao.findByUserAndRequest(userId, requestId.toString());
        if (replay.isPresent()) {
            if (replay.get().getRequestFingerprint().equals(fingerprint)) {
                return replayResult(replay.get());
            }
            throw new RenException(ErrorCode.PDC_NFC_IDEMPOTENCY_CONFLICT);
        }

        // 7. Already claimed?
        if (PdcNfcAssetStatus.CLAIMED.name().equals(asset.getStatus())) {
            if (userId.equals(asset.getClaimedUserId())) {
                return PdcNfcClaimResultVO.claimedBySelf(loadPet(userId, asset.getPetId()));
            }
            throw new RenException(ErrorCode.PDC_NFC_ASSET_ALREADY_CLAIMED);
        }

        // 8. Must be ACTIVE
        if (!PdcNfcAssetStatus.ACTIVE.name().equals(asset.getStatus())) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_UNAVAILABLE);
        }

        // 9. Create pet (same transaction)
        PetVO pet = petService.createEgg(userId, asset.getPrototype());

        // 10. Insert claim record
        PdcNfcClaimRecordEntity record = new PdcNfcClaimRecordEntity();
        record.setAssetId(asset.getId());
        record.setUserId(userId);
        record.setRequestId(requestId.toString());
        record.setRequestFingerprint(fingerprint);
        record.setPetId(pet.getId());
        record.setResult("CLAIMED");
        record.setCreateDate(new Date());
        claimRecordDao.insert(record);

        // 11. Mark asset as claimed (optimistic lock)
        int changed = assetDao.markClaimed(asset.getId(), asset.getVersion(), userId, pet.getId());
        if (changed != 1) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        // 12. Audit
        log.info("[NFC-CLAIM-CONFIRM] NFC claim confirmed: asset={}, user={}, pet={}",
                asset.getId(), userId, pet.getId());

        return PdcNfcClaimResultVO.claimed(pet);
    }

    private void requireClaimEnabledAndPhone(Long userId) {
        if (!wechatPhoneGate.hasBoundWechatPhone(userId)) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_UNAVAILABLE);
        }
        if (!properties.isEnabled()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (!properties.isClaimEnabled()) {
            throw new RenException(ErrorCode.PDC_NFC_FEATURE_DISABLED);
        }
        if (!properties.isReleaseReady()) {
            throw new RenException(ErrorCode.PDC_NFC_RELEASE_NOT_READY);
        }
    }

    private PdcNfcAssetEntity requireExactlyOne(List<PdcNfcAssetEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }
        if (candidates.size() > 1) {
            log.warn("[NFC-CLAIM-CONFIRM] Multiple assets found for ref hashes, count={}", candidates.size());
            throw new RenException(ErrorCode.PDC_NFC_ASSET_UNAVAILABLE);
        }
        return candidates.get(0);
    }

    private String computeFingerprint(Long assetId, UUID requestId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String canonical = assetId + ":" + requestId;
            byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RenException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private PdcNfcClaimResultVO replayResult(PdcNfcClaimRecordEntity record) {
        PetVO pet = null;
        if (record.getPetId() != null) {
            try {
                pet = petService.getById(record.getUserId(), record.getPetId());
            } catch (Exception e) {
                log.warn("[NFC-CLAIM-CONFIRM] Failed to load pet for replay: petId={}", record.getPetId(), e);
            }
        }
        return PdcNfcClaimResultVO.claimed(pet);
    }

    private PetVO loadPet(Long userId, String petId) {
        if (petId == null) {
            return null;
        }
        try {
            return petService.getById(userId, petId);
        } catch (Exception e) {
            log.warn("[NFC-CLAIM-CONFIRM] Failed to load pet: userId={}, petId={}", userId, petId, e);
            return null;
        }
    }

    /**
     * 用户是否已领养蛋宝宝（一人一宠约束判定）。查询失败时按未持有处理并记 warn，
     * 不阻断 preview 主链路——真正的并发兜底在 createEgg 的唯一索引转换。
     */
    private boolean ownsPet(Long userId) {
        try {
            List<PetVO> pets = petService.listByUserId(userId);
            return pets != null && !pets.isEmpty();
        } catch (Exception e) {
            log.warn("[NFC-CLAIM] Failed to check owned pets for userId={}", userId, e);
            return false;
        }
    }

    private String resolveProductName(Long batchId) {
        if (batchId == null) {
            return null;
        }
        PdcNfcBatchEntity batch = batchDao.selectById(batchId);
        if (batch == null || batch.getProductTypeId() == null) {
            return null;
        }
        PdcNfcProductTypeEntity productType = productTypeDao.selectById(batch.getProductTypeId());
        return productType != null ? productType.getTypeName() : null;
    }

    private PdcNfcClaimPreviewVO unavailable() {
        return new PdcNfcClaimPreviewVO(null, null, PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE, null);
    }
}
