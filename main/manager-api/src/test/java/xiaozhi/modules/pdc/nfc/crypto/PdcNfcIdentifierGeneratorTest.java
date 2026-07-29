package xiaozhi.modules.pdc.nfc.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PdcNfcIdentifierGeneratorTest {

    private PdcNfcIdentifierGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PdcNfcIdentifierGenerator();
    }

    @Test
    void newClaimRefMatchesBase64UrlPattern() {
        for (int i = 0; i < 100; i++) {
            String ref = generator.newClaimRef();
            assertThat(ref).matches("[A-Za-z0-9_-]{22}");
        }
    }

    @Test
    void newClaimRefIsUnique() {
        Set<String> refs = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            refs.add(generator.newClaimRef());
        }
        assertThat(refs).hasSize(10000);
    }

    @Test
    void newWechatSnStartsWithEbAndIs28Chars() {
        for (int i = 0; i < 100; i++) {
            String sn = generator.newWechatSn();
            assertThat(sn).startsWith("EB");
            assertThat(sn).hasSize(28); // "EB" + 26 chars
        }
    }

    @Test
    void newWechatSnUsesCrockfordBase32() {
        for (int i = 0; i < 100; i++) {
            String sn = generator.newWechatSn();
            String body = sn.substring(2); // remove "EB" prefix
            assertThat(body).matches("[0-9A-HJ-NP-TV-Z]{26}");
        }
    }

    @Test
    void newWechatSnIsUnique() {
        Set<String> sns = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            sns.add(generator.newWechatSn());
        }
        assertThat(sns).hasSize(10000);
    }
}
