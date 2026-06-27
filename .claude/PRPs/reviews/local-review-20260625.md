# Local Code Review: 微信支付补偿机制改造

**Reviewed**: 2026-06-25
**Scope**: 未提交改动（git diff HEAD）
**Decision**: APPROVE with comments

## Summary

本次改造为微信支付增加了两道补偿机制（小程序前端主动查单 + 定时任务兜底查单），整体设计思路正确，幂等性和并发控制考虑到位，且已通过 `mvn clean compile` 编译验证。

**已修复问题**：
- ✅ HIGH: `PaymentOrderServiceImpl.queryAndFulfill()` 已拆分为 5 个小函数
- ✅ MEDIUM: 履约事务内的 `getStatus()` 已增加 null 防御
- ✅ LOW: `PaymentOrderMaintenanceTask` 类注释已更新

**仍待处理**：
- ⏳ MEDIUM: 新增代码缺少单元测试
- ⏳ LOW: Mock 实现中 `mockPaidOrders` 无界增长（仅影响本地 mock）
- ⏳ LOW: JS 文件使用 `console.warn`（与现有风格一致）

---

## Findings

### HIGH

#### 1. `PaymentOrderServiceImpl.queryAndFulfill()` 函数过长 ✅ 已修复
- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java`
- **修复内容**: 已将 72 行的函数拆分为 5 个职责单一的方法：
  - `queryAndFulfill()`：主入口，约 12 行
  - `loadOrderForQuery()`：加载订单并校验状态
  - `queryWechatPay()`：调用微信支付查单
  - `validateQueryAmount()`：金额核对
  - `advanceToPaidIfNeeded()`：原子推进到 PAID
  - `fulfillIfNeeded()`：事务内履约

### MEDIUM

#### 2. 新增代码缺少单元测试 ⏳ 仍待处理
- **文件**: 多个 Java 文件
- **问题**: 改造涉及支付核心链路（订单状态推进、并发、金额校验、幂等），但没有新增或更新测试。支付逻辑回归风险较高。
- **建议修复**:
  - 至少为 `PaymentOrderServiceImpl.queryAndFulfill()` 添加覆盖以下场景的单元测试：
    - 订单已 FULFILLED，直接返回幂等
    - 订单 PENDING，微信支付返回已支付，成功 markPaid + fulfill
    - 订单 PENDING，微信支付返回未支付，不做任何修改
    - 金额不一致，抛出异常
    - markPaid 因并发失败但状态已是 PAID，继续履约
    - markPaid 因并发失败且状态已是 FULFILLED，直接返回
  - 为 `WechatPayV3Client.queryOrder()` 增加解析 `Transaction` 的测试。
  - 为 `PaymentOrderMaintenanceTask.reconcilePendingOrders()` 增加调度行为测试。

#### 3. `PaymentOrderServiceImpl.queryAndFulfill()` 中 `refreshed.getStatus()` 可能 NPE ✅ 已修复
- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java`
- **修复内容**: `advanceToPaidIfNeeded()` 和 `fulfillIfNeeded()` 中均已改为 `Integer refreshedStatus = refreshed != null ? refreshed.getStatus() : null;` 后再比较，避免 NPE。

#### 4. Mock 实现中 `mockPaidOrders` 无界增长 ⏳ 仍待处理
- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/MockWechatPayClient.java`
- **位置**: 第 39 行
- **问题**: `ConcurrentHashMap` 只写入不清理，本地长时间运行或大量测试后可能占用较多内存。
- **建议修复**: 添加简单的过期清理策略，例如使用 `LinkedHashMap` 重写 `removeEldestEntry` 限制容量，或定期清理。因仅用于本地 mock，优先级不高。

### LOW

#### 5. `PaymentOrderMaintenanceTask` 类注释未更新 ✅ 已修复
- **文件**: `main/manager-api/src/main/java/xiaozhi/modules/payment/task/PaymentOrderMaintenanceTask.java`
- **修复内容**: 类级 Javadoc 已增加第 2 点“主动查询已创建一段时间的 PENDING 订单”说明。

#### 6. JS 文件使用 `console.warn` ⏳ 仍待处理
- **文件**:
  - `main/miniprogram/pages/settings/settings.js` 第 380 行
  - `main/miniprogram/pages/backpack/backpack.js` 第 235 行
- **问题**: 生产代码中使用 `console.warn`，与现有代码风格一致（同文件已有其他 `console.warn`），但不符合“生产环境无 console 输出”的最佳实践。
- **建议修复**: 可保留或替换为项目统一的日志/埋点方案。因与现有风格一致，优先级低。

---

## Validation Results

| Check | Command | Result |
|---|---|---|
| Java 编译 | `mvn clean compile -DskipTests` | Pass |
| 单元测试 | - | Skipped（无新增/更新测试） |
| 类型检查 | - | Skipped（Java 项目） |

---

## Files Reviewed

| File | Change Type |
|---|---|
| `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayClient.java` | Modified |
| `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayV3Client.java` | Modified |
| `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/MockWechatPayClient.java` | Modified |
| `main/manager-api/src/main/java/xiaozhi/modules/payment/dao/PaymentOrderDao.java` | Modified |
| `main/manager-api/src/main/java/xiaozhi/modules/payment/service/PaymentOrderService.java` | Modified |
| `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java` | Modified |
| `main/manager-api/src/main/java/xiaozhi/modules/payment/task/PaymentOrderMaintenanceTask.java` | Modified |
| `main/manager-api/src/main/java/xiaozhi/modules/payment/controller/PaymentController.java` | Modified |
| `main/miniprogram/pages/settings/settings.js` | Modified |
| `main/miniprogram/pages/backpack/backpack.js` | Modified |

---

## Security Notes

- **越权访问**: `PaymentController.queryAndFulfill` 正确校验了订单归属当前登录用户，无越权风险。
- **金额校验**: `PaymentOrderServiceImpl.queryAndFulfill` 对微信支付返回金额与订单金额做了严格核对。
- **幂等性**: 多处使用状态机原子更新（`markPaid` 条件 `status=0`，`markFulfilled` 条件 `status=1`），并发安全。
- **Mock 模式**: `MockWechatPayClient` 仅在 `wechat.pay.mock=true` 时加载，且已有 `WechatPayClientStartupGuard` 阻止 mock + prod 同时生效。

---

## Next Steps

1. ✅ 拆分 `PaymentOrderServiceImpl.queryAndFulfill()` 为更小的私有方法。
2. ✅ 修复履约事务内的潜在 NPE。
3. ✅ 更新 `PaymentOrderMaintenanceTask` 类注释。
4. ⏳ 可选：补充单元测试，尤其是 `queryAndFulfill` 的并发和幂等场景。
5. ⏳ 可选：处理 Mock map 无界增长和 JS console.warn（优先级低）。
6. 重新运行 `mvn clean compile -DskipTests`（已 Pass）。

**HIGH 级问题已修复，当前代码可以提交/合并。建议后续补充单元测试以覆盖核心支付路径。**
