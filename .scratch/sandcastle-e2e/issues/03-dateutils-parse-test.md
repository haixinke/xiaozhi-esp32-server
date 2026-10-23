# 03: DateUtils.parse 纯单元测试

**What to build:** 在现有 `xiaozhi.common.utils.DateUtilsTest` 中追加 `parse(String, String)` 的纯 JUnit 5 单元测试。覆盖：

1. 使用 `DateUtils.DATE_TIME_PATTERN` 解析 `2026-10-23 14:05:06`，再用同一 pattern 格式化，结果仍为 `2026-10-23 14:05:06`
2. 使用 `DateUtils.DATE_PATTERN` 解析非法值 `not-a-date`，返回 `null`

只修改 `main/manager-api/src/test/java/xiaozhi/common/utils/DateUtilsTest.java` 和本 issue 的状态、评论。不修改生产代码，不依赖 Spring 上下文、数据库、Redis、网络或固定系统时区。沿用现有 JUnit 5 import 与命名风格。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [ ] 新增有效日期时间 round-trip 测试
- [ ] 新增非法日期返回 `null` 测试
- [ ] manager-api 全量 `clean verify` 通过

## Comments

- RALPH 迭代完成：在 `DateUtilsTest` 中追加两个纯单元测试——`parse_validDateTime_roundTrip`（用 `DATE_TIME_PATTERN` 解析 `2026-10-23 14:05:06` 再格式化 round-trip）与 `parse_invalidDate_returnsNull`（用 `DATE_PATTERN` 解析 `not-a-date` 返回 `null`）。未改生产代码。manager-api 全量 `clean verify` 通过：Tests run: 965, Failures: 0, Errors: 0。
