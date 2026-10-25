# 04: DateUtils.getShortTime 纯单元测试

**What to build:** 在现有 `xiaozhi.common.utils.DateUtilsTest` 中追加 `getShortTime(Date)` 的纯 JUnit 5 单元测试。覆盖以下分支：

1. `date` 为 `null` 时返回 `null`
2. 传入 `new Date()`（当前时间），差值 ≤ 10 秒，返回 `"刚刚"`
3. 传入 30 秒前的 `Date`，返回 `"30秒前"` 或 `"29秒前"`（容忍 1 秒时钟漂移，断言匹配正则 `^(29|30)秒前$`）
4. 传入 5 分钟前的 `Date`，断言匹配正则 `^[45]分钟前$`
5. 传入 2 小时前的 `Date`，断言匹配正则 `^[12]小时前$`
6. 传入 3 天前的 `Date`，断言匹配正则 `^[23]天前$`
7. 传入 10 天前的 `Date`，断言结果等于 `DateUtils.format(thatDate, DateUtils.DATE_TIME_PATTERN)`（超过一周走 format 分支）

构造历史时间统一用 `Date.from(Instant.now().minus(seconds, ChronoUnit.SECONDS))` 或等价写法，不要硬编码固定日期。断言允许 ±1 秒 / ±1 分钟的时钟漂移以避免 CI 抖动。

只修改 `main/manager-api/src/test/java/xiaozhi/common/utils/DateUtilsTest.java` 和本 issue 的状态、评论。不修改生产代码，不依赖 Spring 上下文、数据库、Redis、网络或固定系统时区。沿用现有 JUnit 5 import 与命名风格。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 新增 null 入参返回 null 测试
- [x] 新增 10 秒内返回"刚刚"测试
- [x] 新增秒/分钟/小时/天四个量级测试
- [x] 新增超过一周走 format 分支测试
- [x] manager-api 全量 `clean verify` 通过

## Comments

在 `DateUtilsTest` 中追加了 `getShortTime` 的 7 个纯单元测试，覆盖 null 入参、"刚刚"（≤10 秒）、30 秒/5 分钟/2 小时/3 天四个量级（正则断言容忍 ±1 秒/±1 分钟时钟漂移），以及 10 天前走 `format` 分支。历史时间统一用 `Date.from(Instant.now().minus(seconds, ChronoUnit.SECONDS))` 构造，未硬编码固定日期，不依赖 Spring/数据库/Redis/网络/时区。全量 `clean verify` 通过：972 个测试，0 失败，覆盖率检查通过。
