# 蛋宝宝领取时手机号授权 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新用户可以进入首页，只有在领取蛋宝宝时才必须授权微信手机号，成功后打开既有邀请码输入页。

**Architecture:** 手机号门槛从欢迎页和 App 路由守卫移至首页空态的领取动作。首页用内嵌弹层接收 `getPhoneNumber` 动态 code，复用 `wechatApi.bindPhone()` 和 `auth.markPhoneBound()`；授权成功后跳转现有 `add-device` 页。

**Tech Stack:** 微信原生小程序（JavaScript、WXML、WXSS）、Node.js 内置 `assert`、现有认证和微信 API 模块。

## Global Constraints

- 保持 `wx.login` 静默登录、token 续期、`/wechat/bindPhone` 与 `/pet/adopt` 契约不变。
- 不记录或展示手机号 code、token、openid、Authorization 或完整登录响应。
- 不新增页面；邀请码继续由 `pages/add-device/add-device` 输入和提交。
- 欢迎页按钮文案固定为“进入快乐仙岛”。
- 未绑定手机号用户可浏览首页；仅领取动作触发手机号授权。
- 已绑定手机号用户领取时直接进入邀请码输入页。
- 拒绝授权、缺少 code、会话无效和绑定失败时不得进入邀请码页。
- 保留与任务无关的 `.claude/skills/*` 删除，不修改也不暂存。

---

## 文件结构

| 文件 | 责任 |
| --- | --- |
| `main/egg-miniprogram/miniprogram/pages/welcome/welcome.js` | 静默登录后进入首页的欢迎页逻辑。 |
| `main/egg-miniprogram/miniprogram/pages/welcome/welcome.wxml` | 普通“进入快乐仙岛”按钮。 |
| `main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js` | 欢迎页不再手机号绑定的测试。 |
| `main/egg-miniprogram/miniprogram/app.js` | 不按 `hasPhone` 重启导航。 |
| `main/egg-miniprogram/miniprogram/app.test.js` | 未绑定会话仍可访问首页的测试。 |
| `main/egg-miniprogram/miniprogram/pages/home/home.js` | 领取前手机号授权、绑定和邀请码页跳转。 |
| `main/egg-miniprogram/miniprogram/pages/home/home.wxml` | 首页授权弹层。 |
| `main/egg-miniprogram/miniprogram/pages/home/home.wxss` | 授权弹层的局部样式。 |
| `main/egg-miniprogram/miniprogram/pages/home/home.test.js` | 领取授权的回归测试。 |
| `main/egg-miniprogram/CLAUDE.md` | 更新后的流程说明。 |

### Task 1: 欢迎页直接进入首页

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.wxml`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`

**Interfaces:**
- Consumes: `getApp().ensureLogin(): Promise<Session|null>`。
- Produces: `onEnterIsland(): void`，调用 `wx.switchTab({ url: '/pages/home/home' })`。

- [ ] **Step 1: 写失败测试**

将测试桩改为仅替换 `auth`，去掉 `pet-store`、`wechat-api`、`markPhoneBound`、`bindPhone` 依赖。覆盖本地有效未绑定会话、静默登录返回未绑定会话和已绑定会话均进入首页，以及点击新入口进入首页：

```js
cachedSession = { userId: 42, hasPhone: false };
const page = makePage();
await page.onLoad();
assert.strictEqual(switchedTo, '/pages/home/home');

resetScenario();
const entered = makePage();
entered.onEnterIsland();
assert.strictEqual(switchedTo, '/pages/home/home');
```

- [ ] **Step 2: 验证 RED**

Run: `node main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`

Expected: FAIL，因为当前未绑定会话仍留在欢迎页，且没有 `onEnterIsland`。

- [ ] **Step 3: 最小实现**

移除 `pet-store`、`wechat-api` 导入、`agreed`/`authorizing` 数据与 `onAuthorize`、协议相关方法。`onLoad` 在任意有效会话（无论 `hasPhone`）时进入首页；无会话或静默登录失败时显示内容。新增：

```js
onEnterIsland() {
  wx.switchTab({ url: '/pages/home/home' });
}
```

替换按钮和提示语：

```xml
<button class="auth-button" bindtap="onEnterIsland">进入快乐仙岛</button>
<text class="auth-note">开启一段只属于你的陪伴</text>
```

移除隐私勾选区和 `open-type="getPhoneNumber"`。

- [ ] **Step 4: 验证 GREEN**

Run: `node main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`

Expected: `welcome.test.js: ALL PASS`，退出码 0。

