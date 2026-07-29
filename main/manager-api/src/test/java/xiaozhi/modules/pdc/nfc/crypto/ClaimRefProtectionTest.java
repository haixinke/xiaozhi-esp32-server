package xiaozhi.modules.pdc.nfc.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaimRefProtectionTest {

    private ClaimRefProtection protection;
    private PdcNfcIdentifierGenerator generator;
    private PdcNfcProperties properties;

    @BeforeAll
    static void initMessageSource() throws Exception {
        MessageSource mockSource = mock(MessageSource.class);
        when(mockSource.getMessage(anyString(), any(), any(), any(Locale.class)))
                .thenReturn("mock message");
        Field field = MessageUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockSource);
    }

    @BeforeEach
    void setUp() {
        properties = new PdcNfcProperties();
        PdcNfcProperties.ClaimRef cr = new PdcNfcProperties.ClaimRef();
        cr.setActiveVersion("v1");
        cr.setActiveHmacKeyBase64("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=");
        cr.setActiveAesKeyBase64("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        cr.setPreviousVersion("v0");
        cr.setPreviousHmacKeyBase64("BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ=");
        cr.setPreviousAesKeyBase64("AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=");
        properties.setClaimRef(cr);

        protection = new ClaimRefProtection(properties);
        generator = new PdcNfcIdentifierGenerator();
    }

    @Test
    void encryptsWithAssetAadAndReadsPreviousKey() {
        String ref = generator.newClaimRef();
        ProtectedClaimRef protectedRef = protection.protect(101L, ref);

        assertThat(ref).matches("[A-Za-z0-9_-]{22}");
        assertThat(protection.decrypt(101L, protectedRef.encrypted())).isEqualTo(ref);
        assertThatThrownBy(() -> protection.decrypt(102L, protectedRef.encrypted()))
                .isInstanceOf(RenException.class);
        assertThat(protection.lookupHashes(ref)).contains(protectedRef.lookupHash());
    }

    @Test
    void nonceDiffersEachEncryption() {
        String ref = "test-claim-ref-123";
        ProtectedClaimRef first = protection.protect(1L, ref);
        ProtectedClaimRef second = protection.protect(1L, ref);

        assertThat(first.encrypted().nonce()).isNotEqualTo(second.encrypted().nonce());
        assertThat(first.encrypted().ciphertext()).isNotEqualTo(second.encrypted().ciphertext());
        assertThat(first.lookupHash()).isEqualTo(second.lookupHash());
    }

    @Test
    void ciphertextTamperFails() {
        String ref = "tamper-test-ref";
        ProtectedClaimRef protectedRef = protection.protect(1L, ref);
        byte[] tampered = protectedRef.encrypted().ciphertext().clone();
        tampered[0] ^= 0xFF;
        EncryptedField tamperedField = new EncryptedField(
                protectedRef.encrypted().keyVersion(),
                protectedRef.encrypted().nonce(),
                tampered);

        assertThatThrownBy(() -> protection.decrypt(1L, tamperedField))
                .isInstanceOf(RenException.class);
    }

    @Test
    void wrongAadFails() {
        String ref = "aad-test-ref";
        ProtectedClaimRef protectedRef = protection.protect(200L, ref);

        assertThatThrownBy(() -> protection.decrypt(201L, protectedRef.encrypted()))
                .isInstanceOf(RenException.class);
    }

    @Test
    void lookupHashesContainsActiveAndPrevious() {
        String ref = "lookup-test-ref";
        List<String> hashes = protection.lookupHashes(ref);

        assertThat(hashes).hasSize(2);
        ProtectedClaimRef activeProtected = protection.protect(1L, ref);
        assertThat(hashes).contains(activeProtected.lookupHash());
    }

    @Test
    void previousAesKeyCanDecrypt() {
        String ref = "previous-key-test";
        // 使用 previous key 版本加密
        byte[] nonce = new byte[12];
        new java.security.SecureRandom().nextBytes(nonce);
        byte[] aad = "1".getBytes();
        byte[] previousAesKey = java.util.Base64.getDecoder().decode("AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=");
        byte[] ciphertext = aesGcmEncrypt(previousAesKey, nonce, aad, ref.getBytes());
        EncryptedField field = new EncryptedField("v0", nonce, ciphertext);

        assertThat(protection.decrypt(1L, field)).isEqualTo(ref);
    }

    @Test
    void hmacAndAesSameKeyRejected() {
        PdcNfcProperties badProps = new PdcNfcProperties();
        PdcNfcProperties.ClaimRef badCr = new PdcNfcProperties.ClaimRef();
        badCr.setActiveVersion("v1");
        badCr.setActiveHmacKeyBase64("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        badCr.setActiveAesKeyBase64("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        badProps.setClaimRef(badCr);
        ClaimRefProtection badProtection = new ClaimRefProtection(badProps);

        assertThatThrownBy(() -> badProtection.protect(1L, "test"))
                .isInstanceOf(RenException.class);
    }

    @Test
    void tenThousandGenerationsNoDuplicate() {
        Set<String> refs = new HashSet<>();
        Set<String> sns = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            refs.add(generator.newClaimRef());
            sns.add(generator.newWechatSn());
        }
        assertThat(refs).hasSize(10000);
        assertThat(sns).hasSize(10000);
    }

    @Test
    void emptyVersionRejected() {
        PdcNfcProperties emptyProps = new PdcNfcProperties();
        PdcNfcProperties.ClaimRef emptyCr = new PdcNfcProperties.ClaimRef();
        emptyProps.setClaimRef(emptyCr);
        ClaimRefProtection emptyProtection = new ClaimRefProtection(emptyProps);

        assertThatThrownBy(() -> emptyProtection.protect(1L, "test"))
                .isInstanceOf(RenException.class);
    }

    @Test
    void invalidAesKeyLengthRejected() {
        PdcNfcProperties badProps = new PdcNfcProperties();
        PdcNfcProperties.ClaimRef badCr = new PdcNfcProperties.ClaimRef();
        badCr.setActiveVersion("v1");
        badCr.setActiveHmacKeyBase64("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=");
        badCr.setActiveAesKeyBase64("AQEB"); // only 3 bytes
        badProps.setClaimRef(badCr);
        ClaimRefProtection badProtection = new ClaimRefProtection(badProps);

        assertThatThrownBy(() -> badProtection.protect(1L, "test"))
                .isInstanceOf(RenException.class);
    }

    private byte[] aesGcmEncrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
