package xiaozhi.modules.pdc.nfc.crypto;

/**
 * Scheme 加密结果：包含 SHA-256 摘要和 AES-256-GCM 加密字段。
 */
public record SchemeEncryption(String sha256, EncryptedField encrypted) {
}
