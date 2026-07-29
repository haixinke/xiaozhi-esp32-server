package xiaozhi.modules.pdc.nfc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcClaimRecordDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcProductTypeEntity;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcClaimServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimPreviewVO;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.wechat.service.WechatPhoneGate;

@ExtendWith(MockitoExtension.class)
class PdcNfcClaimPreviewServiceTest {

    private static final Long USER_ID = 100L;
    private static final String VALID_CLAIM_REF = "abcdefghij1234567890_-";

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
    void previewNeverCreatesPetOrClaimRecord() {
        setupAllGatesEnabled();
        lenient().when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        lenient().when(claimRefProtection.lookupHashes(anyString())).thenReturn(List.of("hash1"));
        lenient().when(assetDao.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        claimService.preview(USER_ID, VALID_CLAIM_REF);

        verifyNoInteractions(petService);
        verifyNoInteractions(claimRecordDao);
    }

    @Test
    void noPhoneBindingReturnsUnavailable() {
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(false);

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void featureDisabledReturnsUnavailable() {
        when(properties.isEnabled()).thenReturn(false);
        lenient().when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void claimDisabledReturnsUnavailable() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isClaimEnabled()).thenReturn(false);
        lenient().when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void releaseNotReadyReturnsUnavailable() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isClaimEnabled()).thenReturn(true);
        when(properties.isReleaseReady()).thenReturn(false);
        lenient().when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void invalidClaimRefReturnsUnavailable() {
        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, "short");

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void invalidClaimRefNullReturnsUnavailable() {
        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, null);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void activeAssetReturnsClaimable() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setBatchId(10L);
        asset.setPrototype("jade_rabbit");
        asset.setStatus("ACTIVE");

        when(assetDao.selectList(any(Wrapper.class))).thenReturn(List.of(asset));

        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setProductTypeId(100L);
        when(batchDao.selectById(10L)).thenReturn(batch);

        PdcNfcProductTypeEntity productType = new PdcNfcProductTypeEntity();
        productType.setId(100L);
        productType.setTypeName("翡翠玉兔");
        when(productTypeDao.selectById(100L)).thenReturn(productType);

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_CLAIMABLE);
        assertThat(result.productName()).isEqualTo("翡翠玉兔");
        assertThat(result.prototype()).isEqualTo("jade_rabbit");
        assertThat(result.pet()).isNull();
    }

    @Test
    void claimedBySelfReturnsPetInfo() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setBatchId(10L);
        asset.setPrototype("jade_rabbit");
        asset.setStatus("CLAIMED");
        asset.setClaimedUserId(USER_ID);
        asset.setPetId("pet-123");

        when(assetDao.selectList(any(Wrapper.class))).thenReturn(List.of(asset));

        PetVO petVO = new PetVO();
        petVO.setId("pet-123");
        petVO.setNickname("小白兔");
        when(petService.getById(USER_ID, "pet-123")).thenReturn(petVO);

        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setProductTypeId(100L);
        when(batchDao.selectById(10L)).thenReturn(batch);

        PdcNfcProductTypeEntity productType = new PdcNfcProductTypeEntity();
        productType.setTypeName("翡翠玉兔");
        when(productTypeDao.selectById(100L)).thenReturn(productType);

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_CLAIMED_BY_SELF);
        assertThat(result.pet()).isNotNull();
    }

    @Test
    void claimedByOtherReturnsNoPet() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setBatchId(10L);
        asset.setPrototype("jade_rabbit");
        asset.setStatus("CLAIMED");
        asset.setClaimedUserId(999L);

        when(assetDao.selectList(any(Wrapper.class))).thenReturn(List.of(asset));

        PdcNfcBatchEntity batch = new PdcNfcBatchEntity();
        batch.setId(10L);
        batch.setProductTypeId(100L);
        when(batchDao.selectById(10L)).thenReturn(batch);

        PdcNfcProductTypeEntity productType = new PdcNfcProductTypeEntity();
        productType.setTypeName("翡翠玉兔");
        when(productTypeDao.selectById(100L)).thenReturn(productType);

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_CLAIMED_BY_OTHER);
        assertThat(result.pet()).isNull();
        verify(petService, never()).getById(any(), anyString());
    }

    @Test
    void scrappedAssetReturnsUnavailable() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setStatus("SCRAPPED");

        when(assetDao.selectList(any(Wrapper.class))).thenReturn(List.of(asset));

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void nonActiveAssetReturnsUnavailable() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);
        when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));

        PdcNfcAssetEntity asset = new PdcNfcAssetEntity();
        asset.setId(1L);
        asset.setStatus("CREATED");

        when(assetDao.selectList(any(Wrapper.class))).thenReturn(List.of(asset));

        PdcNfcClaimPreviewVO result = claimService.preview(USER_ID, VALID_CLAIM_REF);

        assertThat(result.claimStatus()).isEqualTo(PdcNfcClaimPreviewVO.STATUS_UNAVAILABLE);
    }

    @Test
    void rateLimitExceededThrowsException() {
        setupAllGatesEnabled();
        when(wechatPhoneGate.hasBoundWechatPhone(USER_ID)).thenReturn(true);

        org.mockito.Mockito.doThrow(new RenException(10517))
                .when(rateLimiter).checkPreviewUserRate(USER_ID);

        assertThatThrownBy(() -> claimService.preview(USER_ID, VALID_CLAIM_REF))
                .isInstanceOf(RenException.class);
    }

    private void setupAllGatesEnabled() {
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.isClaimEnabled()).thenReturn(true);
        lenient().when(properties.isReleaseReady()).thenReturn(true);
    }
}
