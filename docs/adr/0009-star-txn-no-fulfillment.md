# 0009. 星星流水不承载履约状态——推翻 0008 决策 #4 #5

- 状态：已接受（supersedes 0008 之决策 #4、#5）
- 日期：2026-10-09

## 背景

ADR 0008 决策 #4（流水承载履约状态：`fulfill_status` + `fulfill_ref_id`）与 #5（流水存 `pet_prototype`）落地后重新评估：星星流水本质是**资金变动事实表**，而履约状态、履约产物ID、宠物原型是**业务履约语义**——把履约字段塞进钱流水，混淆了两类关注点：

1. **职责越界**：星星罐是钱包，管"多少钱、何时变动"；履约是业务侧（旅行日记/兑换发货/抽奖开奖）的状态机。钱包不该知道"日记ID"是什么。
2. **通用列名不通用**：`fulfill_status`/`fulfill_ref_id`/`pet_prototype` 对 earn 与即时消费永远无意义，列注释要反复解释"仅 travel 必填"——字段的 NULL 率本身就是设计异味。
3. **真实调用方不存在**：旅行功能（故事引擎履约）尚未实现，`consumePendingTravelPreorder` 无真实调用方——为一个未落地的场景预先在钱流水里开三列，是投机性设计。

## 决策

1. **流水表只承载资金变动**：删除 `pet_prototype`、`fulfill_status`、`fulfill_ref_id` 三列及配套索引 `idx_ai_star_txn_pending_travel`。流水保留 `user_id/type/biz_type/ref_id/amount/balance_after/remark` + 审计列。
2. **履约语义由 refId 关联的业务对象承载**：`ref_id` 字段保留不动。未来异步履约场景（旅行预订等）落地时，由该业务自己的实体表承载履约状态/产物/原型，通过 `ref_id`（如 `tv-` 单号）与流水对账关联。届时再建对应实体，不在星星罐表上预留列。
3. **星星模块接口收敛**：`StarService.consume()` 删除 `petPrototype` 参数；删除 `consumePendingTravelPreorder()`；删除 `StarFulfillStatus` 枚举、`StarConsumeBizType.asyncFulfill`、`StarTransactionDao.markFulfilled()`、错误码 `STAR_PROTOTYPE_REQUIRED`。
4. **模块边界**：星星罐 = 钱包（earn/consume/balance/transactions）；履约 = 各业务模块各自管理。

## 否决的替代方案

- **保留通用履约列（维持 0008）**：见"背景"，职责越界 + 无真实调用方。
- **现在就建旅行预订表 `ai_star_travel_preorder`**：旅行功能未落地，表结构会猜错；ref_id 已预留，届时再建表关联即可。

## 后果

- `ai_star_transaction` 表结构简化：9 业务列 + 2 审计列。
- 旅行预订的履约状态当前**无处承载**——故事引擎迭代实现旅行功能时，需新建履约实体（如 `travel_preorder`）并关联流水 ref_id。
- ADR 0008 其余决策（单表流水、强制 refId 幂等、不开 HTTP 写接口、整数星星永不过期）继续有效。
