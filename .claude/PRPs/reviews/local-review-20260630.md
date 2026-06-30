# Local Code Review — 订阅页面独立化改造

**Reviewed**: 2026-06-30
**Branch**: f-mini-subscription
**Decision**: REQUEST CHANGES

## Summary

本次改造将 settings 页的订阅浮窗重构为独立 `pages/subscription/subscription` 页面，新增 silver/gold 多周期套餐展示与权益对比表，并清理了 settings 页中约 370 行旧浮窗逻辑。整体结构清晰，深色模式与支付流程迁移完整，未发现安全漏洞。但新页面存在函数过长、生产环境不应使用 `console.warn`、以及误改了本地开发 URL 等问题，需在合并前修复。

## Findings

### CRITICAL

None

### HIGH

#### 1. `_doSignContract` 函数超过 50 行
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Lines**: 234–330（约 96 行）
- **Description**: 该函数同时承担下单、Mock/真实支付、订单轮询、刷新全局订阅状态、档位变更标记、返回导航等职责，远超 50 行上限，可读性与可测试性差。
- **Suggested fix**: 拆分为若干小函数，例如：
  - `_createSubscriptionOrder(planId)` — 创建订单并校验响应
  - `_processPayment(order)` — 区分 Mock / 微信支付
  - `_finalizePurchase(order, previousPlanCode)` — 轮询、刷新、标记重连、返回

#### 2. 新页面使用 `console.warn` 而非项目日志工具
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Lines**: 187, 324, 347
- **Description**: 直接使用 `console.warn` 会在生产环境泄露调试信息。项目已提供 `utils/logger.js`，其 `warn` 方法在 release 环境下会自动静默。
- **Suggested fix**: 顶部引入 `const logger = require('../../utils/logger');`，将所有 `console.warn` 替换为 `logger.warn(...)`。

### MEDIUM

#### 3. 误提交本地开发 URL 变更
- **File**: `main/miniprogram/utils/request.js`
- **Line**: 9
- **Description**: `BASE_URL` 从 `http://192.168.4.12:8002/xiaozhi` 改为 `http://192.168.48.81:8002/xiaozhi`，属于个人本地环境配置，与本次订阅页面改造无关。
- **Suggested fix**: 提交前回滚该改动，保持使用仓库原有地址。

#### 4. 未校验 `plansRes.data` 是否为数组
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Line**: 137–139
- **Description**: 若后端返回非数组（例如包装对象或 null），`rawPlans.filter(...)` 将直接抛运行时错误。
- **Suggested fix**: 
  ```js
  var rawPlans = (plansRes && plansRes.code === 0 && Array.isArray(plansRes.data)) ? plansRes.data : [];
  ```

#### 5. 订单轮询超时未处理
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Lines**: 300, 332–349
- **Description**: `_waitOrderFulfilled` 在 12 次轮询后返回 `false`，但调用方未检查返回值，仍继续显示“契约签订成功”。若履约延迟或失败，用户可能看到错误成功提示。
- **Suggested fix**: 检查返回值，超时后给出友好提示（如“支付状态确认中，请稍后到订单页查看”），或延长/重试轮询。

#### 6. 魔法数字：订阅状态 `1`
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Line**: 145
- **Description**: `subRes.data.status === 1` 直接使用魔法数字，含义不明确。
- **Suggested fix**: 在文件顶部定义常量，如 `var SUBSCRIPTION_STATUS_ACTIVE = 1;`。

#### 7. 新增业务逻辑缺少测试
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Description**: `normalizePlan`、`getActionInfo`、`buildFeatureTable` 包含核心展示与按钮状态判定逻辑，目前无单元测试覆盖。
- **Suggested fix**: 为上述纯函数编写单元测试，覆盖未订阅、同档续费、升级、已拥有更高档位等场景。

### LOW

#### 8. 周期天数阈值硬编码
- **File**: `main/miniprogram/pages/subscription/subscription.js`
- **Lines**: 23–24, 26–28
- **Description**: 90 天、365 天直接作为季卡/年卡判断阈值。
- **Suggested fix**: 定义常量 `DURATION_QUARTER_DAYS = 90`、`DURATION_YEAR_DAYS = 365`。

#### 9. 权益对比表使用 Unicode 符号 `✓`
- **File**: `main/miniprogram/pages/subscription/subscription.wxml`
- **Lines**: 62, 65
- **Description**: 使用 `✓` / `—` 符号。虽非 emoji，但项目明确禁止 emoji 图标，建议后续统一换成本地 PNG 图标或 CSS 绘制。
- **Suggested fix**: 保留当前实现作为 MVP，后续迭代替换为图片资源以保持设计系统一致性。

## Validation Results

| Check | Result |
|---|---|
| JS syntax (`node --check`) | Pass |
| JSON validity (`app.json`, `subscription.json`) | Pass |
| Orphaned references to已删除的 settings 方法 | Pass（无残留引用） |
| Java / Maven compile | Skipped（无 Java 源文件变更） |
| Miniprogram tests | Skipped（项目未配置） |

## Files Reviewed

| File | Change Type |
|---|---|
| `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` | Modified |
| `main/manager-api/src/main/resources/db/changelog/202606301500.sql` | Added |
| `main/miniprogram/app.json` | Modified |
| `main/miniprogram/pages/index/index.js` | Modified |
| `main/miniprogram/pages/settings/settings.js` | Modified |
| `main/miniprogram/pages/settings/settings.wxml` | Modified |
| `main/miniprogram/pages/settings/settings.wxss` | Modified |
| `main/miniprogram/utils/request.js` | Modified（含需回滚的本地 URL） |
| `main/miniprogram/pages/subscription/subscription.js` | Added |
| `main/miniprogram/pages/subscription/subscription.json` | Added |
| `main/miniprogram/pages/subscription/subscription.wxml` | Added |
| `main/miniprogram/pages/subscription/subscription.wxss` | Added |

## Next Steps

1. 修复 HIGH 问题：拆分 `_doSignContract`，替换 `console.warn` 为 `logger.warn`。
2. 回滚 `utils/request.js` 中的本地 IP 变更。
3. 处理 MEDIUM 问题中的数组校验、轮询超时、魔法数字。
4. 补充 `normalizePlan` / `getActionInfo` / `buildFeatureTable` 的单元测试。
5. 重新运行审查确认无 HIGH 及以上问题后，方可提交。
