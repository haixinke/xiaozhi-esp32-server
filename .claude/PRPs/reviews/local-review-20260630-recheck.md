# Local Code Review — 订阅页面独立化改造（复审）

**Reviewed**: 2026-06-30
**Branch**: f-mini-subscription
**Decision**: REQUEST CHANGES

## Summary

本次复审仅覆盖当前未提交变更：`pages/subscription/` 下的四个文件。此前报告的 HIGH 问题（`_doSignContract` 过长、`console.warn`）已修复；`_doSignContract` 被拆分为 5 个小函数，全部 ≤ 50 行，`console.warn` 已全部替换为 `logger.warn`。但新发现 `loadPageData` 仍为 64 行，超过 50 行上限，需在合并前拆分。

## Findings

### CRITICAL

None

### HIGH

#### 1. `loadPageData` 函数超过 50 行
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Lines**: 122–185（64 行）
- **Description**: 该函数同时处理并行请求、套餐归一化、权益表构建、当前订阅解析、默认档位/周期选择、最终 setData。逻辑过多，可读性和可测试性不足。
- **Suggested fix**: 拆分为：
  - `_normalizePlans(rawPlans)` — 返回 { silverPlans, goldPlans }
  - `_buildCurrentSub(subRes)` — 从 `/subscription/me` 响应构建 currentSub
  - `_resolveDefaultPlan(plans, activeTab, currentSub)` — 决定默认 tab 和 plan
  - `loadPageData` 只负责编排 Promise.all、调用上述 helper、setData

### MEDIUM

#### 2. 未校验 `plansRes.data` 是否为数组
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Line**: 131
- **Description**: 若后端返回非数组，`rawPlans.filter(...)` 将抛运行时错误。
- **Suggested fix**: `Array.isArray(plansRes.data) ? plansRes.data : []`

#### 3. 魔法数字：订阅状态 `1`
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Line**: 138
- **Description**: `subRes.data.status === 1` 含义不明确。
- **Suggested fix**: 定义常量 `var SUBSCRIPTION_STATUS_ACTIVE = 1;`

#### 4. 订单轮询超时未处理
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Lines**: 310, 328–345
- **Description**: `_waitOrderFulfilled` 超时返回 `false`，但 `_finalizePurchase` 未检查，仍显示“契约签订成功”。
- **Suggested fix**: 检查返回值，超时后给出友好提示。

#### 5. 新增业务逻辑缺少测试
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Description**: `normalizePlan`、`getActionInfo`、`buildFeatureTable` 等纯函数无单元测试。
- **Suggested fix**: 补充覆盖未订阅/续费/升级/降级等场景的测试。

### LOW

#### 6. 周期天数阈值硬编码
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Lines**: 24–25, 27–29
- **Description**: 90 / 365 天直接用于季卡/年卡判断。
- **Suggested fix**: 使用命名常量。

#### 7. 权益对比表使用 Unicode 符号 `✓`
- **File**: `main/miniprogram/pages/subscription/subscription.wxml`
- **Lines**: 56, 59
- **Description**: 虽非 emoji，但项目禁止 emoji 图标，建议后续统一换成本地 PNG/CSS 图形。

## Validation Results

| Check | Result |
|---|---|
| JS syntax (`node --check`) | Pass |
| JSON validity (`subscription.json`) | Pass |
| No `console.log` / `console.warn` | Pass |
| Function length ≤ 50 (except `loadPageData`) | Pass |

## Files Reviewed

| File | Change Type |
|---|---|
| `main/miniprogram/pages/subscription/subscription.js` | Modified |
| `main/miniprogram/pages/subscription/subscription.json` | Modified |
| `main/miniprogram/pages/subscription/subscription.wxml` | Modified |
| `main/miniprogram/pages/subscription/subscription.wxss` | Modified |

## Next Steps

1. 拆分 `loadPageData` 使其 ≤ 50 行。
2. （推荐）顺手处理 MEDIUM 问题：数组校验、魔法数字、轮询超时。
3. 重新审查确认无 HIGH 及以上问题后提交。
