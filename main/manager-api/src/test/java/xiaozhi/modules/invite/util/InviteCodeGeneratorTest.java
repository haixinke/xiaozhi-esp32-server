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
    @DisplayName("generate 2000 次的重复数在生日碰撞预期内")
    void generate_duplicatesWithinBirthdayBound() {
        // 5 位码的字母表有 32 个字符，码空间 32^5 ≈ 3355 万；
        // 2000 次抽样的生日碰撞期望约 0.06 次，硬断言"零重复"约 6% 概率随机失败。
        // 因此改为断言重复次数不超过 5（λ=0.06 时 P(X>=6) ≈ 5e-11），
        // 既消除 flaky，又能在生成器退化（重复率上升）时仍然报警。
        final int samples = 2000;
        final int maxDuplicates = 5;
        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        for (int i = 0; i < samples; i++) {
            String code = InviteCodeGenerator.generate();
            if (!seen.add(code)) {
                duplicates++;
            }
        }
        assertThat(duplicates).as("重复数超过生日碰撞预期").isLessThanOrEqualTo(maxDuplicates);
    }
}
