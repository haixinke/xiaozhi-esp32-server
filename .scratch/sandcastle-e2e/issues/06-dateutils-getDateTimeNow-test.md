# 06: DateUtils.getDateTimeNow 与毫秒 pattern 单元测试

**What to build:** 在现有 `xiaozhi.common.utils.DateUtilsTest` 中追加纯 JUnit 5 单元测试，覆盖 `getDateTimeNow()`、`getDateTimeNow(String pattern)` 与 `DATE_TIME_MILLIS_PATTERN`。覆盖以下用例：

1. `getDateTimeNow()` 返回的字符串匹配正则 `^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$`（断格式不断具体时间值）
2. `getDateTimeNow(DateUtils.DATE_PATTERN)` 返回的字符串匹配正则 `^\d{4}-\d{2}-\d{2}$`
3. 使用 `DateUtils.DATE_TIME_MILLIS_PATTERN` 解析固定字符串 `2026-10-23 14:05:06.789`，再用同一 pattern 格式化，结果仍为 `2026-10-23 14:05:06.789`（parse + format round-trip 不依赖本机时区）

只修改 `main/manager-api/src/test/java/xiaozhi/common/utils/DateUtilsTest.java` 和本 issue 的状态、验收勾选及评论。不修改生产代码、构建配置、依赖、其他测试、其他 issue 或 feature spec。不依赖 Spring 上下文、数据库、Redis、网络，不修改 JVM 默认时区或 Locale，不硬编码除上述 round-trip 输入外的固定时间值。沿用现有 JUnit 5 import 与测试命名风格，新增测试用简洁中文注释说明断格式/round-trip 的原因。

验证：先运行 `DateUtilsTest` 定向测试，再按 `.sandcastle/implement-prompt.md` 中现有 manager-api 命令运行全量 `clean verify`。不得增加排除测试、跳过测试或降低覆盖率门禁。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 新增 `getDateTimeNow()` 默认 pattern 格式断言
- [x] 新增 `getDateTimeNow(String)` 自定义 pattern 格式断言
- [x] 新增 `DATE_TIME_MILLIS_PATTERN` parse + format round-trip 断言
- [x] `DateUtilsTest` 定向测试通过，manager-api 全量 `clean verify` 通过
- [x] 变更限定在允许的测试文件和本 issue；评论记录实际执行的验证命令及结果

## Comments

本 issue 用于验证 Sandcastle sequential-reviewer 全流程（implementer 提交、单 issue 审查、host 复验合并、final-review）。流程结果以 runner 日志与结构化报告为准，本 issue 不预先宣称任何环节通过。

### RALPH 实现记录（2026-10-27）

在 `main/manager-api/src/test/java/xiaozhi/common/utils/DateUtilsTest.java` 追加 3 个纯 JUnit 5 测试：`getDateTimeNow_defaultPattern_returnsDateTimeFormat`、`getDateTimeNow_datePattern_returnsDateFormat`、`parseAndFormat_millisPattern_roundTrip`，并更新类 Javadoc 覆盖范围说明。未改动生产代码、构建配置或其他文件。

实际执行的验证命令及结果：
1. 定向：`mvn -B -ntp -f main/manager-api/pom.xml -Dmaven.repo.local=/home/agent/.m2/repository -DskipTests=false -Dtest=DateUtilsTest test` → Tests run: 18, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS
2. 全量：`mvn -B -ntp -f main/manager-api/pom.xml -Dmaven.repo.local=/home/agent/.m2/repository -DskipTests=false -Dmaven.test.skip=false -DfailIfNoTests=true -Dtest='!xiaozhi.modules.pdc.nfc.service.PdcNfcTrustReleaseIntegrationTest,!xiaozhi.modules.pdc.nfc.service.PdcNfcClaimInviteReplacementIntegrationTest,!xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultImportIntegrationTest' clean verify` → Tests run: 976, Failures: 0, Errors: 0, Skipped: 0，JaCoCo 覆盖率检查通过，BUILD SUCCESS