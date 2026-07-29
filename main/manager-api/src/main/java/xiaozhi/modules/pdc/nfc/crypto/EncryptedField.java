package xiaozhi.modules.pdc.nfc.crypto;

/**
 * 加密字段值对象，包含密钥版本、nonce 和密文。
 */
public record EncryptedField(String keyVersion, byte[] nonce, byte[] ciphertext) {

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }
}
