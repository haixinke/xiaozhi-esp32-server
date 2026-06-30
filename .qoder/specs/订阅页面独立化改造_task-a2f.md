# 订阅页面独立化改造

## Context

当前订阅购买、详情、续费/升级全部以浮窗形式嵌入 settings 页（约 110 行 WXML + 370 行 JS + 650 行 CSS）。引入 silver/gold 的月/季/年六档套餐后，浮窗空间不足以承载分组展示和权益对比。参照"星野"App 的全屏订阅页布局，将其重构为独立页面，提升信息密度和交互体验。

---

## Task 1: 新建订阅页面骨架

**新增文件:**
- `main/miniprogram/pages/subscription/subscription.js`
- `main/miniprogram/pages/subscription/subscription.wxml`
- `main/miniprogram/pages/subscription/subscription.wxss`
- `main/miniprogram/pages/subscription/subscription.json`

**修改文件:**
- `main/miniprogram/app.json` — pages 数组增加 `"pages/subscription/subscription"`

**subscription.json 配置:**
```json
{ "navigationStyle": "custom", "usingComponents": {} }
```

---

## Task 2: 实现页面数据加载与状态管理

**subscription.js 核心结构:**

```
onLoad(options)
 ├─ 解析 options.from / options.tab
 ├─ 计算自定义导航栏高度 (statusBarHeight + navBarHeight)
 ├─ applyTheme(this)
 └─ loadPageData()
      ├─ Promise.all([ GET /subscription/plans, GET /subscription/me ])
      ├─ 分组: silverPlans / goldPlans (filter by planCode)
      ├─ 每个 plan normalize: priceYuan, promoYuan, periodLabel, monthlyYuan
      ├─ 构建权益对比表 allFeatures: [{code, label, silver:bool, gold:bool}]
      ├─ 设置默认选中: activeTab 对应组的月卡
      └─ 如有 currentSub 计算剩余时间
```

**关键增强 — normalizePlan:**
- 新增 `periodLabel`（月卡/季卡/年卡）
- 新增 `monthlyYuan`（季/年卡显示月均价）

**补全 featureLabel:**
- 增加 `'chat_no_limit': '不限次聊天'`

---

## Task 3: 实现页面模板 (wxml)

布局由上到下 5 个区域:

| 区域 | 内容 |
|---|---|
| 自定义导航栏 | ← 返回 + 胶囊 Tab (心动互联 / 灵魂共鸣) |
| 标题区 | "签订 XXX" + "解锁 N 项专属权益" + 已订阅状态条(条件渲染) |
| 周期卡片(scroll-x) | 月卡 ¥19.90 / 季卡 ¥53.70 (¥17.9/月) / 年卡 ¥191.00 (¥15.9/月) |
| 权益对比表 | 表头 + N 行 (feature name / silver✓or— / gold✓or—) |
| 固定底部栏 | "¥XX.XX 签订契约" 按钮 + 条款文字 |

---

## Task 4: 实现页面样式 (wxss) + Dark Mode

- Light mode 基础样式 → `.dark .xxx` 覆盖
- 色彩体系沿用项目现有：light `#f6f3f2` / dark `#121220`，强调色 `#864e5a` / `#d4737a`
- 自定义导航栏动态高度 (statusBarHeight + navBarHeight px)
- Tab 胶囊选中态: 实底 `#864e5a` + 白字
- 周期卡片选中态: 描边 + 阴影
- 底部按钮: `linear-gradient(135deg, #864e5a, #d4737a)`

---

## Task 5: 迁移购买流程逻辑

从 `settings.js` 完整迁移到 `subscription.js`:
- `_doSignContract(planId)` — 下单 → 支付 → 轮询 → 刷新 → navigateBack
- `_waitOrderFulfilled(outTradeNo)` — 12 次轮询
- 购买成功后: `app.fetchSubscription()` → 设 `needReconnectAfterSub` → `wx.navigateBack()`

**底部按钮四状态判定:**

| 条件 | 按钮文案 | action |
|---|---|---|
| 未订阅 | ¥XX.XX 签订契约 | subscribe |
| 同档位 | ¥XX.XX 续费 | renew |
| 更高档位 | 升级到灵魂共鸣 ¥XX.XX | upgrade |
| 已有更高档位 | 当前已拥有更高档位(灰) | disabled |

**升级逻辑修复:** 不再用 `sort > currentSort`，改为按 `planCode` 档位等级比较：
```
PLAN_RANK = { silver: 1, gold: 2 }
```

---

## Task 6: 清理 settings 页

**settings.js 改造:**
- 删除: `loadPlans`, `loadMySubscription`, `_doSignContract`, `_waitOrderFulfilled`, `onSignContract`, `onPlanSelect`, `onRenew`, `onUpgrade`, `onConfirmSubmit`, `formatDate`, 浮窗 show/hide 方法 (约 370 行)
- 保留: `loadSubscription`, `getIdentityName`, 其他无关功能
- 改写 `onContractTap`:
  ```js
  onContractTap() {
    var tab = this.data.planCode || 'silver';
    wx.navigateTo({ url: '/pages/subscription/subscription?from=settings&tab=' + tab });
  }
  ```
- 删除 `openContractPopupAfterSwitch` 相关检测代码

**settings.wxml:** 删除第 94-204 行的三个浮窗块

**settings.wxss:** 删除约 650 行浮窗相关 CSS (light + dark)

---

## Task 7: 改造 index.js 入口

**修改 `main/miniprogram/pages/index/index.js`:**

```js
// 原: app.globalData.openContractPopupAfterSwitch = true; wx.switchTab(...)
// 改为:
wx.navigateTo({ url: '/pages/subscription/subscription?from=voiceCall&tab=gold' });

// onQuotaUpgrade 同理:
wx.navigateTo({ url: '/pages/subscription/subscription?from=quotaUpgrade' });
```

删除 `openContractPopupAfterSwitch` 全局标志位。

---

## 涉及文件汇总

| 操作 | 文件路径 |
|---|---|
| 新增 | `main/miniprogram/pages/subscription/subscription.js` |
| 新增 | `main/miniprogram/pages/subscription/subscription.wxml` |
| 新增 | `main/miniprogram/pages/subscription/subscription.wxss` |
| 新增 | `main/miniprogram/pages/subscription/subscription.json` |
| 修改 | `main/miniprogram/app.json` |
| 修改 | `main/miniprogram/pages/settings/settings.js` |
| 修改 | `main/miniprogram/pages/settings/settings.wxml` |
| 修改 | `main/miniprogram/pages/settings/settings.wxss` |
| 修改 | `main/miniprogram/pages/index/index.js` |

---

## 验证方案

1. **未订阅用户**: settings "我的契约" → 跳转订阅页 → Tab 默认 silver → 选月卡 → 点购买 → mock 支付成功 → 返回 settings → 身份标签更新
2. **已订阅 silver 用户**: 进入订阅页 → 显示当前状态条 → 切 gold Tab → 底部显示"升级" → 点击升级 → mock 成功 → 返回
3. **已订阅 gold 用户**: 进入订阅页 → Tab 默认 gold → 底部显示"续费" → 选季卡 → 点续费 → 成功
4. **从 index 入口**: 语音通话点击 → 弹窗确认 → 跳转订阅页(tab=gold) → 正常流程
5. **配额升级入口**: index 配额耗尽 → 点升级 → 跳转订阅页 → 正常流程
6. **Dark Mode**: 切换深色模式 → 进入订阅页 → 所有元素正确渲染深色主题
7. **降级保护**: gold 用户切到 silver Tab → 底部按钮灰显"当前已拥有更高档位"
