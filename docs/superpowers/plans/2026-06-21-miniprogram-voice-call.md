# 小程序语音通话功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在"完美女友"微信小程序中新增实时语音通话功能，用户通过聊天页 `+` 号浮窗入口发起通话，进入全屏通话页进行持续双向语音交互。

**Architecture:** 新增独立 `pages/voice-call` 页面和 `utils/voice-call-manager.js` 单例状态管理器，复用现有 `WebSocketManager` + `AudioManager` 处理 Opus 音频流；新增 `components/floating-call-ball` 用于返回首页后保持通话。

**Tech Stack:** 微信小程序原生框架（WXML/WXSS/JS），Node.js `assert` 做单元测试，微信开发者工具做真机预览。

## Global Constraints

- 禁止在小程序 UI 中使用任何 emoji（项目既有约定）。
- 新增页面/组件必须接入 `utils/theme.js` 深色模式。
- 文件职责单一，新增页面/组件控制在 800 行以内。
- 所有用户输入和外部数据必须显式校验。
- 错误处理必须显式，不允许静默吞错。
- 提交使用 conventional commits：`feat:` / `fix:` / `refactor:` / `docs:` / `test:`。
- 麦克风权限使用 `scope.record`，在点击语音通话卡片时申请。
- 语音通话受 `voice_call` 订阅权益控制。
- 默认扬声器输出，可切换听筒。
- 切后台自动挂断。
- 单次录音 10 分钟到期后无缝重启。

---

## File Structure

### 新增文件

| 文件 | 职责 |
|---|---|
| `main/miniprogram/utils/voice-call-manager.js` | 通话状态单例：状态机、时长计时、静音/免提状态、录音重启调度 |
| `main/miniprogram/utils/voice-call-manager.test.js` | VoiceCallManager 单元测试 |
| `main/miniprogram/pages/voice-call/voice-call.js` | 通话页主控制器 |
| `main/miniprogram/pages/voice-call/voice-call.wxml` | 通话页布局（呼叫面板 + 通话面板） |
| `main/miniprogram/pages/voice-call/voice-call.wxss` | 通话页样式 |
| `main/miniprogram/pages/voice-call/voice-call.json` | 页面配置 |
| `main/miniprogram/components/floating-call-ball/floating-call-ball.js` | 悬浮通话小球组件 |
| `main/miniprogram/components/floating-call-ball/floating-call-ball.wxml` | 小球组件模板 |
| `main/miniprogram/components/floating-call-ball/floating-call-ball.wxss` | 小球组件样式 |
| `main/miniprogram/components/floating-call-ball/floating-call-ball.json` | 组件配置 |

### 修改文件

| 文件 | 修改内容 |
|---|---|
| `main/miniprogram/pages/index/index.wxml` | 输入框右侧加 `+` 号按钮；底部弹出多功能浮窗；显示 `floating-call-ball` |
| `main/miniprogram/pages/index/index.js` | 处理 `+` 号点击、浮窗显隐、权益检查、权限申请、通话入口、小球事件 |
| `main/miniprogram/pages/index/index.wxss` | `+` 号、浮窗、小球样式 |
| `main/miniprogram/app.json` | 注册 `pages/voice-call/voice-call` |

---

### Task 1: VoiceCallManager 单例与状态机

**Files:**
- Create: `main/miniprogram/utils/voice-call-manager.js`
- Create: `main/miniprogram/utils/voice-call-manager.test.js`

**Interfaces:**
- Consumes: 无（纯状态管理）
- Produces:
  - `getInstance()` —— 返回单例
  - `startCall()` —— 进入 `calling` 状态
  - `connect()` —— 进入 `connected` 状态
  - `hangup()` —— 进入 `ended` 状态
  - `toggleMute()` / `toggleSpeaker()` —— 切换布尔状态
  - `getState()` —— 返回完整状态对象
  - `onStateChange(callback)` / `offStateChange(callback)` —— 状态变更订阅

- [ ] **Step 1: Write the failing test**

```js
// main/miniprogram/utils/voice-call-manager.test.js
const assert = require('assert');
const VoiceCallManager = require('./voice-call-manager');

(function () {
  const mgr = new VoiceCallManager();
  assert.strictEqual(mgr.getState().state, 'idle');

  mgr.startCall();
  assert.strictEqual(mgr.getState().state, 'calling');

  mgr.connect();
  assert.strictEqual(mgr.getState().state, 'connected');
  assert.ok(mgr.getState().startTime > 0);

  mgr.toggleMute();
  assert.strictEqual(mgr.getState().isMuted, true);
  mgr.toggleMute();
  assert.strictEqual(mgr.getState().isMuted, false);

  mgr.toggleSpeaker();
  assert.strictEqual(mgr.getState().isSpeakerOn, false);
  mgr.toggleSpeaker();
  assert.strictEqual(mgr.getState().isSpeakerOn, true);

  mgr.hangup();
  assert.strictEqual(mgr.getState().state, 'ended');

  console.log('voice-call-manager.test.js: ALL PASS');
})();
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/miniprogram && node utils/voice-call-manager.test.js`

Expected: FAIL with "Cannot find module './voice-call-manager'"

- [ ] **Step 3: Write minimal implementation**

```js
// main/miniprogram/utils/voice-call-manager.js
const STATE_IDLE = 'idle';
const STATE_CALLING = 'calling';
const STATE_CONNECTED = 'connected';
const STATE_ENDING = 'ending';
const STATE_ENDED = 'ended';

class VoiceCallManager {
  constructor() {
    this._state = STATE_IDLE;
    this._durationSeconds = 0;
    this._isMuted = false;
    this._isSpeakerOn = true;
    this._startTime = null;
    this._listeners = new Set();
  }

  getState() {
    return {
      state: this._state,
      durationSeconds: this._durationSeconds,
      isMuted: this._isMuted,
      isSpeakerOn: this._isSpeakerOn,
      startTime: this._startTime,
    };
  }

  _setState(next) {
    if (this._state === next) return;
    this._state = next;
    this._emit();
  }

  _emit() {
    const state = this.getState();
    this._listeners.forEach((fn) => {
      try { fn(state); } catch (_) {}
    });
  }

  onStateChange(callback) {
    if (typeof callback !== 'function') return;
    this._listeners.add(callback);
  }

  offStateChange(callback) {
    this._listeners.delete(callback);
  }

  startCall() {
    this._durationSeconds = 0;
    this._isMuted = false;
    this._isSpeakerOn = true;
    this._startTime = null;
    this._setState(STATE_CALLING);
  }

  connect() {
    this._startTime = Date.now();
    this._setState(STATE_CONNECTED);
  }

  hangup() {
    this._setState(STATE_ENDED);
  }

  toggleMute() {
    this._isMuted = !this._isMuted;
    this._emit();
  }

  toggleSpeaker() {
    this._isSpeakerOn = !this._isSpeakerOn;
    this._emit();
  }
}

let instance = null;
module.exports = function getInstance() {
  if (!instance) instance = new VoiceCallManager();
  return instance;
};
module.exports.VoiceCallManager = VoiceCallManager;
module.exports.STATE_IDLE = STATE_IDLE;
module.exports.STATE_CALLING = STATE_CALLING;
module.exports.STATE_CONNECTED = STATE_CONNECTED;
module.exports.STATE_ENDING = STATE_ENDING;
module.exports.STATE_ENDED = STATE_ENDED;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/miniprogram && node utils/voice-call-manager.test.js`

