package xiaozhi.modules.pdc.nfc.crypto;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * NFC 标识符生成器。
 * - claimRef: 16 随机字节 → Base64URL 无填充 → 22 字符
 * - wechatSn: "EB" + 26 位 Crockford Base32
 */
@Component
public class PdcNfcIdentifierGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /**
     * 生成 22 字符的 Base64URL claimRef。
     */
    public String newClaimRef() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * 生成 "EB" + 26 位 Crockford Base32 的 wechatSn。
     */
    public String newWechatSn() {
        byte[] bytes = new byte[17];
        SECURE_RANDOM.nextBytes(bytes);
        String encoded = crockfordBase32Encode(bytes);
        return "EB" + encoded.substring(0, 26);
    }

    private static String crockfordBase32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(CROCKFORD_ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(CROCKFORD_ALPHABET.charAt(index));
        }
        return sb.toString();
    }
}
