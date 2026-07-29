package xiaozhi.modules.pdc.nfc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcClaimRecordDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcClaimRecordEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcClaimServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimResultVO;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.wechat.service.WechatPhoneGate;

@ExtendWith(MockitoExtension.class)
class PdcNfcClaimServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long OTHER_USER_ID = 999L;
    private static final String VALID_CLAIM_REF = "abcdefghij1234567890_-";
    private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID DIFFERENT_REQUEST_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    @Mock private PdcNfcProperties properties;
    @Mock private WechatPhoneGate wechatPhoneGate;
    @Mock private ClaimRefProtection claimRefProtection;
    @Mock private PdcNfcAssetDao assetDao;
    @Mock private PdcNfcBatchDao batchDao;
    @Mock private PdcNfcProductTypeDao productTypeDao;
    @Mock private PdcNfcClaimRateLimiter rateLimiter;
    @Mock private PetService petService;
    @Mock private PdcNfcClaimRecordDao claimRecordDao;

    private PdcNfcClaimServiceImpl claimService;

    @BeforeAll
    static void initMessageSource() throws Exception {
        MessageSource mockSource = mock(MessageSource.class);
        lenient().when(mockSource.getMessage(anyString(), any(), any(), any(Locale.class)))
                .thenReturn("mock message");
        Field field = MessageUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockSource);
    }

    @BeforeEach
    void setUp() {
        claimService = new PdcNfcClaimServiceImpl(
                properties, wechatPhoneGate, claimRefProtection,
                assetDao, batchDao, productTypeDao, rateLimiter, petService, claimRecordDao);
    }

    @Test
    void confirmSuccessCreatesPetAndClaimRecord() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = createActiveAsset();
        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class))).thenReturn(List.of(asset));
        when(claimRecordDao.findByUserAndRequest(eq(USER_ID), anyString())).thenReturn(Optional.empty());

        PetVO petVO = new PetVO();
        petVO.setId("pet-abc");
        petVO.setPrototype("jade_rabbit");
        when(petService.createEgg(USER_ID, "jade_rabbit")).thenReturn(petVO);
        when(assetDao.markClaimed(eq(1L), eq(1), eq(USER_ID), eq("pet-abc"))).thenReturn(1);

        PdcNfcClaimResultVO result = claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID);

        assertThat(result.claimStatus()).isEqualTo("CLAIMED");
        assertThat(result.pet()).isNotNull();
        verify(petService).createEgg(USER_ID, "jade_rabbit");
        verify(claimRecordDao).insert(any(PdcNfcClaimRecordEntity.class));
        verify(assetDao).markClaimed(1L, 1, USER_ID, "pet-abc");
    }

    @Test
    void petFailureRollsBackClaimAndLeavesAssetActive() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = createActiveAsset();
        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class))).thenReturn(List.of(asset));
        when(claimRecordDao.findByUserAndRequest(eq(USER_ID), anyString())).thenReturn(Optional.empty());
        when(petService.createEgg(USER_ID, "jade_rabbit")).thenThrow(new RuntimeException("pet creation failed"));

        assertThatThrownBy(() -> claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("pet creation failed");

        verify(assetDao, never()).markClaimed(any(), any(), any(), anyString());
        verify(claimRecordDao, never()).insert(any(PdcNfcClaimRecordEntity.class));
    }

    @Test
    void sameRequestIdReturnsOriginalResult() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = createActiveAsset();
        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class))).thenReturn(List.of(asset));

        PdcNfcClaimRecordEntity existingRecord = new PdcNfcClaimRecordEntity();
        existingRecord.setUserId(USER_ID);
        existingRecord.setRequestId(REQUEST_ID.toString());
        existingRecord.setPetId("pet-abc");
        existingRecord.setResult("CLAIMED");
        String expectedFingerprint = computeExpectedFingerprint(1L, REQUEST_ID);
        existingRecord.setRequestFingerprint(expectedFingerprint);

        when(claimRecordDao.findByUserAndRequest(USER_ID, REQUEST_ID.toString()))
                .thenReturn(Optional.of(existingRecord));

        PetVO petVO = new PetVO();
        petVO.setId("pet-abc");
        when(petService.getById(USER_ID, "pet-abc")).thenReturn(petVO);

        PdcNfcClaimResultVO result = claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID);

        assertThat(result.claimStatus()).isEqualTo("CLAIMED");
        verify(petService, never()).createEgg(any(), anyString());
        verify(assetDao, never()).markClaimed(any(), any(), any(), anyString());
    }

    @Test
    void differentRequestIdSameAssetReturnsAlreadyClaimed() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setPrototype("jade_rabbit");
        asset.setStatus("CLAIMED");
        asset.setVersion(2);
        asset.setClaimedUserId(OTHER_USER_ID);
        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class))).thenReturn(List.of(asset));
        when(claimRecordDao.findByUserAndRequest(eq(USER_ID), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.confirm(USER_ID, VALID_CLAIM_REF, DIFFERENT_REQUEST_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode())
                        .isEqualTo(ErrorCode.PDC_NFC_ASSET_ALREADY_CLAIMED));
    }

    @Test
    void sameUserSameAssetNewRequestIdReturnsSelf() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setPrototype("jade_rabbit");
        asset.setStatus("CLAIMED");
        asset.setVersion(2);
        asset.setClaimedUserId(USER_ID);
        asset.setPetId("pet-abc");
        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class))).thenReturn(List.of(asset));
        when(claimRecordDao.findByUserAndRequest(eq(USER_ID), anyString())).thenReturn(Optional.empty());

        PetVO petVO = new PetVO();
        petVO.setId("pet-abc");
        when(petService.getById(USER_ID, "pet-abc")).thenReturn(petVO);

        PdcNfcClaimResultVO result = claimService.confirm(USER_ID, VALID_CLAIM_REF, DIFFERENT_REQUEST_ID);

        assertThat(result.claimStatus()).isEqualTo("CLAIMED_BY_SELF");
        verify(petService, never()).createEgg(any(), anyString());
    }

    @Test
    void invalidClaimRefThrows() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> claimService.confirm(USER_ID, "short", REQUEST_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode())
                        .isEqualTo(ErrorCode.PDC_NFC_ASSET_NOT_FOUND));

        verify(petService, never()).createEgg(any(), anyString());
    }

    @Test
    void nonActiveAssetThrows() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setPrototype("jade_rabbit");
        asset.setStatus("SCRAPPED");
        asset.setVersion(1);
        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class))).thenReturn(List.of(asset));
        when(claimRecordDao.findByUserAndRequest(eq(USER_ID), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode())
                        .isEqualTo(ErrorCode.PDC_NFC_ASSET_UNAVAILABLE));
    }

    @Test
    void phoneNotBoundThrows() {
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID))
                .isInstanceOf(RenException.class);

        verify(petService, never()).createEgg(any(), anyString());
    }

    @Test
    void optimisticLockFailureThrowsInvalidState() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = createActiveAsset();
        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class))).thenReturn(List.of(asset));
        when(claimRecordDao.findByUserAndRequest(eq(USER_ID), anyString())).thenReturn(Optional.empty());

        PetVO petVO = new PetVO();
        petVO.setId("pet-abc");
        when(petService.createEgg(USER_ID, "jade_rabbit")).thenReturn(petVO);
        when(assetDao.markClaimed(1L, 1, USER_ID, "pet-abc")).thenReturn(0);

        assertThatThrownBy(() -> claimService.confirm(USER_ID, VALID_CLAIM_REF, REQUEST_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode())
                        .isEqualTo(ErrorCode.PDC_NFC_INVALID_STATE));
    }

    private PdcNfcAssetEntity createActiveAsset() {
        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setPrototype("jade_rabbit");
        asset.setStatus("ACTIVE");
        asset.setVersion(1);
        return asset;
    }

    private void setupAllGatesEnabled() {
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.isClaimEnabled()).thenReturn(true);
        lenient().when(properties.isReleaseReady()).thenReturn(true);
    }

    private String computeExpectedFingerprint(Long assetId, UUID requestId) {
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
            throw new RuntimeException(e);
        }
    }
}
