# 02: DateUtils.format 单参数重载单元测试

**What to build:** 在 `xiaozhi.common.utils.DateUtilsTest`（已存在，包 `xiaozhi.common.utils`）中追加针对 `format(Date)` 单参数重载的测试方法。已知该重载使用 `DATE_PATTERN`（`yyyy-MM-dd`）。覆盖两个用例：
1. `new Date(0L)`（epoch）格式化后匹配正则 `\d{4}-\d{2}-\d{2}`（断格式，不断具体时区值，避免 CI 时区漂移）
2. `null` 输入返回 `null`

不改任何生产代码。不依赖 Spring 上下文、数据库、Redis —— 纯 JUnit 5 断言，沿用 `DateUtilsTest` 现有 import 与写法。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `DateUtilsTest` 新增两个测试方法：`format_withEpochDate_returnsDatePattern` / `format_withNullDate_returnsNull`

## Comments

2026-10-22: 已完成。在 `DateUtilsTest` 追加 `format_withEpochDate_returnsDatePattern`（断 `\d{4}-\d{2}-\d{2}` 正则，避免时区漂移）与 `format_withNullDate_returnsNull` 两个纯 JUnit 5 测试；未改动生产代码。验证：manager-api 全量 `clean verify` 通过（963 tests, 0 failures），提交 `ceda02c5`。
