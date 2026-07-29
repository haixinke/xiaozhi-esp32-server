package xiaozhi.modules.pdc.nfc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFC 敏感日志契约测试。
 * <p>
 * 扫描 {@code xiaozhi/modules/pdc/nfc/} 下所有 Java 源文件，
 * 检查 {@code log.*} 调用中不得直接输出敏感字段（密钥、密文、token 等）。
 * <p>
 * 本测试基于文件静态扫描（正则匹配），非运行时日志捕获。
 */
@DisplayName("NFC 敏感日志契约测试")
class PdcNfcSensitiveLoggingContractTest {

    /** 禁止出现在 log.* 语句中的敏感字段名 */
    private static final List<String> SENSITIVE_FIELDS = List.of(
            "claimRef",
            "schemeCiphertext",
            "accessToken",
            "appSecret",
            "claimRefNonce",
            "claimRefCiphertext"
    );

    /** 匹配 log.info / log.warn / log.error / log.debug 调用 */
    private static final Pattern LOG_CALL_PATTERN =
            Pattern.compile("log\\.(info|warn|error|debug)\\s*\\(");

    /** 源码根目录 */
    private static final Path SRC_ROOT = Paths.get(
            "src/main/java/xiaozhi/modules/pdc/nfc");

    @Test
    @DisplayName("NFC 模块 log.* 语句不得输出敏感字段")
    void noSensitiveFieldsInLogStatements() throws IOException {
        assertThat(Files.isDirectory(SRC_ROOT))
                .as("源码目录 %s 必须存在", SRC_ROOT)
                .isTrue();

        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SRC_ROOT)) {
            List<Path> javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            for (Path file : javaFiles) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    Matcher matcher = LOG_CALL_PATTERN.matcher(line);
                    if (matcher.find()) {
                        // 检查该行及后续拼接行（多行 log 语句）
                        StringBuilder logStatement = new StringBuilder(line);
                        // 向上检查可能的多行拼接（向前回溯括号未闭合的情况）
                        int closeCount = countChar(logStatement.toString(), ')');
                        int openCount = countChar(logStatement.toString(), '(');
                        int j = i + 1;
                        while (openCount > closeCount && j < lines.size() && j < i + 5) {
                            logStatement.append(" ").append(lines.get(j));
                            closeCount = countChar(logStatement.toString(), ')');
                            openCount = countChar(logStatement.toString(), '(');
                            j++;
                        }

                        String stmt = logStatement.toString();
                        for (String field : SENSITIVE_FIELDS) {
                            if (stmt.contains(field)) {
                                violations.add(String.format(
                                        "%s:%d -> 敏感字段 '%s' 出现在 log 语句中",
                                        file.getFileName(), i + 1, field));
                            }
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("发现 log.* 语句中包含敏感字段，请使用脱敏方法（如 mask()）替代直接输出")
                .isEmpty();
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }
}