- [ ] **Step 5: 提交**

Run: `git add main/egg-miniprogram/miniprogram/pages/welcome/welcome.js main/egg-miniprogram/miniprogram/pages/welcome/welcome.wxml main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`

Run: `git commit -m "feat(egg-miniprogram): allow entering home before phone binding"`

### Task 2: 移除 App 级手机号守卫

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/app.js`
- Modify: `main/egg-miniprogram/miniprogram/app.test.js`

**Interfaces:**
- Consumes: `auth.getSession()`, `auth.isExpired()`, `auth.isExpiringSoon()`。
- Produces: 启动和前台恢复只维护登录态，不按 `hasPhone` 重启导航。


- [ ] **Step 1: 写失败测试**

删除所有 `enforcePhoneGate` 断言。通过 `onLaunch()` 与 `onShow()` 验证未绑定会话不会调用 `wx.reLaunch`；保留过期会话清除断言：

```js
storedSession = { ...session, hasPhone: false };
expired = false;
relaunchedTo = null;
appConfig.onShow.call(appConfig);
assert.strictEqual(relaunchedTo, null,
  'unbound session may continue to home before claiming a pet');
```

- [ ] **Step 2: 验证 RED**

Run: `node main/egg-miniprogram/miniprogram/app.test.js`

Expected: FAIL，因为当前 `enforcePhoneGate` 会重启未绑定会话。

- [ ] **Step 3: 最小实现**

移除 `onLaunch` 成功/失败分支和 `onShow` 过期分支对 `this.enforcePhoneGate(...)` 的调用，删除 `enforcePhoneGate` 方法。保持登录失败时清空全局态：

```js
this.globalData.authReady = this.ensureLogin()
  .then((session) => session)
  .catch(() => {
    this.applySession(null);
    return null;
  });
```

- [ ] **Step 4: 验证 GREEN**

Run: `node main/egg-miniprogram/miniprogram/app.test.js`

Expected: `app.test.js: ALL PASS`，退出码 0。

- [ ] **Step 5: 提交**

Run: `git add main/egg-miniprogram/miniprogram/app.js main/egg-miniprogram/miniprogram/app.test.js`

Run: `git commit -m "feat(egg-miniprogram): defer phone gate until pet claim"`

### Task 3: 在领取时授权手机号

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.wxml`
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.wxss`
- Create: `main/egg-miniprogram/miniprogram/pages/home/home.test.js`

**Interfaces:**
- Consumes: `auth.getSession()`, `auth.isExpired()`, `auth.markPhoneBound()`, `wechatApi.bindPhone(phoneCode)`, `getApp().ensureLogin()`。
- Produces: `onAddDevice()`, `onAuthorizePhone(event): Promise<void>`, `onClosePhoneAuthorization()`。

- [ ] **Step 1: 写失败测试**

创建 `home.test.js`，使用 `Module._load` 注入 `auth`、`petStore`、`wechatApi`、`request` 桩并捕获 `Page` 配置。至少覆盖：

```js
cachedSession = { userId: 42, hasPhone: true };
const boundPage = makePage();
boundPage.onAddDevice();
assert.strictEqual(navigatedTo, '/pages/add-device/add-device');

resetScenario();
cachedSession = { userId: 42, hasPhone: false };
const unboundPage = makePage();
unboundPage.onAddDevice();
assert.strictEqual(unboundPage.data.showPhoneAuthorization, true);
assert.strictEqual(navigatedTo, null);

resetScenario();
ensureSession = { userId: 42, hasPhone: false };
markPhoneResult = { userId: 42, hasPhone: true };
const authorizationPage = makePage();
authorizationPage.setData({ showPhoneAuthorization: true });
await authorizationPage.onAuthorizePhone({ detail: { code: 'test-phone-code' } });
assert.strictEqual(bindPhoneCalls, 1);
assert.strictEqual(navigatedTo, '/pages/add-device/add-device');
```

加入拒绝/缺少 code、无会话、绑定失败、关闭弹层和并发点击断言；全部情况不得导航，且 `authorizingPhone` 最终为 `false`。

- [ ] **Step 2: 验证 RED**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`

Expected: FAIL，因为当前领取无条件导航，且没有弹层状态与 `onAuthorizePhone`。

- [ ] **Step 3: 最小实现**

在 `home.js` 引入 `wechatApi`，增加 `showPhoneAuthorization`、`authorizingPhone` 数据。以如下接口取代无条件跳转：