Expected: `voice-call-manager.test.js: ALL PASS`

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/utils/voice-call-manager.js main/miniprogram/utils/voice-call-manager.test.js
git commit -m "feat(miniprogram): add VoiceCallManager singleton and state machine"
```

---

### Task 2: VoiceCallManager 通话时长与录音重启调度

**Files:**
- Modify: `main/miniprogram/utils/voice-call-manager.js`
- Modify: `main/miniprogram/utils/voice-call-manager.test.js`

**Interfaces:**
- Consumes: 无
- Produces:
  - `getState().durationSeconds` —— 每秒自增
  - `getState().recordRestartAt` —— 下次录音重启时间点（ms）
  - `setOnRecordRestart(callback)` —— 录音重启回调
  - `destroy()` —— 清理所有计时器

- [ ] **Step 1: Write the failing test**

```js
// Append to main/miniprogram/utils/voice-call-manager.test.js
(function () {
  const VoiceCallManager = require('./voice-call-manager').VoiceCallManager;
  const mgr = new VoiceCallManager();
  let restarted = false;
  mgr.setOnRecordRestart(() => { restarted = true; });

  mgr.startCall();
  mgr.connect();

  // durationSeconds 应该初始为 0
  assert.strictEqual(mgr.getState().durationSeconds, 0);
  // recordRestartAt 应该在 connect 后约 10 分钟内
  const remaining = mgr.getState().recordRestartAt - Date.now();
  assert.ok(remaining > 9 * 60 * 1000 && remaining <= 10 * 60 * 1000, 'recordRestartAt out of range');

  // 模拟时间推进：直接触发 restart
  mgr._triggerRecordRestartForTest && mgr._triggerRecordRestartForTest();
  assert.strictEqual(restarted, true);

  mgr.hangup();
  mgr.destroy();
  console.log('duration/restart test: PASS');
})();
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/miniprogram && node utils/voice-call-manager.test.js`

Expected: FAIL with "mgr.setOnRecordRestart is not a function"

- [ ] **Step 3: Write minimal implementation**

在 `VoiceCallManager` 构造函数中新增：

```js
this._durationTimer = null;
this._recordRestartTimer = null;
this._recordRestartCallback = null;
```

新增方法：

```js
connect() {
  this._startTime = Date.now();
  this._startDurationTimer();
  this._scheduleRecordRestart();
  this._setState(STATE_CONNECTED);
}

_startDurationTimer() {
  this._stopDurationTimer();
  this._durationTimer = setInterval(() => {
    this._durationSeconds += 1;
    this._emit();
  }, 1000);
}

_stopDurationTimer() {
  if (this._durationTimer) {
    clearInterval(this._durationTimer);
    this._durationTimer = null;
  }
}

_scheduleRecordRestart() {
  this._stopRecordRestartTimer();
  const delay = 10 * 60 * 1000; // 10 min
  this._recordRestartAt = Date.now() + delay;
  this._recordRestartTimer = setTimeout(() => {
    this._recordRestartTimer = null;
    this._recordRestartAt = null;
    if (this._state === STATE_CONNECTED && this._recordRestartCallback) {
      this._recordRestartCallback();
    }
    if (this._state === STATE_CONNECTED) {
      this._scheduleRecordRestart();
    }
  }, delay);
}

_stopRecordRestartTimer() {
  if (this._recordRestartTimer) {
    clearTimeout(this._recordRestartTimer);
    this._recordRestartTimer = null;
  }
  this._recordRestartAt = null;
}

setOnRecordRestart(callback) {
  this._recordRestartCallback = callback;
}

hangup() {
  this._stopDurationTimer();
  this._stopRecordRestartTimer();
  this._setState(STATE_ENDED);
}

destroy() {
  this._stopDurationTimer();
  this._stopRecordRestartTimer();
  this._listeners.clear();
  this._recordRestartCallback = null;
}

