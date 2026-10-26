# 05: DateUtils.parse 显式时区偏移与毫秒精度测试

**What to build:** 在现有 `xiaozhi.common.utils.DateUtilsTest` 中新增一个纯 JUnit 5 单元测试，验证 `DateUtils.parse` 能保留毫秒并正确处理输入中的显式时区偏移。

使用 pattern `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` 解析固定字符串 `2026-10-26T12:34:56.789+08:00`，断言结果严格等于 `Date.from(Instant.parse("2026-10-26T04:34:56.789Z"))`。期望值使用独立的绝对时间，不通过被测方法生成。

这是一项现有行为的补测。先新增测试并运行，记录实际结果；不为制造失败而修改生产代码。测试不得使用当前时间、修改 JVM 默认时区或默认 Locale，也不得依赖 Spring 上下文、数据库、Redis 或网络。沿用现有 JUnit 5 import 和测试命名风格，新增测试使用简洁中文注释说明显式偏移消除了本机时区依赖。

允许修改的文件仅为 `main/manager-api/src/test/java/xiaozhi/common/utils/DateUtilsTest.java` 和本 issue 的状态、验收勾选及评论。不修改生产代码、构建配置、依赖、其他测试、其他 issue 或 feature spec。

验证先运行 `DateUtilsTest` 定向测试，再按 `.sandcastle/implement-prompt.md` 中现有 manager-api 命令运行全量 `clean verify`。不得增加排除测试、跳过测试或降低覆盖率门禁。

**Blocked by:** None (can start immediately)

**Status:** open

- [ ] 新增测试解析指定的带 `+08:00` 偏移和 `.789` 毫秒的字符串
- [ ] 使用指定 UTC 绝对时间做精确相等断言，同时验证时区偏移换算和毫秒保留
- [ ] 新增测试不依赖当前时间、本机默认时区或外部服务，且不修改全局时区或 Locale
- [ ] `DateUtilsTest` 定向测试通过，manager-api 全量 `clean verify` 通过
- [ ] 变更限定在允许的测试文件和本 issue；评论记录实际执行的验证命令及结果

## Comments

用于验证本次 sequential-reviewer 改造后的真实流程：implementer 提交、单 issue 审查、host 复验、合并和 final-review。流程结果由 runner 的日志和结构化报告证明；本 issue 不预先宣称任何审查或验证通过。
