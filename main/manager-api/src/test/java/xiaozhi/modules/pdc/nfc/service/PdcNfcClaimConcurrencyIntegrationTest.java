package xiaozhi.modules.pdc.nfc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.pdc.nfc.crypto.ClaimRefProtection;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcClaimRecordDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcProductTypeDao;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcManualWriteService;
import xiaozhi.modules.pdc.nfc.service.impl.PdcNfcClaimServiceImpl;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimResultVO;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.wechat.service.WechatPhoneGate;

@ExtendWith(MockitoExtension.class)
class PdcNfcClaimConcurrencyIntegrationTest {

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
    @Mock private PdcNfcManualWriteService manualWriteService;

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
                assetDao, batchDao, productTypeDao, rateLimiter, petService, claimRecordDao,
                manualWriteService);
    }

    @Test
    void twoUsersOneWins() throws Exception {
        Long userA = 100L;
        Long userB = 200L;
        UUID reqA = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID reqB = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.isClaimEnabled()).thenReturn(true);
        lenient().when(properties.isReleaseReady()).thenReturn(true);
        lenient().when(wechatPhoneGate.hasBoundWechatPhone(any())).thenReturn(true);
        lenient().when(claimRefProtection.lookupHashes(VALID_CLAIM_REF)).thenReturn(List.of("hash1"));
        lenient().when(claimRecordDao.findByUserAndRequest(any(), anyString())).thenReturn(Optional.empty());

        AtomicInteger lockCounter = new AtomicInteger(1);

        PdcNfcAssetEntity assetForA = new PdcNfcAssetEntity();
        assetForA.setId(1L);
        assetForA.setPrototype("jade_rabbit");
        assetForA.setStatus("ACTIVE");
        assetForA.setVersion(1);

        PdcNfcAssetEntity assetForB = new PdcNfcAssetEntity();
        assetForB.setId(1L);
        assetForB.setPrototype("jade_rabbit");
        assetForB.setStatus("ACTIVE");
        assetForB.setVersion(1);

        when(assetDao.selectByClaimHashesForUpdate(any(Collection.class)))
                .thenReturn(List.of(assetForA))
                .thenReturn(List.of(assetForB));

        PetVO petA = new PetVO();
        petA.setId("pet-A");
        PetVO petB = new PetVO();
        petB.setId("pet-B");
        when(petService.createEgg(eq(userA), anyString())).thenReturn(petA);
        when(petService.createEgg(eq(userB), anyString())).thenReturn(petB);

        when(assetDao.markClaimed(eq(1L), eq(1), eq(userA), anyString()))
                .thenAnswer(inv -> lockCounter.compareAndSet(1, 0) ? 1 : 0);
        when(assetDao.markClaimed(eq(1L), eq(1), eq(userB), anyString()))
                .thenReturn(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<PdcNfcClaimResultVO> futureA = executor.submit(() -> {
            startLatch.await();
            return claimService.confirm(userA, VALID_CLAIM_REF, reqA);
        });

        Future<PdcNfcClaimResultVO> futureB = executor.submit(() -> {
            startLatch.await();
            return claimService.confirm(userB, VALID_CLAIM_REF, reqB);
        });

        startLatch.countDown();

        int successCount = 0;
        int failCount = 0;
        try {
            PdcNfcClaimResultVO resultA = futureA.get();
            assertThat(resultA.claimStatus()).isEqualTo("CLAIMED");
            successCount++;
        } catch (Exception e) {
            failCount++;
        }

        try {
            PdcNfcClaimResultVO resultB = futureB.get();
            assertThat(resultB.claimStatus()).isEqualTo("CLAIMED");
            successCount++;
        } catch (Exception e) {
            failCount++;
        }

        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(failCount).isEqualTo(1);
    }
}