```js
onAddDevice() {
  const session = auth.getSession();
  if (session && !auth.isExpired() && session.hasPhone === true) {
    wx.navigateTo({ url: '/pages/add-device/add-device' });
    return;
  }
  this.setData({ showPhoneAuthorization: true });
},

onClosePhoneAuthorization() {
  if (!this.data.authorizingPhone) this.setData({ showPhoneAuthorization: false });
},

async onAuthorizePhone(event) {
  const phoneCode = event && event.detail && event.detail.code;
  if (!phoneCode) {
    wx.showToast({ title: '需要授权手机号后才能领取蛋宝宝', icon: 'none' });
    return;
  }
  if (this.data.authorizingPhone) return;
  this.setData({ authorizingPhone: true });
  try {
    const session = await getApp().ensureLogin();
    if (!session || !session.userId) throw new Error('invalid login session');
    await wechatApi.bindPhone(phoneCode);
    const boundSession = auth.markPhoneBound();
    if (!boundSession) throw new Error('invalid login session');
    getApp().applySession(boundSession);
    this.setData({ showPhoneAuthorization: false });
    wx.navigateTo({ url: '/pages/add-device/add-device' });
  } catch (error) {
    wx.showToast({ title: error.userMessage || '暂时无法连接服务，请稍后重试', icon: 'none' });
  } finally {
    this.setData({ authorizingPhone: false });
  }
}
```

在破壳遮罩前新增弹层：

```xml
<view wx:if="{{showPhoneAuthorization}}" class="phone-auth-mask" catchtap="onClosePhoneAuthorization">
  <view class="phone-auth-dialog" catchtap="noop">
    <text class="phone-auth-title">领取前需要授权手机号</text>
    <text class="phone-auth-copy">用于保障你的蛋宝宝账号安全。</text>
    <button class="primary-button" open-type="getPhoneNumber" loading="{{authorizingPhone}}" disabled="{{authorizingPhone}}" bindgetphonenumber="onAuthorizePhone">授权手机号并继续</button>
    <button class="phone-auth-cancel" disabled="{{authorizingPhone}}" bindtap="onClosePhoneAuthorization">暂不领取</button>
  </view>
</view>
```

只在 `home.wxss` 添加弹层的定位、半透明遮罩、白色圆角对话框和取消按钮样式，不改动其它首页样式。

- [ ] **Step 4: 验证 GREEN**

Run: `node main/egg-miniprogram/miniprogram/pages/home/home.test.js`

Expected: `home.test.js: ALL PASS`，退出码 0。

- [ ] **Step 5: 提交**

Run: `git add main/egg-miniprogram/miniprogram/pages/home/home.js main/egg-miniprogram/miniprogram/pages/home/home.wxml main/egg-miniprogram/miniprogram/pages/home/home.wxss main/egg-miniprogram/miniprogram/pages/home/home.test.js`

Run: `git commit -m "feat(egg-miniprogram): require phone authorization to claim pet"`

### Task 4: 更新说明并完整验证

**Files:**
- Modify: `main/egg-miniprogram/CLAUDE.md`

**Interfaces:**
- Consumes: 完成后的欢迎页、App 和首页领取实现。
- Produces: 准确描述领取时授权的项目文档。

- [ ] **Step 1: 更新文档**

将“手机号绑定”从“进入首页的强制门槛”更新为“领取蛋宝宝前的强制门槛”。将“未绑定手机号必须留在欢迎页”更新为“未绑定手机号可进入首页，但点击添加蛋宝宝后必须完成手机号授权”。其余接口、token 和敏感信息约束保持不变。

- [ ] **Step 2: 运行聚焦测试**

Run: `node main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js && node main/egg-miniprogram/miniprogram/app.test.js && node main/egg-miniprogram/miniprogram/pages/home/home.test.js && node main/egg-miniprogram/miniprogram/utils/auth.test.js && node main/egg-miniprogram/miniprogram/utils/wechat-api.test.js && node main/egg-miniprogram/miniprogram/utils/request.test.js`

Expected: 所有脚本打印 `ALL PASS`，退出码 0。

- [ ] **Step 3: 运行工程校验**

Run: `node main/egg-miniprogram/scripts/verify-project.js`

Expected: 退出码 0。

Run: `find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check`

Expected: 退出码 0。

Run: `find main/egg-miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty`

Expected: 退出码 0。

- [ ] **Step 4: 提交**

Run: `git add main/egg-miniprogram/CLAUDE.md`

Run: `git commit -m "docs(egg-miniprogram): document claim-time phone authorization"`
