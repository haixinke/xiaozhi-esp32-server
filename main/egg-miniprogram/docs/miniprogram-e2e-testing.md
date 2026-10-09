# 小程序自动化测试

排查或验证**页面级交互问题**（WXML 事件绑定、手势、页面跳转、组件联动）时，不要只靠读代码推测——用下面两个官方工具搭可重复运行的反馈回路。选择标准：纯 JS 逻辑用单测；组件渲染/事件用 simulate；整页交互、只有真引擎才暴露的问题用 automator。

## miniprogram-automator（整页 E2E，驱动真实开发者工具）

适用：页面跳转、WXML 事件绑定、手势交互、原生组件（swiper/image/scroll-view）行为、`wx.*` API 真实行为。凡是「逻辑看着对但真机/模拟器就是不生效」的 bug（如 `catchtouchstart` 吞掉 `bindtap`），只有它能复现。

前置条件：微信开发者工具 → 设置 → 安全设置 → 开启「服务端口」。依赖装在临时目录即可（本项目无 npm 基建，不入库）：

```bash
mkdir -p /tmp/egg-e2e && cd /tmp/egg-e2e && npm init -y && npm install miniprogram-automator
```

核心用法：

```js
const automator = require('miniprogram-automator');
const miniProgram = await automator.launch({
  cliPath: '/Applications/wechatwebdevtools.app/Contents/MacOS/cli',
  projectPath: '<repo>/main/egg-miniprogram'
});
const page = await miniProgram.reLaunch('/pages/photo-gallery/photo-gallery');
await page.waitFor(3000);                        // 页面 API 无加载完成回调，用固定等待
const el = await page.$('.deck-card');           // CSS 选择器；$$ 取全部
await el.tap();                                  // 还有 touchstart/touchmove/touchend/longpress
const data = await page.data();                  // 直接读页面 data 断言状态
await page.callMethod('onCardTap', fakeEvent);   // 绕过事件系统直调页面方法（区分「事件没触发」与「逻辑错了」）
await miniProgram.evaluate(() => {               // 在小程序上下文执行任意 JS，
  const p = getCurrentPages().pop();             // 可 monkeypatch 页面方法记录调用
});
await miniProgram.close();
```

调试范式：先 `$()` + `tap()` 复现症状，再 `callMethod()` 直调处理函数——若直调正常而 tap 无效，问题在 WXML 事件绑定层而非 JS 逻辑。

## miniprogram-simulate（组件单测，纯 JS，无需开发者工具）

适用：自定义组件（`components/` 下 nav-bar、egg-avatar 等）的渲染与事件单测，可进 CI。配合 jest 使用：

```js
const simulate = require('miniprogram-simulate');
const id = simulate.load('/components/nav-bar/nav-bar');   // 组件路径
const comp = simulate.render(id, { title: 'AI写真' });     // 传 properties
comp.attach(document.createElement('parent-wrapper'));     // 挂载触发生命周期
expect(comp.querySelector('.title').dom.textContent).toBe('AI写真');
comp.querySelector('.back').dispatchEvent('touchstart');   // 触发事件
await simulate.sleep(0);
```
