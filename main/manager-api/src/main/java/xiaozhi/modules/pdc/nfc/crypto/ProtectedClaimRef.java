package xiaozhi.modules.pdc.nfc.crypto;

/**
 * claimRef 保护结果，包含 HMAC 查找哈希和加密字段。
 */
public record ProtectedClaimRef(String lookupHash, EncryptedField encrypted) {
}
