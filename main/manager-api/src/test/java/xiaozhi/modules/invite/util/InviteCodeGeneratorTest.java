package xiaozhi.modules.invite.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodeGeneratorTest {

    private static final String ALLOWED = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Test
    @DisplayName("generate 返回 8 位且全部在允许字符集内")
    void generate_lengthAndCharset() {
        for (int i = 0; i < 500; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(code).hasSize(8);
            for (char c : code.toCharArray()) {
                assertThat(ALLOWED.indexOf(c)).isGreaterThan(-1);
            }
        }
    }

    @Test
    @DisplayName("generate 不包含歧义字符 0/1/I/O")
    void generate_noAmbiguousChars() {
        for (int i = 0; i < 500; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(code).doesNotContain("0").doesNotContain("1")
                    .doesNotContain("O").doesNotContain("I");
        }
    }

    @Test
    @DisplayName("generate 10000 次无重复")
    void generate_uniqueOver10000() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(seen.add(code)).as("重复码: %s", code).isTrue();
        }
    }
}
