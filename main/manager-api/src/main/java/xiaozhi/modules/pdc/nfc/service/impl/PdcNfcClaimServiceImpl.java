package xiaozhi.modules.pdc.nfc.service.impl;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcClaimRateLimiter;
import xiaozhi.modules.pdc.nfc.service.PdcNfcClaimService;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimPreviewVO;
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

    public PdcNfcClaimServiceImpl(
            PdcNfcProperties properties,
            WechatPhoneGate wechatPhoneGate,
            ClaimRefProtection claimRefProtection,
            PdcNfcAssetDao assetDao,
            PdcNfcBatchDao batchDao,
            PdcNfcProductTypeDao productTypeDao,
            PdcNfcClaimRateLimiter rateLimiter,
            PetService petService) {
        this.properties = properties;
        this.wechatPhoneGate = wechatPhoneGate;
        this.claimRefProtection = claimRefProtection;
        this.assetDao = assetDao;
        this.batchDao = batchDao;
        this.productTypeDao = productTypeDao;
        this.rateLimiter = rateLimiter;
        this.petService = petService;
    }

    @Override
    public PdcNfcClaimPreviewVO preview(Long userId, String claimRef) {
        // 1. Validate claimRef format
        if (claimRef == null || !CLAIM_REF_PATTERN.matcher(claimRef).matches()) {
            log.debug("[NFC-CLAIM] Invalid claimRef format for userId={}", userId);
            rateLimiter.recordInvalidRef(userId);
            return unavailable();
        }

        // 2. Check phone binding
        if (!wechatPhoneGate.hasBoundWechatPhone(userId)) {
            log.debug("[NFC-CLAIM] userId={} has no bound wechat phone", userId);
            return unavailable();
        }

        // 3. Feature gates: enabled → claimEnabled → releaseReady
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

        // 4. Rate limit check
        rateLimiter.checkPreviewUserRate(userId);

        // 5. Lookup asset by claimRef hash
        List<String> hashes = claimRefProtection.lookupHashes(claimRef);
        List<PdcNfcAssetEntity> assets = assetDao.selectList(
                new QueryWrapper<PdcNfcAssetEntity>()
                        .in("claim_ref_hash", hashes)
                        .last("LIMIT 1"));

        // 6. No asset found → UNAVAILABLE (don't reveal existence)
        if (assets == null || assets.isEmpty()) {
            log.debug("[NFC-CLAIM] No asset found for claimRef hash, userId={}", userId);
            return unavailable();
        }

        PdcNfcAssetEntity asset = assets.get(0);

        // Rate limit on asset
        rateLimiter.checkPreviewAssetRate(asset.getId());

        // 7. Check asset status
        String status = asset.getStatus();
        if (PdcNfcAssetStatus.ACTIVE.name().equals(status)) {
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
