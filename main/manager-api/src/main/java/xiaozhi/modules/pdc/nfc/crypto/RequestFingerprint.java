package xiaozhi.modules.pdc.nfc.crypto;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 请求指纹：使用 Jackson 排序属性和 map key 后做 SHA-256。
 * 不保存原始请求正文。
 */
@Component
public class RequestFingerprint {

    private final ObjectMapper sortedMapper;

    public RequestFingerprint() {
        this.sortedMapper = new ObjectMapper();
        this.sortedMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.sortedMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public String sha256Canonical(Object value) {
        try {
            String json = sortedMapper.writeValueAsString(value);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute request fingerprint", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
