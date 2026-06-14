# Companion Subscription Items Payment

完整方案已写入 [plans/companion-subscription-items-payment.md](file:///Users/minwang/codes/github/xiaozhi-esp32-server/plans/companion-subscription-items-payment.md)，包含 Context、表设计、模块结构、接口契约、交互流程、安全要点、实施顺序与验证方法。下面是落地纲要：

## Context
完美女友目前仅免费基础聊天，需要新增三个商业化能力：分档位订阅会员、一次性道具购买（含亲密度道具：玫瑰/奶茶/钻戒）、微信支付 V3 JSAPI 通道。三件事统一通过新增 3 个后端模块完成，复用现有 `companion/wechat/voiceclone/security` 基础。

## 设计核心
- **订阅**：扁平 `plan_code` 档位 + 权益 JSON 快照写入 `user_subscription`，避免运营改套餐影响存量。
- **道具**：SKU 表 + 用户库存 + 发放/消耗双流水。订阅可附赠道具（`bonus_items`）。服装类不消耗、其余按次扣减。
- **支付**：统一 `payment_order` + 微信 V3 JSAPI；回调写 `payment_callback_log`、`transaction_id` 去重；`FulfillmentDispatcher` 路由到订阅或道具履约。
- **联动**：`companion` 换职业/换小任性 → 消耗对应券；用户克隆音色 → 校验订阅 feature；`voiceClone/cloneAudio` 前置消耗 `voice_clone_quota`。

## Task 1 — Liquibase 迁移
- 新增 `main/manager-api/src/main/resources/db/changelog/202606101030.sql`，建 8 张表：`subscription_plan / user_subscription / item_sku / user_item / item_grant_log / item_consume_log / payment_order / payment_refund / payment_callback_log`。
- 在 `db.changelog-master.yaml` 末尾追加 changeSet。

## Task 2 — payment 模块（含微信支付 SDK 接入）
- 引入 `wechatpay-java` 依赖；`WechatPayClient/Config/Signer` 三件套。
- `PaymentController` 暴露：下单、查询、列表、取消；`PaymentNotifyController` 处理回调（验签 + 解密 + 去重 + 触发履约）。
- 敏感配置通过 `sys_params` + `AESUtils` 加密入库，启动期解密。

## Task 3 — subscription 模块
- 接口：`/subscription/plans`、`/subscription/me`、`/subscription/entitlements`。
- 服务：`SubscriptionService.requireFeature(...)` 提供给 companion 使用；`SubscriptionFulfillmentService.fulfill/rollback` 由支付回调驱动。
- 种子数据：默认 3 档（青铜/白银/黄金），权益 JSON 配齐。

## Task 4 — item 模块
- 接口：`/item/skus`、`/item/inventory`、`/item/consume-log`。
- 服务：`ItemService.grant/consume`，行锁保证并发安全；`outfit` 类不递减。
- 种子数据：4 类 SKU（券类/服装/克隆额度/亲密度道具）。

## Task 5 — FulfillmentDispatcher
- 在支付回调处统一调度，按 `product_type` 路由；用 `TransactionTemplate` 与订单状态机一起做幂等履约。
- 退款分支：订阅置 3、道具调用 `itemService.adjust`，差异写审计日志。

## Task 6 — 与 companion / voiceclone 联动
- 修改 `CompanionServiceImpl.update`：`occupation/soulQuirk/voice/defaultImage` 变更分别触发券消耗或订阅 feature 校验。
- 修改 `VoiceCloneController.cloneAudio`：调用前 `itemService.consume(userId, "voice_clone_quota", 1, ...)`。

## Task 7 — Shiro 路由与错误码
- `ShiroConfig.filterMap` 在 `/**` 前追加：`/subscription/plans` `anon`、`/payment/notify` `anon`、其余 `oauth2`。
- `ErrorCode.java` 追加 10300~10322 共 11 个错误码。

## Task 8 — 超时关单 / 对账（可放 P2）
- `@Scheduled` 每 5 分钟扫超时未支付订单关单。
- 每日对账（拉取微信对账单 vs 本地订单）写差异表，留给运营。

## 关键文件
- 新增：`main/manager-api/src/main/resources/db/changelog/202606101030.sql`、三模块包 `xiaozhi/modules/{subscription,item,payment}/...` 共约 40+ 个 Java 文件。
- 修改：[ShiroConfig.java](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/security/config/ShiroConfig.java)、[ErrorCode.java](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java)、[CompanionServiceImpl.java](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java)、[VoiceCloneController.java](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/voiceclone/controller/VoiceCloneController.java)、[db.changelog-master.yaml](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml)、`main/manager-api/pom.xml`。

## 验证
1. 启动 `manager-api`：Liquibase 跑通 8 张新表，Swagger 显示新接口。
2. mock 模式：`wechat/login` → `/payment/order` → 调 dev-only `/test/payment/mock-notify` → `/subscription/me` 出现 active 订阅，`/item/inventory` 含赠送道具。
3. 用 mock 库存触发 `/companion/update` 换职业，验证 `user_item.remain_count -1`、`item_consume_log` 新增 1 条；非订阅用户切换克隆音色返回 `10312`。
4. 微信支付沙箱跑一次真实下单 + 回调，验证签名校验、`transaction_id` 去重、订单 0→1→2 状态机。
5. 回归 `companion/setup`、`wechat/login`、`voiceClone/cloneAudio` 既有功能。

## 待用户决策（实施前可再确认）
- 默认 3 档具体定价（如 30 元 / 月、80 元 / 季、288 元 / 年）；
- 默认 SKU 单价与亲密度道具的 `intimacy_delta`（玫瑰=5/奶茶=10/钻戒=100 仅为示例）；
- 是否本期就把 `companion/voiceClone` 改造合并发布，还是先发新模块、灰度后再开启鉴权。
