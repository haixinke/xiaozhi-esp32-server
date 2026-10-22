# 门禁测试：egg-miniprogram 加一个空配置文件

**Status:** open

在 `main/egg-miniprogram/miniprogram/config/` 下新增 `gate-test.js`：

```js
// Sandcastle 门禁端到端测试占位模块
module.exports = { GATE_CHECK_MINIPROGRAM: 'ok' }
```

要求：
- 不要改任何其它文件
