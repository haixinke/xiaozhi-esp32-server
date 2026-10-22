# 门禁测试：manager-web 加一个工具函数

**Status:** open

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
