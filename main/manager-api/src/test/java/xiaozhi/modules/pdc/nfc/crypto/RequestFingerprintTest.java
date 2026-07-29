package xiaozhi.modules.pdc.nfc.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

    private RequestFingerprint fingerprint;

    @BeforeEach
    void setUp() {
        fingerprint = new RequestFingerprint();
    }

    @Test
    void sameContentProducesSameFingerprint() {
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("b", "value-b");
        data1.put("a", "value-a");

        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("a", "value-a");
        data2.put("b", "value-b");

        String fp1 = fingerprint.sha256Canonical(data1);
        String fp2 = fingerprint.sha256Canonical(data2);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).matches("[0-9a-f]{64}");
    }

    @Test
    void differentContentProducesDifferentFingerprint() {
        Map<String, Object> data1 = Map.of("key", "value1");
        Map<String, Object> data2 = Map.of("key", "value2");

        String fp1 = fingerprint.sha256Canonical(data1);
        String fp2 = fingerprint.sha256Canonical(data2);

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    void mapOrderDoesNotAffectFingerprint() {
        Map<String, Object> insertionOrder = new LinkedHashMap<>();
        insertionOrder.put("zebra", 1);
        insertionOrder.put("apple", 2);
        insertionOrder.put("mango", 3);

        Map<String, Object> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("mango", 3);
        reverseOrder.put("apple", 2);
        reverseOrder.put("zebra", 1);

        String fp1 = fingerprint.sha256Canonical(insertionOrder);
        String fp2 = fingerprint.sha256Canonical(reverseOrder);

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void nestedMapOrderDoesNotAffectFingerprint() {
        Map<String, Object> data1 = new LinkedHashMap<>();
        Map<String, Object> nested1 = new LinkedHashMap<>();
        nested1.put("y", "yval");
        nested1.put("x", "xval");
        data1.put("nested", nested1);

        Map<String, Object> data2 = new LinkedHashMap<>();
        Map<String, Object> nested2 = new TreeMap<>();
        nested2.put("x", "xval");
        nested2.put("y", "yval");
        data2.put("nested", nested2);

        String fp1 = fingerprint.sha256Canonical(data1);
        String fp2 = fingerprint.sha256Canonical(data2);

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void nullValueHandledGracefully() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", null);

        String fp = fingerprint.sha256Canonical(data);
        assertThat(fp).matches("[0-9a-f]{64}");
    }

    @Test
    void emptyMapProducesStableFingerprint() {
        String fp1 = fingerprint.sha256Canonical(Map.of());
        String fp2 = fingerprint.sha256Canonical(new TreeMap<>());

        assertThat(fp1).isEqualTo(fp2);
    }
}
