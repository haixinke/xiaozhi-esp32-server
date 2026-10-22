# 门禁测试：manager-web 加一个工具函数

**Status:** resolved

在 `main/manager-web/src/utils/` 下新增 `gateTest.js`：

```js
// Sandcastle 门禁端到端测试占位模块
export function gateCheckWeb() {
  return 'ok'
}
```

要求：
- 在 `main/manager-web/tests/` 下加 `gateTest.test.mjs`（node --test 风格，断言返回值）
- 不要改任何其它文件

## Comments

新增了 `src/utils/gateTest.js`（导出 `gateCheckWeb()` 函数）和 `tests/gateTest.test.mjs`（node --test 风格断言返回值 "ok"）。npm run test:unit 136 tests pass，check:i18n 和 check:template 均通过。commit: 490ee492。
