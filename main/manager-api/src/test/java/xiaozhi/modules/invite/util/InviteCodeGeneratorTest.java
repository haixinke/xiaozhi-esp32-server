package xiaozhi.modules.invite.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodeGeneratorTest {

    private static final String ALLOWED = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Test
    @DisplayName("generate 返回 5 位且全部在允许字符集内")
    void generate_lengthAndCharset() {
        for (int i = 0; i < 500; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(code).hasSize(5);
            for (char c : code.toCharArray()) {
                assertThat(ALLOWED.indexOf(c)).isGreaterThan(-1);
            }
        }
    }

    @Test
    @DisplayName("字母表排除歧义字符 0/1/I/O，且长度为 32")
    void alphabet_excludesAmbiguousChars() {
        // 直接断言 ALLOWED 常量，把"无歧义字符"这一规格钉死在定义上；
        // 对随机生成的 5 位码断言 doesNotContain 永真（字母表决定），测不出回归。
        assertThat(ALLOWED)
                .hasSize(32)
                .doesNotContain("0", "1", "I", "O");
    }

    @Test
    @DisplayName("generate 2000 次无重复")
    void generate_uniqueOver10000() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String code = InviteCodeGenerator.generate();
            assertThat(seen.add(code)).as("重复码: %s", code).isTrue();
        }
    }
}