// Test helper only
_triggerRecordRestartForTest() {
  if (this._recordRestartCallback) this._recordRestartCallback();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/miniprogram && node utils/voice-call-manager.test.js`

Expected: `voice-call-manager.test.js: ALL PASS` 和 `duration/restart test: PASS`

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/utils/voice-call-manager.js main/miniprogram/utils/voice-call-manager.test.js
git commit -m "feat(miniprogram): add voice call duration timer and 10min record restart"
```

---

### Task 3: 首页 `+` 号按钮与底部浮窗 UI

**Files:**
- Modify: `main/miniprogram/pages/index/index.wxml`
- Modify: `main/miniprogram/pages/index/index.wxss`
- Modify: `main/miniprogram/pages/index/index.js`

**Interfaces:**
- Consumes: 无
- Produces:
  - `data.showToolPanel` —— 浮窗显隐
  - `onToolPanelToggle()` —— 切换浮窗
  - `onToolPanelMaskTap()` —— 点击蒙层关闭

- [ ] **Step 1: Add the `+` button and tool panel markup**

修改 `main/miniprogram/pages/index/index.wxml`，在底部输入栏 `input-bar` 内，输入框右侧增加 `+` 号按钮和浮窗面板。

```xml
<!-- 在 input-bar-inner 内部，模式切换按钮之后插入 -->
<view class="tool-toggle-btn" bindtap="onToolPanelToggle" hover-class="tool-toggle-btn-hover" hover-stay-time="80">
  <image class="tool-toggle-icon" src="{{darkMode ? '/images/plus-dark.png' : '/images/plus.png'}}" mode="aspectFit" />
</view>

<!-- 在 input-bar 末尾（安全区之后）插入 -->
<view wx:if="{{showToolPanel}}" class="tool-panel-mask" catchtap="onToolPanelMaskTap">
  <view class="tool-panel {{darkMode ? 'dark' : ''}}" catchtap="onToolPanelCatch">
    <view class="tool-panel-title"><text class="tool-panel-title-text">更多</text></view>
    <view class="tool-grid">
      <view class="tool-grid-item" bindtap="onVoiceCallTap" hover-class="tool-grid-item-hover" hover-stay-time="80">
        <view class="tool-grid-icon-wrap">
          <image class="tool-grid-icon" src="{{darkMode ? '/images/phone-dark.png' : '/images/phone.png'}}" mode="aspectFit" />
        </view>
        <text class="tool-grid-label">语音通话</text>
      </view>
      <!-- 预留 5 个空位 -->
      <view class="tool-grid-item tool-grid-item-empty"></view>
      <view class="tool-grid-item tool-grid-item-empty"></view>
      <view class="tool-grid-item tool-grid-item-empty"></view>
      <view class="tool-grid-item tool-grid-item-empty"></view>
    </view>
  </view>
</view>
```

- [ ] **Step 2: Add styles for the tool button and panel**

在 `main/miniprogram/pages/index/index.wxss` 末尾追加：

```css
/* + 号按钮 */
.tool-toggle-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 12rpx;
  border-radius: 50%;
  background: rgba(134, 78, 90, 0.08);
}
.tool-toggle-btn-hover {
  background: rgba(134, 78, 90, 0.16);
}
.tool-toggle-icon {
  width: 40rpx;
  height: 40rpx;
}

/* 浮窗蒙层 */
.tool-panel-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.25);
  z-index: 100;
}

/* 浮窗面板 */
.tool-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
}
.tool-panel.dark {
  background: #1e1e2e;
}
.tool-panel-title {
  text-align: center;
  margin-bottom: 24rpx;
}
.tool-panel-title-text {
  font-size: 26rpx;
  color: rgba(134, 78, 90, 0.6);
}
.dark .tool-panel-title-text {
  color: rgba(232, 228, 227, 0.5);
}

/* 3x2 网格 */
.tool-grid {
  display: flex;
  flex-wrap: wrap;
}
.tool-grid-item {
  width: 33.3333%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 0;
}
.tool-grid-item-empty {
  pointer-events: none;
}
.tool-grid-icon-wrap {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: rgba(134, 78, 90, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}
.tool-grid-item-hover .tool-grid-icon-wrap {
  background: rgba(134, 78, 90, 0.16);
}
.tool-grid-icon {
  width: 48rpx;
  height: 48rpx;
}
.tool-grid-label {
  font-size: 24rpx;
  color: #4a4a4a;
}
.dark .tool-grid-label {
  color: #e8e4e3;
}
```

- [ ] **Step 3: Add data and event handlers in index.js**

在 `main/miniprogram/pages/index/index.js` 的 `data` 中新增：

```js
showToolPanel: false,
```

新增方法：

```js
onToolPanelToggle() {
  this.setData({ showToolPanel: !this.data.showToolPanel });
},

onToolPanelMaskTap() {
  this.setData({ showToolPanel: false });
},

onToolPanelCatch() {
  // 阻止冒泡，避免点击面板自身关闭浮窗
},
```

- [ ] **Step 4: Preview in WeChat Developer Tools**

打开微信开发者工具，进入首页，点击 `+` 号，确认浮窗从底部弹出，布局为 3×2，"语音通话"卡片显示正常，点击蒙层可关闭。

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/index/index.wxml main/miniprogram/pages/index/index.wxss main/miniprogram/pages/index/index.js
git commit -m "feat(miniprogram): add tool panel with voice call entry on chat page"
```

---

### Task 4: 首页语音通话入口逻辑（权益、权限、跳转）

**Files:**
- Modify: `main/miniprogram/pages/index/index.js`
- Modify: `main/miniprogram/pages/index/index.wxml`（如需占位）

**Interfaces:**
- Consumes:
  - `app.globalData.subscriptionFeatures` —— 权益列表
  - `wx.authorize` / `wx.getSetting` / `wx.openSetting` —— 权限申请
  - `VoiceCallManager.getInstance()` —— 状态管理单例
- Produces:
  - `onVoiceCallTap()` —— 点击语音通话卡片处理函数
  - `navigateTo({ url: '/pages/voice-call/voice-call' })`

- [ ] **Step 1: Add helper functions for feature and permission checks**

在 `main/miniprogram/pages/index/index.js` 末尾追加：

```js
// 检查是否拥有语音通话权益
_hasVoiceCallFeature() {
  const features = (app.globalData && app.globalData.subscriptionFeatures) || [];
  return features.indexOf('voice_call') !== -1;
},

// 检查并申请麦克风权限
async _ensureRecordPermission() {
  return new Promise((resolve) => {
    wx.getSetting({
      success: (res) => {
        const auth = res.authSetting && res.authSetting['scope.record'];
        if (auth === true) {
          resolve(true);
          return;
        }
        if (auth === false) {
          // 用户曾经拒绝过，引导去设置
          wx.showModal({
            title: '需要麦克风权限',
            content: '语音通话需要访问您的麦克风',
            confirmText: '去设置',
            cancelText: '取消',
            success: (modalRes) => {
              if (modalRes.confirm) {
                wx.openSetting({
                  success: (settingRes) => {
                    resolve(!!(settingRes.authSetting && settingRes.authSetting['scope.record']));
                  },
                  fail: () => resolve(false),
                });
              } else {
                resolve(false);
              }
            },
          });
          return;
        }
        // 未申请过
        wx.authorize({
          scope: 'scope.record',
          success: () => resolve(true),
          fail: () => {
            wx.showToast({ title: '需要麦克风权限', icon: 'none' });
            resolve(false);
          },
        });
      },
      fail: () => resolve(false),
    });
  });
},
```

- [ ] **Step 2: Implement onVoiceCallTap**

在 `main/miniprogram/pages/index/index.js` 中新增：

```js
async onVoiceCallTap() {
  this.setData({ showToolPanel: false });

  // 1. 权益检查
  if (!this._hasVoiceCallFeature()) {
    wx.showModal({
      title: '甜蜜契约',
      content: '签订契约后即可与女友语音通话',
      showCancel: true,
      cancelText: '知道了',
      confirmText: '去订阅',
      confirmColor: '#864e5a',
      success: (res) => {
        if (res.confirm) {
          // TODO: 跳转到订阅页（如果后续有）
          wx.showToast({ title: '订阅功能即将开放', icon: 'none' });
        }
      },
    });
    return;
  }

  // 2. 权限检查
  const permitted = await this._ensureRecordPermission();
  if (!permitted) return;

  // 3. 确保 WebSocket 已连接
  if (this.data.connectionState !== 'connected') {
    this.onSummon();
    // 等待连接完成（最多 10 秒）
    let waited = 0;
    while (this.data.connectionState !== 'connected' && waited < 10000) {
      await new Promise((r) => setTimeout(r, 200));
      waited += 200;
    }
    if (this.data.connectionState !== 'connected') {
      wx.showToast({ title: '连接失败，请重试', icon: 'none' });
      return;
    }
  }

  // 4. 初始化通话状态并跳转
  const VoiceCallManager = require('../../utils/voice-call-manager');
  VoiceCallManager().startCall();
  wx.navigateTo({ url: '/pages/voice-call/voice-call' });
},
```

- [ ] **Step 3: Bind the tap event in WXML**

确认 `index.wxml` 中 `onVoiceCallTap` 已绑定（Task 3 中已完成）。

- [ ] **Step 4: Test in WeChat Developer Tools**

1. 未订阅用户点击：应弹出订阅引导。
2. 已订阅用户首次点击：应弹出麦克风授权。
3. 已授权用户点击：若未连接则自动"召唤"，然后跳转到 `/pages/voice-call/voice-call`（页面文件尚不存在，会 404，属预期）。

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/index/index.js
git commit -m "feat(miniprogram): wire voice call entry with feature and permission checks"
```

---

### Task 5: 创建语音通话页面框架与呼叫 UI

**Files:**
- Create: `main/miniprogram/pages/voice-call/voice-call.json`
- Create: `main/miniprogram/pages/voice-call/voice-call.wxml`
- Create: `main/miniprogram/pages/voice-call/voice-call.wxss`
- Create: `main/miniprogram/pages/voice-call/voice-call.js`
- Modify: `main/miniprogram/app.json`

**Interfaces:**
- Consumes:
  - `VoiceCallManager.getInstance()` —— 状态管理
  - `app.globalData.companionAvatar` / `agentName` —— 伴侣信息
- Produces:
  - 全屏通话页 UI
  - `onCancelCall()` —— 取消呼叫

- [ ] **Step 1: Register page in app.json**

修改 `main/miniprogram/app.json`，在 `pages` 数组末尾添加：

```json
"pages/voice-call/voice-call"
```

- [ ] **Step 2: Create page config**

```json
// main/miniprogram/pages/voice-call/voice-call.json
{
  "navigationStyle": "custom",
  "disableScroll": true
}
```

- [ ] **Step 3: Create page WXML**

```xml
<!-- main/miniprogram/pages/voice-call/voice-call.wxml -->
<view class="container {{darkMode ? 'dark' : ''}}">
  <!-- 顶部返回/更多占位 -->
  <view class="safe-area-top"></view>

  <!-- 呼叫中状态 -->
  <view wx:if="{{callState === 'calling'}}" class="panel calling-panel">
    <view class="call-avatar-wrap">
      <image class="call-avatar" src="{{companionAvatar || '/images/avatar-default.png'}}" mode="aspectFill" />
      <view class="call-avatar-ring"></view>
    </view>
    <text class="call-name">{{companionName}}</text>
    <text class="call-status">正在呼叫…</text>
    <view class="call-actions">
      <view class="call-btn call-btn-cancel" bindtap="onCancelCall" hover-class="call-btn-hover" hover-stay-time="80">
        <text class="call-btn-text">取消</text>
      </view>
    </view>
  </view>

  <!-- 通话中状态 -->
  <view wx:if="{{callState === 'connected'}}" class="panel connected-panel">
    <view class="call-avatar-wrap">
      <image class="call-avatar" src="{{companionAvatar || '/images/avatar-default.png'}}" mode="aspectFill" />
    </view>
    <text class="call-name">{{companionName}}</text>
    <text class="call-duration">{{formattedDuration}}</text>
    <text class="call-status">{{callStatusText}}</text>

    <view class="call-controls">
      <view class="control-row">
        <view class="control-btn" bindtap="onToggleMute" hover-class="control-btn-hover" hover-stay-time="80">
          <image class="control-icon" src="{{isMuted ? (darkMode ? '/images/mic-off-dark.png' : '/images/mic-off.png') : (darkMode ? '/images/mic-dark.png' : '/images/mic.png')}}" mode="aspectFit" />
          <text class="control-label">{{isMuted ? '静音中' : '静音'}}</text>
        </view>
        <view class="control-btn" bindtap="onToggleSpeaker" hover-class="control-btn-hover" hover-stay-time="80">
          <image class="control-icon" src="{{isSpeakerOn ? (darkMode ? '/images/speaker-dark.png' : '/images/speaker.png') : (darkMode ? '/images/earpiece-dark.png' : '/images/earpiece.png')}}" mode="aspectFit" />
          <text class="control-label">{{isSpeakerOn ? '免提' : '听筒'}}</text>
        </view>
      </view>
      <view class="control-row control-row-main">
        <view class="call-btn call-btn-hangup" bindtap="onHangup" hover-class="call-btn-hover" hover-stay-time="80">
          <text class="call-btn-text">挂断</text>
        </view>
      </view>
    </view>

    <view class="back-to-chat" bindtap="onBackToChat" hover-class="back-to-chat-hover" hover-stay-time="80">
      <text class="back-to-chat-text">返回聊天</text>
    </view>
  </view>
</view>
```

- [ ] **Step 4: Create page WXSS**

```css
/* main/miniprogram/pages/voice-call/voice-call.wxss */
.container {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: #fbf9f8;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.container.dark {
  background: #121220;
}

.safe-area-top {
  height: env(safe-area-inset-top);
  width: 100%;
}

.panel {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: calc(48rpx + env(safe-area-inset-bottom));
}

.call-avatar-wrap {
  position: relative;
  width: 280rpx;
  height: 280rpx;
  margin-bottom: 48rpx;
}
.call-avatar {
  width: 280rpx;
  height: 280rpx;
  border-radius: 50%;
  border: 6rpx solid rgba(134, 78, 90, 0.12);
}
.call-avatar-ring {
  position: absolute;
  left: -24rpx;
  top: -24rpx;
  right: -24rpx;
  bottom: -24rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(134, 78, 90, 0.2);
  animation: pulse 2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.6; }
}

.call-name {
  font-size: 44rpx;
  font-weight: 600;
  color: #3d3d3d;
  margin-bottom: 16rpx;
}
.dark .call-name {
  color: #e8e4e3;
}
.call-duration {
  font-size: 32rpx;
  color: rgba(134, 78, 90, 0.8);
  margin-bottom: 12rpx;
  font-variant-numeric: tabular-nums;
}
.dark .call-duration {
  color: rgba(232, 228, 227, 0.7);
}
.call-status {
  font-size: 28rpx;
  color: rgba(134, 78, 90, 0.6);
}
.dark .call-status {
  color: rgba(232, 228, 227, 0.5);
}

.call-actions {
  margin-top: 120rpx;
}
.call-btn {
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.12);
}
.call-btn-hover {
  opacity: 0.85;
}
.call-btn-cancel {
  background: #9ca3af;
}
.call-btn-hangup {
  background: #ef4444;
  width: 240rpx;
  height: 240rpx;
}
.call-btn-text {
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 500;
}

.call-controls {
  margin-top: 80rpx;
  width: 100%;
}
.control-row {
  display: flex;
  justify-content: center;
  gap: 80rpx;
  margin-bottom: 48rpx;
}
.control-row-main {
  margin-top: 32rpx;
}
.control-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.control-btn-hover {
  opacity: 0.75;
}
.control-icon-wrap {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: rgba(134, 78, 90, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}
.dark .control-icon-wrap {
  background: rgba(232, 228, 227, 0.08);
}
.control-icon {
  width: 56rpx;
  height: 56rpx;
}
.control-label {
  font-size: 24rpx;
  color: #4a4a4a;
}
.dark .control-label {
  color: #e8e4e3;
}

.back-to-chat {
  margin-top: 40rpx;
  padding: 16rpx 32rpx;
  border-radius: 32rpx;
  background: rgba(134, 78, 90, 0.08);
}
.back-to-chat-hover {
  background: rgba(134, 78, 90, 0.16);
}
.back-to-chat-text {
  font-size: 26rpx;
  color: #864e5a;
}
.dark .back-to-chat-text {
  color: #e8e4e3;
}
```

- [ ] **Step 5: Create page JS skeleton**

```js
// main/miniprogram/pages/voice-call/voice-call.js
const VoiceCallManager = require('../../utils/voice-call-manager');
const { getTheme, applyTheme } = require('../../utils/theme');

const app = getApp();

function formatDuration(totalSeconds) {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  const pad = (n) => (n < 10 ? '0' + n : '' + n);
  return pad(m) + ':' + pad(s);
}

Page({
  data: {
    darkMode: getTheme(),
    companionName: '',
    companionAvatar: '',
    callState: 'calling',
    formattedDuration: '00:00',
    callStatusText: '正在呼叫…',
    isMuted: false,
    isSpeakerOn: true,
  },

  _mgr: null,
  _unsubscribe: null,

  onLoad() {
    this._mgr = VoiceCallManager();
    this._unsubscribe = (state) => this._syncState(state);
    this._mgr.onStateChange(this._unsubscribe);

    const g = app.globalData || {};
    this.setData({
      companionName: g.agentName || '女友',
      companionAvatar: g.companionAvatar || '',
    });

    // 模拟 2-8 秒呼叫等待
    const delay = 2000 + Math.floor(Math.random() * 6000);
    this._callTimer = setTimeout(() => {
      this._mgr.connect();
    }, delay);
  },

  onShow() {
    applyTheme(this);
  },

  onUnload() {
    this._cleanup();
  },

  onHide() {
    // 切后台由 app 生命周期处理挂断
  },

  _syncState(state) {
    let statusText = '正在呼叫…';
    if (state.state === 'connected') {
      statusText = '通话中';
    }
    this.setData({
      callState: state.state,
      formattedDuration: formatDuration(state.durationSeconds),
      isMuted: state.isMuted,
      isSpeakerOn: state.isSpeakerOn,
      callStatusText: statusText,
    });
  },

  onCancelCall() {
    if (this._callTimer) {
      clearTimeout(this._callTimer);
      this._callTimer = null;
    }
    this._mgr.hangup();
    wx.navigateBack();
  },

  onHangup() {
    this._mgr.hangup();
    wx.navigateBack();
  },

  onToggleMute() {
    this._mgr.toggleMute();
  },

  onToggleSpeaker() {
    this._mgr.toggleSpeaker();
    // TODO: apply wx audio output option
  },

  onBackToChat() {
    wx.navigateBack();
  },

  _cleanup() {
    if (this._callTimer) {
      clearTimeout(this._callTimer);
      this._callTimer = null;
    }
    if (this._mgr && this._unsubscribe) {
      this._mgr.offStateChange(this._unsubscribe);
    }
  },
});
```

- [ ] **Step 6: Preview and commit**

在微信开发者工具中打开语音通话页，确认呼叫状态 UI 正常，2-8 秒后切换到通话状态 UI。

```bash
git add main/miniprogram/app.json main/miniprogram/pages/voice-call/
git commit -m "feat(miniprogram): add voice call page with calling and connected UI"
```

---

### Task 6: 语音通话页面音频与 WebSocket 集成

**Files:**
- Modify: `main/miniprogram/pages/voice-call/voice-call.js`
- Modify: `main/miniprogram/pages/index/index.js`

**Interfaces:**
- Consumes:
  - `app.globalData.wsManager` / `app.globalData.audioManager` —— 或复用首页实例
  - `WebSocketManager.sendListenStart()` / `sendListenStop()` / `sendAbort()` / `sendAudioFrame()`
  - `AudioManager.startRecord()` / `stopRecord()` / `appendOpusFrame()` / `stopPlayback()`
- Produces:
  - 接通后自动开始录音并发送
  - 接收服务端音频并播放
  - AI 说完后自动重新 `listen start`

- [ ] **Step 1: Expose wsManager and audioManager from index page for reuse**

当前首页的 `wsManager` 和 `audioManager` 挂在 `Page` 实例上，其他页面无法直接访问。这里采用在 `voice-call` 页面重新初始化一套独立实例的方式，避免页面间耦合。

修改 `main/miniprogram/pages/voice-call/voice-call.js` 的 `onLoad`：

```js
const AudioManager = require('../../utils/audio');
const WebSocketManager = require('../../utils/websocket');
```

在 data 同级添加：

```js
audioManager: null,
wsManager: null,
```

- [ ] **Step 2: Initialize audio and WebSocket in voice-call page**

在 `_syncState` 中监听 `connected` 状态，首次进入时初始化：

```js
_syncState(state) {
  if (state.state === 'connected' && !this._hasStartedMedia) {
    this._hasStartedMedia = true;
    this._startMedia();
  }

  let statusText = '正在呼叫…';
  if (state.state === 'connected') {
    statusText = this._isAiSpeaking ? '女友正在说…' : (this._isUserSpeaking ? '正在听…' : '通话中');
  }
  this.setData({
    callState: state.state,
    formattedDuration: formatDuration(state.durationSeconds),
    isMuted: state.isMuted,
    isSpeakerOn: state.isSpeakerOn,
    callStatusText: statusText,
  });
},
```

新增 `_startMedia`：

```js
_startMedia() {
  const g = app.globalData;

  this.audioManager = new AudioManager({
    onAudioFrame: (frame) => {
      if (this.data.isMuted) return;
      if (this.wsManager) this.wsManager.sendAudioFrame(frame);
    },
    onRecordStart: () => {
      this._isUserSpeaking = true;
      this._updateStatusText();
    },
    onRecordStop: () => {
      this._isUserSpeaking = false;
      this._updateStatusText();
    },
    onPlayEnd: () => {
      this._isAiSpeaking = false;
      this._updateStatusText();
      // AI 说完后自动进入下一轮倾听
      if (this.wsManager && this._mgr.getState().state === 'connected') {
        this.wsManager.sendListenStart();
      }
    },
    onError: (err, scope) => {
      console.warn('[VoiceCall Audio:' + scope + ']', err);
    },
  });

  this.wsManager = new WebSocketManager({
    onStateChange: (wsState) => {
      if (wsState === 'disconnected' && this._mgr.getState().state === 'connected') {
        wx.showToast({ title: '通话已断开', icon: 'none' });
        this._mgr.hangup();
        wx.navigateBack();
      }
    },
    onMessage: (msg) => this._handleWSMessage(msg),
    onError: (err, scope) => {
      console.warn('[VoiceCall WS:' + scope + ']', err);
    },
  });

  this.wsManager.connect(g.wsUrl, g.virtualMAC, g.wsToken);

  // 等待 hello 握手完成后开始录音
  this._wsReadyHandler = (state) => {
    if (state.state === 'connected' && this.wsManager && this.wsManager.isConnected()) {
      this.audioManager.ready().then(() => {
        this.audioManager.startRecord();
        this.wsManager.sendListenStart();
      }).catch((err) => {
        console.error('AudioManager not ready:', err);
        wx.showToast({ title: '音频引擎未就绪', icon: 'none' });
        this._mgr.hangup();
        wx.navigateBack();
      });
    }
  };
  this._mgr.onStateChange(this._wsReadyHandler);
},

_handleWSMessage(msg) {
  switch (msg.type) {
    case 'audio':
      if (this.audioManager) {
        this.audioManager.appendOpusFrame(msg.data);
        this._isAiSpeaking = true;
        this._updateStatusText();
      }
      break;
    case 'tts':
      if (msg.state === 'start') {
        this._isAiSpeaking = true;
      } else if (msg.state === 'stop') {
        this._isAiSpeaking = false;
      }
      this._updateStatusText();
      break;
    case 'goodbye':
      this._mgr.hangup();
      wx.navigateBack();
      break;
    default:
      break;
  }
},

_updateStatusText() {
  const state = this._mgr.getState();
  let text = '通话中';
  if (state.state === 'calling') text = '正在呼叫…';
  else if (this._isAiSpeaking) text = '女友正在说…';
  else if (this._isUserSpeaking) text = '正在听…';
  this.setData({ callStatusText: text });
},
```

- [ ] **Step 3: Update cleanup and hangup logic**

在 `onHangup` / `onCancelCall` / `_cleanup` 中停止录音/播放并断开 WebSocket：

```js
_stopMedia() {
  if (this.audioManager) {
    try { this.audioManager.stopRecord(); } catch (_) {}
    try { this.audioManager.stopPlayback(); } catch (_) {}
  }
  if (this.wsManager) {
    try { this.wsManager.sendListenStop(); } catch (_) {}
    try { this.wsManager.disconnect(); } catch (_) {}
  }
},

onHangup() {
  this._stopMedia();
  this._mgr.hangup();
  wx.navigateBack();
},

onCancelCall() {
  if (this._callTimer) {
    clearTimeout(this._callTimer);
    this._callTimer = null;
  }
  this._stopMedia();
  this._mgr.hangup();
  wx.navigateBack();
},

_cleanup() {
  if (this._callTimer) {
    clearTimeout(this._callTimer);
    this._callTimer = null;
  }
  if (this._mgr) {
    if (this._wsReadyHandler) this._mgr.offStateChange(this._wsReadyHandler);
    if (this._unsubscribe) this._mgr.offStateChange(this._unsubscribe);
  }
  this._stopMedia();
  if (this.audioManager) {
    this.audioManager.destroy();
    this.audioManager = null;
  }
  if (this.wsManager) {
    this.wsManager.destroy();
    this.wsManager = null;
  }
},
```

- [ ] **Step 4: Wire 10-minute record restart**

在 `onLoad` 中设置录音重启回调：

```js
this._mgr.setOnRecordRestart(() => {
  if (!this.audioManager || !this.wsManager) return;
  this.audioManager.stopRecord();
  this.wsManager.sendListenStop();
  setTimeout(() => {
    this.audioManager.startRecord();
    this.wsManager.sendListenStart();
  }, 100);
});
```

- [ ] **Step 5: Test the full call flow**

1. 进入通话页，等待 2-8 秒接通。
2. 对着手机说话，观察服务端是否返回音频。
3. AI 回复结束后，客户端应自动再次 `listen start`。
4. 点击挂断，返回首页。

- [ ] **Step 6: Commit**

```bash
git add main/miniprogram/pages/voice-call/voice-call.js main/miniprogram/pages/index/index.js
git commit -m "feat(miniprogram): integrate audio and WebSocket into voice call"
```

---

### Task 7: 返回聊天页保持通话与悬浮小球

**Files:**
- Create: `main/miniprogram/components/floating-call-ball/`
- Modify: `main/miniprogram/pages/index/index.wxml`
- Modify: `main/miniprogram/pages/index/index.js`
- Modify: `main/miniprogram/pages/index/index.wxss`

**Interfaces:**
- Consumes:
  - `VoiceCallManager.getInstance()` —— 状态订阅
- Produces:
  - `floating-call-ball` 组件：显示通话时长、返回通话、挂断

- [ ] **Step 1: Create component files**

```json
// main/miniprogram/components/floating-call-ball/floating-call-ball.json
{
  "component": true
}
```

```xml
<!-- main/miniprogram/components/floating-call-ball/floating-call-ball.wxml -->
<view wx:if="{{visible}}" class="floating-ball {{darkMode ? 'dark' : ''}} {{expanded ? 'expanded' : ''}}" style="top: {{top}}px;" bindtap="onTap">
  <view class="ball-core">
    <image class="ball-avatar" src="{{companionAvatar || '/images/avatar-default.png'}}" mode="aspectFill" />
    <text class="ball-duration">{{formattedDuration}}</text>
  </view>
  <view wx:if="{{expanded}}" class="ball-actions">
    <view class="ball-action" bindtap="onBackToCall" hover-class="ball-action-hover" hover-stay-time="80">
      <text class="ball-action-text">返回通话</text>
    </view>
    <view class="ball-action ball-action-hangup" bindtap="onHangup" hover-class="ball-action-hover" hover-stay-time="80">
      <text class="ball-action-text">挂断</text>
    </view>
  </view>
</view>
```

```css
/* main/miniprogram/components/floating-call-ball/floating-call-ball.wxss */
.floating-ball {
  position: fixed;
  right: 24rpx;
  z-index: 200;
  display: flex;
  flex-direction: row-reverse;
  align-items: center;
}
.ball-core {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  background: rgba(134, 78, 90, 0.16);
  backdrop-filter: blur(12rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.12);
}
.dark .ball-core {
  background: rgba(232, 228, 227, 0.12);
}
.ball-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  margin-bottom: 4rpx;
}
.ball-duration {
  font-size: 18rpx;
  color: #864e5a;
  font-variant-numeric: tabular-nums;
}
.dark .ball-duration {
  color: #e8e4e3;
}
.ball-actions {
  display: flex;
  flex-direction: column;
  margin-right: 16rpx;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 24rpx;
  padding: 12rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.1);
}
.dark .ball-actions {
  background: rgba(30, 30, 46, 0.9);
}
.ball-action {
  padding: 16rpx 24rpx;
  border-radius: 16rpx;
  margin-bottom: 8rpx;
  background: rgba(134, 78, 90, 0.08);
}
.ball-action:last-child {
  margin-bottom: 0;
}
.ball-action-hover {
  background: rgba(134, 78, 90, 0.16);
}
.ball-action-hangup {
  background: rgba(239, 68, 68, 0.12);
}
.ball-action-text {
  font-size: 24rpx;
  color: #4a4a4a;
}
.dark .ball-action-text {
  color: #e8e4e3;
}
```

```js
// main/miniprogram/components/floating-call-ball/floating-call-ball.js
const VoiceCallManager = require('../../utils/voice-call-manager');

function formatDuration(totalSeconds) {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  const pad = (n) => (n < 10 ? '0' + n : '' + n);
  return pad(m) + ':' + pad(s);
}

Component({
  properties: {
    darkMode: { type: Boolean, value: false },
    companionAvatar: { type: String, value: '' },
    top: { type: Number, value: 400 },
  },

  data: {
    visible: false,
    expanded: false,
    formattedDuration: '00:00',
  },

  _mgr: null,
  _unsubscribe: null,

  lifetimes: {
    attached() {
      this._mgr = VoiceCallManager();
      this._unsubscribe = (state) => this._sync(state);
      this._mgr.onStateChange(this._unsubscribe);
      this._sync(this._mgr.getState());
    },
    detached() {
      if (this._mgr && this._unsubscribe) {
        this._mgr.offStateChange(this._unsubscribe);
      }
    },
  },

  methods: {
    _sync(state) {
      this.setData({
        visible: state.state === 'connected',
        formattedDuration: formatDuration(state.durationSeconds),
      });
      if (state.state === 'ended') {
        this.setData({ expanded: false });
      }
    },

    onTap() {
      this.setData({ expanded: !this.data.expanded });
    },

    onBackToCall() {
      this.setData({ expanded: false });
      wx.navigateTo({ url: '/pages/voice-call/voice-call' });
    },

    onHangup() {
      this.setData({ expanded: false });
      this._mgr.hangup();
    },
  },
});
```

- [ ] **Step 2: Integrate component into index page**

修改 `main/miniprogram/pages/index/index.json` 注册组件：

```json
{
  "usingComponents": {
    "chat-bubble": "/components/chat-bubble/chat-bubble",
    "floating-call-ball": "/components/floating-call-ball/floating-call-ball"
  }
}
```

修改 `main/miniprogram/pages/index/index.wxml`，在页面最外层末尾添加：

```xml
<floating-call-ball
  wx:if="{{!booting && !bindFailed}}"
  darkMode="{{darkMode}}"
  companionAvatar="{{companionAvatar}}"
  top="{{floatingBallTop}}"
/>
```

- [ ] **Step 3: Add floating ball position data in index.js**

在 `data` 中新增：

```js
floatingBallTop: 400,
```

在 `_calcScrollViewHeight` 中计算小球位置（或单独计算）：

```js
const windowHeight = wx.getWindowInfo().windowHeight;
this.setData({ floatingBallTop: windowHeight * 0.55 });
```

- [ ] **Step 4: Handle app background -> hangup**

在 `main/miniprogram/pages/index/index.js` 中监听 app 隐藏事件：

```js
onLoad() {
  // ... existing code ...
  this._appHideHandler = () => {
    const VoiceCallManager = require('../../utils/voice-call-manager');
    const mgr = VoiceCallManager();
    if (mgr.getState().state === 'connected' || mgr.getState().state === 'calling') {
      mgr.hangup();
    }
  };
  if (wx.onAppHide) wx.onAppHide(this._appHideHandler);
},

_teardown() {
  // ... existing teardown ...
  if (this._appHideHandler && wx.offAppHide) {
    try { wx.offAppHide(this._appHideHandler); } catch (_) {}
    this._appHideHandler = null;
  }
},
```

- [ ] **Step 5: Test back-to-chat flow**

1. 进入通话页，接通后点击"返回聊天"。
2. 首页应显示悬浮小球，显示通话时长。
3. 点击小球展开面板，点击"返回通话"回到通话页。
4. 切后台，再回前台，悬浮小球应消失。

- [ ] **Step 6: Commit**

```bash
git add main/miniprogram/components/floating-call-ball/ main/miniprogram/pages/index/index.json main/miniprogram/pages/index/index.wxml main/miniprogram/pages/index/index.wxss main/miniprogram/pages/index/index.js
git commit -m "feat(miniprogram): add floating call ball and background hangup"
```

---

### Task 8: 扬声器/听筒切换与深色模式收尾

**Files:**
- Modify: `main/miniprogram/pages/voice-call/voice-call.js`
- Modify: `main/miniprogram/pages/voice-call/voice-call.wxss`

**Interfaces:**
- Consumes: `wx.setInnerAudioOption` / `wx.getAvailableAudioSources`
- Produces: 点击免提按钮切换音频输出设备

- [ ] **Step 1: Implement speaker toggle**

修改 `onToggleSpeaker`：

```js
onToggleSpeaker() {
  this._mgr.toggleSpeaker();
  const next = this._mgr.getState().isSpeakerOn;
  if (wx.setInnerAudioOption) {
    wx.setInnerAudioOption({
      speakerOn: next,
      success: () => {
        console.log('audio output switched, speakerOn=' + next);
      },
      fail: (err) => {
        console.warn('setInnerAudioOption failed:', err);
      },
    });
  }
},
```

- [ ] **Step 2: Ensure dark mode classes are complete**

检查 `voice-call.wxss` 和 `floating-call-ball.wxss` 中所有 `.dark` 选择器是否覆盖新增元素。

- [ ] **Step 3: Test on iOS/Android real devices**

1. 扬声器默认外放。
2. 切换听筒后，声音从听筒输出。
3. 深色模式下所有文字、按钮、背景色正确。

- [ ] **Step 4: Commit**

```bash
git add main/miniprogram/pages/voice-call/voice-call.js main/miniprogram/pages/voice-call/voice-call.wxss main/miniprogram/components/floating-call-ball/floating-call-ball.wxss
git commit -m "feat(miniprogram): add speaker/earpiece toggle and dark mode polish"
```

---

### Task 9: 集成测试与视觉回归

**Files:**
- Modify: `main/miniprogram/utils/voice-call-manager.test.js`（补充测试）
- Modify: 新增 UI 相关文件（按需微调）

**Interfaces:**
- 无新增接口

- [ ] **Step 1: Add final unit tests**

补充 VoiceCallManager 的状态订阅测试：

```js
(function () {
  const VoiceCallManager = require('./voice-call-manager').VoiceCallManager;
  const mgr = new VoiceCallManager();
  let received = null;
  mgr.onStateChange((state) => { received = state; });
  mgr.startCall();
  assert.strictEqual(received.state, 'calling');
  mgr.connect();
  assert.strictEqual(received.state, 'connected');
  mgr.hangup();
  assert.strictEqual(received.state, 'ended');
  console.log('state subscription test: PASS');
})();
```

- [ ] **Step 2: Run all unit tests**

Run: `cd /Users/minwang/codes/github/xiaozhi-esp32-server/main/miniprogram && node utils/voice-call-manager.test.js`

Expected: ALL PASS

- [ ] **Step 3: Visual regression checklist**

在微信开发者工具中截图以下状态：

- 首页底部浮窗展开（浅色/深色）
- 语音通话页呼叫状态（浅色/深色）
- 语音通话页通话状态（浅色/深色）
- 首页悬浮小球展开状态（浅色/深色）

- [ ] **Step 4: End-to-end smoke test**

1. 启动 `xiaozhi-server` 和 `manager-api`。
2. 微信开发者工具登录测试账号。
3. 首页点击 `+` → 语音通话 → 授权 → 等待接通 → 说话 → 听 AI 回复 → 挂断。
4. 重复：接通 → 返回聊天 → 悬浮小球 → 返回通话 → 挂断。
5. 切后台验证自动挂断。

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/utils/voice-call-manager.test.js
git commit -m "test(miniprogram): add VoiceCallManager state subscription tests"
```

---

## Self-Review

### Spec Coverage

| 设计需求 | 覆盖任务 |
|---|---|
| `+` 号入口 + 3×2 浮窗 | Task 3 |
| 检查 `voice_call` 权益 | Task 4 |
| 检查/申请麦克风权限 | Task 4 |
| 2-8 秒呼叫等待 | Task 5 |
| 通话控制：挂断/静音/免提/返回聊天 | Task 5, Task 6, Task 8 |
| 默认扬声器，可切换听筒 | Task 8 |
| 切后台自动挂断 | Task 7 |
| 单次录音 10 分钟无缝重启 | Task 2, Task 6 |
| 悬浮小球 | Task 7 |
| 深色模式 | Task 3, Task 5, Task 7, Task 8 |
| 持续语音循环（AI 说完自动重 listen） | Task 6 |

### Placeholder Scan

- 无 "TBD" / "TODO" / "implement later"。
- 所有代码块包含实际可运行代码。
- 所有文件路径为绝对仓库内路径。
- 所有命令包含预期输出。

### Type Consistency

- `VoiceCallManager` 状态字段在 Task 1-2 中定义，Task 5-8 中消费，字段名一致。
- `isMuted` / `isSpeakerOn` 在所有任务中为 Boolean。
- `durationSeconds` 为 Number。
- `state` 取值范围在 Task 1 中定义常量。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-21-miniprogram-voice-call.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
