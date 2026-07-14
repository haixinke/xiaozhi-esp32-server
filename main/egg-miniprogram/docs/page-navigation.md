# 蛋宝宝小程序页面跳转逻辑

> 面向新开发者的导航地图。基于 `miniprogram/app.json`、`miniprogram/app.js` 与各页面 JS 中的 `wx.navigateTo` / `wx.redirectTo` / `wx.switchTab` / `wx.reLaunch` / `wx.navigateBack` 梳理。

## 目录

- [入口流程](#入口流程)
- [Tab 页面](#tab-页面)
- [核心流程跳转图](#核心流程跳转图)
  - [领蛋流程](#领蛋流程)
  - [孵化流程](#孵化流程)
  - [破壳后流程](#破壳后流程)
- [「我的」页面入口](#我的页面入口)
- [页面安全兜底逻辑](#页面安全兜底逻辑)
- [给新人的记忆口诀](#给新人的记忆口诀)

---

## 入口流程

小程序启动后，页面栈由 `app.json` 中 `pages` 数组的第一项决定：

```
启动
  └── pages/welcome/welcome（启动欢迎/授权页）
        ├── 已登录用户：onLoad 自动跳转到首页
        │     └── wx.switchTab → pages/home/home
        └── 未登录用户：勾选隐私协议后点击「微信登录并进入」
              └── wx.switchTab → pages/home/home
```

说明：

- `app.js` 的 `onLaunch` 只执行静默微信登录（`wx.login` → `/wechat/login`），不主动跳转。
- `welcome.js` 的 `onLoad` 调用 `getApp().ensureLogin()`：若已取得 `userId`，直接 `switchTab` 到 `home`；否则留在本页等待用户授权。
- 用户点击授权按钮前必须先同意隐私协议，可点击隐私文案进入 `pages/privacy/privacy` 查看详情。

---

## Tab 页面

小程序只有 2 个底部 tab，在 `app.json` 的 `tabBar.list` 中声明：

| Tab | 页面路径 | 作用 |
| --- | --- | --- |
| 蛋宝宝 | `pages/home/home` | 主舞台，显示蛋/宠物、孵化进度、每日状态 |
| 我的 | `pages/my/my` | 账号中心、设置与各类辅助页面入口 |

所有二级页面最终都会回到这两个 tab 之一。

---

## 核心流程跳转图

### 领蛋流程

用户首次进入或当前没有蛋时触发：

```
pages/home/home（无蛋空态）
  └── 点击「添加设备 / 领蛋」
        └── wx.navigateTo → pages/add-device/add-device
              └── 输入激活码验证成功
                    └── wx.switchTab → pages/home/home
```

### 孵化流程

蛋已领取但尚未破壳时触发：

```
pages/home/home
  └── 点击主按钮（stage 为孵化中：waiting / hatching / soon）
        └── wx.navigateTo → pages/hatch-guide/hatch-guide（修炼手册）
              ├── 任务「贴贴」→ wx.switchTab → pages/home/home（提示长按蛋壳）
              ├── 任务「起昵称」→ wx.navigateTo → pages/nickname/nickname
              ├── 任务「写愿望」→ wx.navigateTo → pages/wish/wish
              ├── 任务「蛋蛋早教班」→ wx.navigateTo → pages/lesson/lesson
              └── 任务「彩蛋涂鸦」→ wx.navigateTo → pages/doodle/doodle
                    └── 各任务页完成后
                          └── wx.navigateBack → pages/hatch-guide/hatch-guide

pages/home/home
  └── 点击主按钮（stage === 'ready'，可以破壳）
        └── wx.navigateTo → pages/hatch/hatch（破壳仪式）
              └── 破壳动画结束并成功后
                    └── wx.redirectTo → pages/collection-card/collection-card?new=1
```

### 破壳后流程

蛋已破壳，进入长期养成/对话阶段：

```
pages/home/home（stage === 'hatched'）
  ├── 点击主按钮
  │     └── wx.navigateTo → pages/chat/chat（语音/文字对话）
  └── 点击宠物
        └── wx.navigateTo → pages/pet-detail/pet-detail（宠物档案）
              ├── 点击对话
              │     └── wx.navigateTo → pages/chat/chat
              └── 点击收藏卡
                    └── wx.navigateTo → pages/collection-card/collection-card

pages/collection-card/collection-card
  ├── 点击卡册
  │     └── wx.navigateTo → pages/album/album
  └── 点击宠物档案
        └── wx.navigateTo → pages/pet-detail/pet-detail

pages/album/album
  └── 点击某张卡片
        └── wx.navigateTo → pages/collection-card/collection-card
```

---

## 「我的」页面入口

`pages/my/my` 作为账号中心，集中进入辅助页面：

```
pages/my/my
  ├── 点击用户卡
  │     └── wx.navigateTo → pages/profile/profile
  ├── 系统设置
  │     └── wx.navigateTo → pages/settings/settings
  ├── 账号相关
  │     └── wx.navigateTo → pages/account/account
  │           ├── 退出登录
  │           │     └── wx.reLaunch → pages/welcome/welcome
  │           ├── 重置体验数据
  │           │     └── wx.switchTab → pages/home/home
  │           └── 注销账号
  │                 └── wx.navigateTo → pages/deregister/deregister
  │                       └── 取消/返回
  │                             └── wx.navigateBack
  ├── 帮助中心
  │     └── wx.navigateTo → pages/help/help
  ├── 隐私协议
  │     └── wx.navigateTo → pages/privacy/privacy
  └── 我的激活码
        └── wx.navigateTo → pages/invite-codes/invite-codes
```

辅助页面基本都是单向进入：从 `my` 进入，返回即回到 `my`。

---

## 页面安全兜底逻辑

部分页面会在 `onLoad` 或 `onShow` 中校验状态，不满足条件时自动返回或重定向：

| 页面 | 校验条件 | 不满足时的行为 |
| --- | --- | --- |
| `pages/home/home` | 当前是否有蛋 | 无蛋时显示空态，引导去 `add-device` |
| `pages/hatch-guide/hatch-guide` | 当前是否有 pet | 无 pet 时 `switchTab` 回 `home` |
| `pages/hatch/hatch` | 是否已有收藏卡 / 是否已 ready | 已有卡则 `redirectTo` 到 `collection-card`；未 ready 则 `navigateBack` |
| `pages/chat/chat` | 是否已破壳 | 未破壳则 `navigateBack` |
| `pages/pet-detail/pet-detail` | 是否已破壳 | 未破壳则 `navigateBack` |
| `pages/collection-card/collection-card` | 是否有收藏卡 | 无收藏卡则 `navigateBack` |

---

## 给新人的记忆口诀

- **入口永远走 `welcome`**，但老用户会秒切到 `home`。
- **只有两个老家**：`home`（蛋宝宝）和 `my`（我的）。
- **没蛋先领蛋**：`home` → `add-device` → 成功后回到 `home`。
- **孵化做任务**：`home` → `hatch-guide` → `nickname / wish / lesson / doodle` → 返回 `hatch-guide`。
- **能破壳就破壳**：`home` → `hatch` → `collection-card?new=1`。
- **破壳后聊天/看档案**：`home` → `chat` / `pet-detail`。
- **卡册和档案互通**：`collection-card` ↔ `album` ↔ `pet-detail`。
- **辅助页都是单程**：从 `my` 进入，返回就是 `my`。
- **退出/重置/注销**：统一在 `account` 页处理。

改页面时，先判断它属于「领蛋 / 孵化 / 破壳 / 对话 / 我的」哪条主线，再找对应的入口和返回路径即可。
