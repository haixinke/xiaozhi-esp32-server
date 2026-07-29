package xiaozhi.modules.pdc.nfc.crypto;

import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * claimRef 保护：AES-256-GCM 加密 + HMAC-SHA-256 查找哈希。
 * assetId 作为 AAD 绑定，防止跨资产密文重放。
 */
@Component
public class ClaimRefProtection {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final PdcNfcProperties properties;

    public ClaimRefProtection(PdcNfcProperties properties) {
        this.properties = properties;
    }

    public ProtectedClaimRef protect(Long assetId, String claimRef) {
        validateCryptoConfig();
        byte[] nonce = new byte[NONCE_BYTES];
        new java.security.SecureRandom().nextBytes(nonce);
        byte[] aad = Long.toUnsignedString(assetId).getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = activeAesKeyBytes();
        byte[] ciphertext = aesGcmEncrypt(aesKey, nonce, aad, claimRef.getBytes(StandardCharsets.UTF_8));
        return new ProtectedClaimRef(
                hmacHex(activeHmacKeyBytes(), claimRef),
                new EncryptedField(properties.getClaimRef().getActiveVersion(), nonce, ciphertext)
        );
    }

    public String decrypt(Long assetId, EncryptedField field) {
        validateCryptoConfig();
        byte[] aad = Long.toUnsignedString(assetId).getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = resolveAesKey(field.keyVersion());
        byte[] plaintext = aesGcmDecrypt(aesKey, field.nonce(), aad, field.ciphertext());
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public List<String> lookupHashes(String claimRef) {
        validateCryptoConfig();
        List<String> hashes = new ArrayList<>();
        hashes.add(hmacHex(activeHmacKeyBytes(), claimRef));
        if (hasPreviousKey()) {
            hashes.add(hmacHex(previousHmacKeyBytes(), claimRef));
        }
        return hashes;
    }

    /**
     * 加密 Scheme URL（AES-256-GCM，assetId 作为 AAD），并计算 SHA-256 摘要。
     * 与 claimRef 加密复用同一密钥版本，但产出独立的 nonce/ciphertext/sha256。
     */
    public SchemeEncryption encryptScheme(Long assetId, String scheme) {
        validateCryptoConfig();
        byte[] nonce = new byte[NONCE_BYTES];
        new java.security.SecureRandom().nextBytes(nonce);
        byte[] aad = Long.toUnsignedString(assetId).getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = activeAesKeyBytes();
        byte[] ciphertext = aesGcmEncrypt(aesKey, nonce, aad, scheme.getBytes(StandardCharsets.UTF_8));
        return new SchemeEncryption(
                sha256Hex(scheme),
                new EncryptedField(properties.getClaimRef().getActiveVersion(), nonce, ciphertext)
        );
    }

    // --- internal ---

    private void validateCryptoConfig() {
        PdcNfcProperties.ClaimRef cr = properties.getClaimRef();
        if (cr.getActiveVersion() == null || cr.getActiveVersion().isBlank()) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED);
        }
        if (cr.getActiveHmacKeyBase64() == null || cr.getActiveHmacKeyBase64().isBlank()) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED);
        }
        byte[] aesKey = decodeBase64(cr.getActiveAesKeyBase64());
        if (aesKey == null || aesKey.length != 32) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED);
        }
        byte[] hmacKey = decodeBase64(cr.getActiveHmacKeyBase64());
        if (hmacKey == null || hmacKey.length == 0) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED);
        }
        if (java.util.Arrays.equals(aesKey, hmacKey)) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED);
        }
    }

    private boolean hasPreviousKey() {
        PdcNfcProperties.ClaimRef cr = properties.getClaimRef();
        return cr.getPreviousVersion() != null && !cr.getPreviousVersion().isBlank()
                && cr.getPreviousHmacKeyBase64() != null && !cr.getPreviousHmacKeyBase64().isBlank();
    }

    private byte[] activeAesKeyBytes() {
        return decodeBase64(properties.getClaimRef().getActiveAesKeyBase64());
    }

    private byte[] activeHmacKeyBytes() {
        return decodeBase64(properties.getClaimRef().getActiveHmacKeyBase64());
    }

    private byte[] previousHmacKeyBytes() {
        return decodeBase64(properties.getClaimRef().getPreviousHmacKeyBase64());
    }

    private byte[] resolveAesKey(String keyVersion) {
        PdcNfcProperties.ClaimRef cr = properties.getClaimRef();
        if (cr.getActiveVersion().equals(keyVersion)) {
            return decodeBase64(cr.getActiveAesKeyBase64());
        }
        if (cr.getPreviousVersion() != null && cr.getPreviousVersion().equals(keyVersion)) {
            return decodeBase64(cr.getPreviousAesKeyBase64());
        }
        throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED);
    }

    private byte[] aesGcmEncrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED, e);
        }
    }

    private byte[] aesGcmDecrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED, e);
        }
    }

    private String hmacHex(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED, e);
        }
    }

    private static byte[] decodeBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        return Base64.getDecoder().decode(base64);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RenException(ErrorCode.PDC_NFC_CRYPTO_NOT_CONFIGURED, e);
        }
    }
}
