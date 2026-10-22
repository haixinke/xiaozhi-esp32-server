# 门禁测试：egg-miniprogram 加一个空配置文件

**Status:** resolved

在 `main/egg-miniprogram/miniprogram/config/` 下新增 `gate-test.js`：

```js
// Sandcastle 门禁端到端测试占位模块
module.exports = { GATE_CHECK_MINIPROGRAM: 'ok' }
```

要求：
- 不要改任何其它文件

## Comments

新增了 `miniprogram/config/gate-test.js`，导出 `GATE_CHECK_MINIPROGRAM: 'ok'`。node verify-project.js 通过（25 个页面，工程结构正常）。commit: 583f605a。
