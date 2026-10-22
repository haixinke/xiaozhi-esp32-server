# 01: DateUtils.millsToSecond 单元测试

**What to build:** 为 `xiaozhi.common.utils.DateUtils` 的 `millsToSecond(long)` 方法新增纯单元测试类 `DateUtilsTest`（包 `xiaozhi.common.utils`）。覆盖三个用例：`0` → `"0.000"`、`1500` → `"1.500"`、`123456` → `"123.456"`。不改任何生产代码。不依赖 Spring 上下文、数据库、Redis —— 纯 JUnit 5 断言，参照同目录 `AESUtilsTest` / `JsonUtilsTest` 的写法。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 新增 `main/manager-api/src/test/java/xiaozhi/common/utils/DateUtilsTest.java`，断言 millsToSecond 的 0 / 1500 / 123456 三个输入输出

## Comments

新增 `DateUtilsTest.java`，三个 JUnit 5 测试方法分别验证 `millsToSecond(0)`、`millsToSecond(1500)`、`millsToSecond(123456)` 的输出。无生产代码改动。Maven 全量验证通过：961 tests, 0 failures, BUILD SUCCESS。
