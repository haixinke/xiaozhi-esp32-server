# 完美女友：订阅 + 道具 + 微信支付 后端技术方案

## Context

完美女友（基于 `companion` 模块）目前只支持免费基础聊天。为满足商业化诉求，需要：

1. **订阅会员**：分档位（青铜/白银/黄金）按周期开通"换装、换职业、换小任性、换声音、声音克隆"等高级能力，到期失效。
2. **道具购买**：一次性消耗品（职业变更券、小任性变更券、服装、声音克隆额度、亲密度道具：玫瑰花/奶茶/挚爱钻戒）。
3. **微信支付 V3 JSAPI**：统一支撑订阅与道具的支付链路，并预留退款、对账、回调等运营能力。

后端需对应增加：套餐定义、道具 SKU、用户订阅、用户道具库存、统一订单、微信支付适配层、回调审计与对账，以及与 `companion` 模块的能力鉴权 / 道具核销联动。本期不动小程序代码，但暴露的接口需要小程序可以直接消费。

> 已确认设计选型：扁平档位字段 + 权益 JSON 快照、SKU+库存+核销流水、微信支付 V3 JSAPI、仅手动续费且支持订阅赠送道具、统一订单表、亲密度逻辑后续补、免费用户仅基础聊天。

---

## 1. 总体架构

新增 3 个一级模块，复用现有 `wechat`、`companion`、`voiceclone`、`security`、`sys` 基础设施：

```
modules/
├── subscription/   # 套餐档位 + 用户订阅 + 权益判定
├── item/           # 道具 SKU + 用户库存 + 核销流水
└── payment/        # 统一订单 + 微信支付V3适配 + 回调 + 退款 + 对账
```

模块依赖：
- `payment` 依赖 `subscription` 和 `item`，支付成功后回调它们做"履约"。
- `companion` 在执行换职业 / 小任性 / 头像 / 声音克隆等敏感动作前调用：
  - `subscriptionService.requireFeature(userId, FeatureCode)` 进行能力鉴权；
  - `itemService.consume(userId, skuCode, count, bizRefId)` 进行一次性道具核销。
- `subscription.fulfill()` 中如果套餐配置了 `bonusItems`，会调用 `item.grant()` 充入用户库存。

---

## 2. 数据库设计（Liquibase 迁移）

### 命名与基线
- 迁移文件名沿用 `YYYYMMDDHHMM.sql`（如 `202606101030.sql`），并在 `db.changelog-master.yaml` 末尾追加。
- 货币单位：所有金额一律存储为分（`BIGINT`，`*_fen` 列名后缀），避免精度问题。
- 时间字段统一 `DATETIME`，UTC 入库、应用层按 Asia/Shanghai 展示，与 `companion` 一致。
- 用户隔离：与已有模块一致，使用 `user_id` 列 + 应用层 `SecurityUser` 校验，不依赖数据库行级权限。

### 2.1 `subscription_plan` 套餐档位
```sql
CREATE TABLE subscription_plan (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  plan_code       VARCHAR(32)  NOT NULL COMMENT '档位编码: bronze/silver/gold',
  plan_name       VARCHAR(64)  NOT NULL COMMENT '档位名称',
  duration_days   INT          NOT NULL COMMENT '周期天数: 30/90/365',
  price_fen       BIGINT       NOT NULL COMMENT '原价(分)',
  promo_price_fen BIGINT       NULL     COMMENT '促销价(分)，为空走原价',
  features        JSON         NOT NULL COMMENT '权益列表 ["outfit","occupation_change","voice_clone","custom_voice"]',
  bonus_items     JSON         NULL     COMMENT '附赠道具 [{"skuCode":"rose","count":10}, ...]',
  description     VARCHAR(500) NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
  sort            INT          NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plan_code (plan_code, duration_days)
) COMMENT='订阅套餐档位';
```

### 2.2 `user_subscription` 用户订阅
```sql
CREATE TABLE user_subscription (
  id                  BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT       NOT NULL,
  plan_id             BIGINT       NOT NULL,
  plan_code           VARCHAR(32)  NOT NULL COMMENT '冗余便于查询',
  order_id            BIGINT       NOT NULL COMMENT '关联payment_order.id',
  features_snapshot   JSON         NOT NULL COMMENT '下单时的权益快照(防止plan改动影响存量)',
  start_at            DATETIME     NOT NULL,
  end_at              DATETIME     NOT NULL,
  status              TINYINT      NOT NULL COMMENT '0未生效 1生效中 2已过期 3已退款',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_status (user_id, status, end_at),
  KEY idx_order (order_id)
) COMMENT='用户订阅记录';
```

> "续费"语义：同一 `user_id` 下若已存在 `status=1` 的订阅，新订阅的 `start_at = 旧end_at`；否则 `start_at = now()`。同一时刻一个用户最多 1 条 `status=1` 记录。

### 2.3 `item_sku` 道具 SKU
```sql
CREATE TABLE item_sku (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  sku_code        VARCHAR(64)  NOT NULL UNIQUE COMMENT 'occupation_change/soul_quirk_change/outfit_xxx/voice_clone_quota/rose/milktea/diamond_ring',
  sku_name        VARCHAR(64)  NOT NULL,
  category        VARCHAR(32)  NOT NULL COMMENT 'consumable_change/outfit/voice_quota/intimacy',
  price_fen       BIGINT       NOT NULL,
  promo_price_fen BIGINT       NULL,
  attributes      JSON         NULL COMMENT '道具属性: {intimacy_delta:5, outfit_image:"..."}',
  icon_url        VARCHAR(256) NULL,
  description     VARCHAR(500) NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  sort            INT          NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP
) COMMENT='道具SKU';
```

### 2.4 `user_item` 用户道具库存（按 SKU 聚合）
```sql
CREATE TABLE user_item (
  id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  sku_code    VARCHAR(64) NOT NULL,
  total_count INT         NOT NULL DEFAULT 0 COMMENT '累计获得',
  used_count  INT         NOT NULL DEFAULT 0 COMMENT '累计消耗',
  remain_count INT        NOT NULL DEFAULT 0 COMMENT '剩余可用',
  updated_at  DATETIME    NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_sku (user_id, sku_code)
) COMMENT='用户道具库存';
```

> 服装道具特殊：`category=outfit` 表示"已拥有该外观"，`remain_count` 仅作权限判定（>0 即可重复换装），不在 `companion` 换装时递减；其余 `consumable_change/voice_quota/intimacy` 均按消耗递减。

### 2.5 `item_grant_log` & `item_consume_log`
```sql
CREATE TABLE item_grant_log (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  sku_code    VARCHAR(64)  NOT NULL,
  count       INT          NOT NULL,
  source      VARCHAR(32)  NOT NULL COMMENT 'purchase/subscription_bonus/admin_grant',
  source_ref  VARCHAR(64)  NULL     COMMENT '关联订单号或运营记录',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id, created_at)
) COMMENT='道具发放流水';

CREATE TABLE item_consume_log (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  sku_code    VARCHAR(64)  NOT NULL,
  count       INT          NOT NULL,
  biz_type    VARCHAR(32)  NOT NULL COMMENT 'occupation_change/soul_quirk_change/outfit_equip/voice_clone/intimacy_gift',
  biz_ref_id  VARCHAR(64)  NULL     COMMENT 'companion_id/voice_clone_id 等',
  remark      VARCHAR(200) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id, created_at),
  KEY idx_biz (biz_type, biz_ref_id)
) COMMENT='道具消耗流水';
```

### 2.6 `payment_order` 统一订单
```sql
CREATE TABLE payment_order (
  id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
  out_trade_no      VARCHAR(40)  NOT NULL UNIQUE COMMENT '商户订单号(雪花/UUID)',
  user_id           BIGINT       NOT NULL,
  product_type      VARCHAR(16)  NOT NULL COMMENT 'SUBSCRIPTION/ITEM',
  product_ref_id    BIGINT       NOT NULL COMMENT 'plan_id 或 sku_id',
  product_snapshot  JSON         NOT NULL COMMENT '下单时的产品快照(plan 或 sku)',
  quantity          INT          NOT NULL DEFAULT 1,
  amount_fen        BIGINT       NOT NULL,
  pay_channel       VARCHAR(16)  NOT NULL DEFAULT 'WECHAT_JSAPI',
  status            TINYINT      NOT NULL COMMENT '0待支付 1已支付 2已发货 3已取消 4已退款 5已超时',
  prepay_id         VARCHAR(64)  NULL     COMMENT '微信预支付ID',
  transaction_id    VARCHAR(40)  NULL     COMMENT '微信支付订单号',
  paid_at           DATETIME     NULL,
  fulfilled_at      DATETIME     NULL,
  expire_at         DATETIME     NOT NULL COMMENT '下单后15分钟未支付自动关单',
  refund_amount_fen BIGINT       NOT NULL DEFAULT 0,
  client_ip         VARCHAR(64)  NULL,
  fail_reason       VARCHAR(500) NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_status (user_id, status, created_at),
  KEY idx_transaction (transaction_id)
) COMMENT='统一支付订单';
```

### 2.7 `payment_refund` 退款记录
```sql
CREATE TABLE payment_refund (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  out_refund_no   VARCHAR(40)  NOT NULL UNIQUE,
  order_id        BIGINT       NOT NULL,
  refund_fen      BIGINT       NOT NULL,
  reason          VARCHAR(200) NULL,
  status          TINYINT      NOT NULL COMMENT '0处理中 1成功 2失败',
  refund_id       VARCHAR(64)  NULL COMMENT '微信退款单号',
  refunded_at     DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order (order_id)
) COMMENT='退款记录';
```

### 2.8 `payment_callback_log` 回调审计
```sql
CREATE TABLE payment_callback_log (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  channel         VARCHAR(16)  NOT NULL,
  out_trade_no    VARCHAR(40)  NULL,
  transaction_id  VARCHAR(40)  NULL,
  raw_headers     TEXT         NULL,
  raw_body        MEDIUMTEXT   NOT NULL,
  signature_valid TINYINT      NOT NULL,
  process_result  VARCHAR(32)  NOT NULL COMMENT 'SUCCESS/DUPLICATE/SIGN_FAIL/PROCESS_FAIL',
  remark          VARCHAR(500) NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_trans (transaction_id),
  KEY idx_out_trade (out_trade_no)
) COMMENT='支付回调原始日志(审计/排障/对账)';
```

### 2.9 `sys_params` 配置项追加
```
wechat.miniprogram.appid          # 已存在
wechat.miniprogram.secret         # 已存在
wechat.pay.mock                   # 是否走 mock，生产必须 false（boolean）
wechat.pay.mchid                  # 商户号
wechat.pay.serial_no              # 商户证书序列号
wechat.pay.private_key            # 商户私钥 PEM（AESUtils 加密入库）
wechat.pay.api_v3_key             # APIv3 密钥（AESUtils 加密入库）
wechat.pay.notify_url             # 回调 URL（HTTPS 公网可达）
```

> 私钥与 APIv3 密钥使用 `AESUtils.encrypt(server.secret, plain)` 加密后写入 `sys_params`，运行时由 `WechatPayProperties.loadReal` 解密。`payment` 模块启动时由 `WechatPayV3Client` 构造单例（基于 wechatpay-java 的 `RSAAutoCertificateConfig`，平台证书自动下载、滚动更新，无需人工维护证书文件）。

#### 上线前 6 项配置写入操作单

| 步骤 | 操作 |
|---|---|
| 1 | 商户平台「账户中心 → API 安全」获取 `mchid`、`serial_no`、下载 API 证书包（含 `apiclient_key.pem`），并设置 32 字节 APIv3 密钥。 |
| 2 | 用 `AESUtils.encrypt(serverSecret, pemPlain)` 加密 `apiclient_key.pem` 全文 → 写入 `wechat.pay.private_key`。 |
| 3 | 用 `AESUtils.encrypt(serverSecret, apiV3Plain)` 加密 APIv3 密钥 → 写入 `wechat.pay.api_v3_key`。 |
| 4 | `mchid` / `serial_no` / `notify_url`（HTTPS 公网地址）按明文写入对应参数。 |
| 5 | 商户平台「APIv3 → 回调通知」配置同一个 `notify_url`；「产品中心 → 开发配置 → 支付配置」绑定小程序 `appid` 并开通 JSAPI。 |
| 6 | 将 `wechat.pay.mock` 置为 `false`；用 `WECHAT_PAY_MOCK=false` 环境变量或 `application-prod.yml` 覆盖应用层配置；重启服务，启动日志应有 `WechatPayV3Client initialized, mchid=...`。 |

> `serverSecret` 取自 `sys_params.server.secret`，由 `SysParamsServiceImpl.initServerSecret()` 在首次启动时自动生成。运维需用同一个值加解密，否则启动期 `WechatPayProperties.loadReal` 会抛 `配置项解密失败` 终止启动。

---

## 3. 后端模块结构

### 3.1 包结构
```
xiaozhi/modules/subscription/
├── controller/SubscriptionController.java
├── dao/{SubscriptionPlanDao, UserSubscriptionDao}.java
├── dto/{PlanQueryDTO, SubscribeOrderDTO}.java
├── entity/{SubscriptionPlanEntity, UserSubscriptionEntity}.java
├── enums/{SubscriptionStatus, FeatureCode}.java
├── service/{SubscriptionService, SubscriptionFulfillmentService}.java
├── service/impl/...
└── vo/{SubscriptionPlanVO, UserSubscriptionVO, EntitlementVO}.java

xiaozhi/modules/item/
├── controller/ItemController.java
├── dao/{ItemSkuDao, UserItemDao, ItemGrantLogDao, ItemConsumeLogDao}.java
├── dto/{ItemPurchaseDTO, ItemConsumeDTO, ItemGrantDTO}.java
├── entity/...
├── enums/{ItemCategory, ConsumeBizType}.java
├── service/{ItemService, ItemFulfillmentService}.java
├── service/impl/...
└── vo/{ItemSkuVO, UserItemVO}.java

xiaozhi/modules/payment/
├── controller/{PaymentController, PaymentNotifyController}.java
├── dao/{PaymentOrderDao, PaymentRefundDao, PaymentCallbackLogDao}.java
├── dto/{CreateOrderDTO, OrderQueryDTO, RefundDTO}.java
├── entity/...
├── enums/{OrderStatus, ProductType, PayChannel}.java
├── service/{PaymentOrderService, FulfillmentDispatcher, RefundService}.java
├── service/impl/...
├── wechat/{WechatPayClient, WechatPayConfig, WechatPaySigner}.java
└── vo/{PrepayVO, OrderVO}.java
```

### 3.2 关键服务契约
```java
// subscription
public interface SubscriptionService {
    List<SubscriptionPlanVO> listActivePlans();
    UserSubscriptionVO getActiveSubscription(Long userId);
    EntitlementVO getEntitlements(Long userId);              // 用于小程序前置校验
    void requireFeature(Long userId, FeatureCode code);      // companion模块鉴权调用
}

public interface SubscriptionFulfillmentService {
    /** 支付成功回调中调用：创建user_subscription + 发放bonus_items */
    void fulfill(PaymentOrderEntity order);
    void rollback(PaymentOrderEntity order);                 // 退款时
}

// item
public interface ItemService {
    List<ItemSkuVO> listSkus(String category);
    List<UserItemVO> myInventory(Long userId);
    void grant(Long userId, String skuCode, int count, String source, String sourceRef);
    void consume(Long userId, String skuCode, int count, ConsumeBizType bizType, String bizRefId);
}

// payment
public interface PaymentOrderService {
    PrepayVO createOrder(Long userId, CreateOrderDTO dto, String clientIp);
    OrderVO query(Long userId, String outTradeNo);
    PageData<OrderVO> page(Long userId, Map<String, Object> params);
    void cancelExpired();                                    // 定时任务
}

public interface FulfillmentDispatcher {
    /** 支付成功后按 product_type 路由到 SubscriptionFulfillment 或 ItemFulfillment */
    void dispatch(PaymentOrderEntity order);
}
```

### 3.3 与 `companion` 的联动改造
在 `CompanionServiceImpl.update(...)` 中：
- 修改 `occupation` → 调 `itemService.consume(userId, "occupation_change", 1, OCCUPATION_CHANGE, deviceId)`，库存不足直接抛 `ITEM_INSUFFICIENT`。
- 修改 `soulQuirk` → 同理消耗 `soul_quirk_change`。
- 修改 `voice` 且新值不在系统默认音色集合（即用户克隆音色） → `subscriptionService.requireFeature(userId, CUSTOM_VOICE)`。
- 修改 `defaultImage`（换装）→ 校验当前用户拥有该 `outfit_xxx` SKU。

`VoiceCloneController.cloneAudio(...)` 之前增加：
```java
itemService.consume(userId, "voice_clone_quota", 1, VOICE_CLONE, cloneId);
```

> 这部分在落地时再做最小改动，不在本期方案修改 `companion` 既有逻辑的前提下，新接口位先行就绪。

---

## 4. 接口设计（小程序面向）

> 路由前缀沿用全局 `/xiaozhi/...`。所有接口除支付回调外，统一走 `oauth2` 过滤器（依赖 `wechat/login` 拿到的 token）。

### 4.1 订阅 `/subscription`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/subscription/plans` | 列出可购买的档位（公开，含权益、价格、附赠道具） |
| GET | `/subscription/me` | 当前用户订阅状态 + 剩余天数 |
| GET | `/subscription/entitlements` | 当前用户拥有的 feature codes（用于小程序前置 UI 灰度） |

### 4.2 道具 `/item`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/item/skus?category=` | 列出 SKU |
| GET | `/item/inventory` | 当前用户库存 |
| GET | `/item/consume-log` | 当前用户消耗流水（分页） |

### 4.3 支付 `/payment`
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/payment/order` | 统一下单（body: productType, productRefId, quantity）→ 返回 prepayParams 给小程序拉起 wx.requestPayment |
| GET | `/payment/order/{outTradeNo}` | 查询订单（强一致：先查微信再回写本地） |
| GET | `/payment/orders` | 我的订单分页 |
| POST | `/payment/order/{outTradeNo}/cancel` | 用户取消未支付订单 |
| POST | `/payment/notify` | **微信回调**（path 走 `anon`，签名校验在拦截器内） |
| POST | `/payment/refund` | （Web 后台）发起退款 |

### 4.4 下单 DTO
```json
POST /payment/order
{
  "productType": "SUBSCRIPTION",          // 或 "ITEM"
  "productRefId": 1003,                   // plan_id 或 sku_id
  "quantity": 1                            // ITEM 时可>1，SUBSCRIPTION 强制1
}
```
返回：
```json
{
  "outTradeNo": "PG202606101030xxxx",
  "amountFen": 9900,
  "prepayParams": {                       // 直接喂给 wx.requestPayment
    "timeStamp": "...", "nonceStr": "...",
    "package": "prepay_id=...",
    "signType": "RSA",
    "paySign": "..."
  }
}
```

### 4.5 微信回调
- URL `/xiaozhi/payment/notify`，配置在 `ShiroConfig.filterMap` 为 `anon`。
- 进入 `WechatPayNotifyHandler`：
  1. 读取 `Wechatpay-Signature/Timestamp/Nonce/Serial` Header；
  2. 用平台证书验签（失败则记 `payment_callback_log.signature_valid=0` 并返回 401）；
  3. 用 APIv3 key + AES-GCM 解密 `resource`；
  4. 通过 `transaction_id` 去重（已处理过则返回 200 `SUCCESS`）；
  5. 加锁更新 `payment_order.status` 0→1，写 `paid_at`、`transaction_id`；
  6. 异步 / 同步调用 `FulfillmentDispatcher.dispatch(order)`；
  7. 返回 `{"code":"SUCCESS"}`。

### 4.6 错误码（追加到 `ErrorCode.java`）
```
10300 PAY_CHANNEL_NOT_AVAILABLE
10301 PAY_ORDER_NOT_FOUND
10302 PAY_ORDER_AMOUNT_MISMATCH
10303 PAY_ORDER_DUPLICATE
10304 PAY_SIGN_INVALID
10305 PAY_REFUND_FAILED
10310 SUBSCRIPTION_PLAN_NOT_FOUND
10311 SUBSCRIPTION_NOT_ACTIVE
10312 SUBSCRIPTION_FEATURE_DENIED
10320 ITEM_SKU_NOT_FOUND
10321 ITEM_INSUFFICIENT
10322 ITEM_CONSUME_FAILED
```

---

## 5. 关键交互流程

### 5.1 订阅下单 → 支付 → 履约
```
[小程序] /payment/order  productType=SUBSCRIPTION, productRefId=goldPlanId
  └─> PaymentOrderService.createOrder
        ├─ 校验 plan 存在 & 上架；快照 plan 到 product_snapshot
        ├─ 计算 amount_fen（promo_price 优先），下发 amount 不可被前端覆盖
        ├─ INSERT payment_order(status=0, expire_at=now()+15min)
        ├─ WechatPayClient.jsapiPrepay(orderNo, amountFen, openid, description)
        ├─ 写回 prepay_id
        └─ 返回 prepayParams 给小程序

[小程序] wx.requestPayment(prepayParams)
[微信]   推送回调 -> /payment/notify
  └─> WechatPayNotifyHandler
        ├─ 验签/解密/去重
        ├─ UPDATE payment_order SET status=1, paid_at=now(), transaction_id=...
        ├─ FulfillmentDispatcher.dispatch(order)
        │     └─ SubscriptionFulfillmentService.fulfill(order)
        │            ├─ 计算 start_at=max(now, 旧end_at)、end_at=start+duration_days
        │            ├─ INSERT user_subscription(status=1, features_snapshot)
        │            ├─ 旧订阅(若有)保持 status=1 直至 end_at（拼接续期）
        │            └─ 遍历 bonus_items: itemService.grant(...)
        ├─ UPDATE payment_order SET status=2(已发货), fulfilled_at=now()
        └─ 返回 SUCCESS
```

### 5.2 道具购买
与 5.1 类似，仅 `dispatch` 走 `ItemFulfillmentService`：
```
ItemFulfillmentService.fulfill(order):
  └─ itemService.grant(userId, sku_code, quantity, "purchase", out_trade_no)
        └─ INSERT/UPDATE user_item(remain_count += quantity)
        └─ INSERT item_grant_log
```

### 5.3 道具消耗（如换职业）
```
[小程序] /companion/update {occupation:"music"}
  └─ CompanionServiceImpl.update
        ├─ 校验 currentUser owns companion
        ├─ 检测到 occupation 变化
        ├─ itemService.consume(userId, "occupation_change", 1, OCCUPATION_CHANGE, deviceId)
        │     ├─ SELECT user_item FOR UPDATE
        │     ├─ if remain<1 -> ITEM_INSUFFICIENT
        │     ├─ UPDATE user_item SET remain_count -= 1, used_count += 1
        │     └─ INSERT item_consume_log
        ├─ entity.setOccupation(...)
        └─ companionDao.updateById
```

### 5.4 能力鉴权（如换装/换克隆音色）
```
[小程序] /companion/update {voice:"clone_xxx"}
  └─ CompanionServiceImpl.update
        ├─ 若 voice 是用户克隆音色:
        │     subscriptionService.requireFeature(userId, CUSTOM_VOICE)
        │        └─ 查 active subscription 的 features_snapshot 是否包含 "custom_voice"
        │        └─ 不包含 -> SUBSCRIPTION_FEATURE_DENIED
        └─ 通过则更新
```

### 5.5 退款
- 后台触发 `POST /payment/refund` → `WechatPayClient.refund(...)` → 写 `payment_refund(0)`
- 微信退款回调 → `payment_refund.status=1` →
  - `subscription`：将对应 `user_subscription.status` 置为 3（已退款），并按时长扣减/作废后续订阅；
  - `item`：调 `itemService.adjust(...)` 视库存还有多少，扣减剩余库存（不足则记审计 `ITEM_OVERCONSUMED`，由人工处理）。

### 5.6 超时关单（定时）
- 使用现有 `@Async` 或 Spring `@Scheduled`：每 5 分钟扫描 `payment_order` `status=0 AND expire_at<now()` 置为 5（已超时）；同时调 `WechatPayClient.closeOrder(...)` 关闭微信侧。

### 5.7 对账（每日）
- 拉取 `T-1` 日微信支付对账单与本地 `payment_order(status IN (1,2,4))` 进行差异比对，差异写入 `payment_recon_diff` 审计表（可放到 P2 阶段）。

---

## 6. 安全 / 一致性要点

1. **金额防篡改**：`createOrder` 时金额来自 `subscription_plan` / `item_sku` DB 查询，**绝不**从前端传入。
2. **下单幂等**：同一用户同一 `productRefId` 5 秒内不允许重复下单（Redis 限频 `pay:lock:{userId}:{productType}:{refId}`）。
3. **回调幂等**：`transaction_id` 唯一索引 + `payment_order.status` 状态机 + 行锁；履约方法本身亦幂等（`user_subscription` 用 `order_id` UNIQUE 防重；`item_grant_log` 用 `(out_trade_no, sku_code)` 防重）。
4. **签名/证书**：使用微信支付官方 SDK `wechatpay-apache-httpclient` 或 `wechatpay-java`（建议后者，原生支持 V3）。平台证书自动下载、滚动更新。
5. **私钥安全**：`apiclient_key.pem` 与 `api_v3_key` 写 `sys_params` 时用 `AESUtils` 加密；`PaymentController` 路由不下放任何敏感字段。
6. **Shiro 配置**（`ShiroConfig.filterMap`，按顺序追加，**注意必须放在 `/**` 之前**）：
   ```java
   filterMap.put("/subscription/plans", "anon");
   filterMap.put("/subscription/me", "oauth2");
   filterMap.put("/subscription/entitlements", "oauth2");
   filterMap.put("/item/skus", "anon");
   filterMap.put("/item/inventory", "oauth2");
   filterMap.put("/item/consume-log", "oauth2");
   filterMap.put("/payment/notify", "anon");      // 验签在 handler 内做
   filterMap.put("/payment/**", "oauth2");
   ```
7. **事务边界**：
   - `createOrder`：本地事务 + 调用微信 `prepay`（外部 IO 放事务后，失败则订单标 0 直接超时关）。
   - 回调履约：使用 `TransactionTemplate`（参考 `CompanionServiceImpl.setup`），订单状态 + 履约写库放在同一事务；外部调用（如下游消息）放事务提交后。
8. **数据隔离**：所有 `controller` 强制使用 `SecurityUser.getUserId()`，严禁信任请求体里的 `userId`。
9. **限流**：`/payment/order` 单用户 60 秒最多 5 次；`/payment/notify` 不限流但 5xx 时返回非 SUCCESS 让微信重试。

---

## 7. 关键文件清单

### 新增（迁移 + 模块代码）
```
main/manager-api/src/main/resources/db/changelog/
└── 202606101030.sql                              # 8张新表
main/manager-api/src/main/resources/db/changelog/
└── db.changelog-master.yaml                      # 追加 changeSet 引用

main/manager-api/src/main/java/xiaozhi/modules/subscription/...   # ~12个文件
main/manager-api/src/main/java/xiaozhi/modules/item/...           # ~14个文件
main/manager-api/src/main/java/xiaozhi/modules/payment/...        # ~18个文件
```

### 修改
```
main/manager-api/src/main/java/xiaozhi/modules/security/config/ShiroConfig.java
main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java
main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java
main/manager-api/src/main/java/xiaozhi/modules/voiceclone/controller/VoiceCloneController.java
main/manager-api/pom.xml                          # 引入 wechatpay-java SDK
```

### 配置
```
main/manager-api/src/main/resources/application.yml
  └─ 仅占位，敏感值通过 sys_params 表 + AESUtils 注入
```

---

## 8. 实施顺序建议

1. **Task 1 — Liquibase 迁移**：8 张新表 + master.yaml 追加；启动通过。
2. **Task 2 — payment 模块骨架 + 微信支付 SDK 接入**：`WechatPayClient` 抽象 + 占位实现 + 单元测试；下单接口可通（mock 模式）。
3. **Task 3 — subscription 模块**：plan 数据初始化（默认 3 档位）+ 列表 / 我的订阅 / 权益 接口。
4. **Task 4 — item 模块**：SKU 数据初始化 + 库存 + 消耗流水接口。
5. **Task 5 — 履约调度**：`FulfillmentDispatcher` 以及订阅 / 道具的 fulfill / rollback 实现。
6. **Task 6 — 微信回调实路接入**：替换 mock，用真实证书在沙箱跑通；写好回调审计与去重。
7. **Task 7 — 与 companion / voiceclone 联动**：在换职业 / 换小任性 / 声音克隆处加消耗与鉴权。
8. **Task 8 — 退款 / 超时关单 / 对账（P2）**：定时任务上线，运营后台暴露退款入口。
9. **Task 9 — 错误码 / Shiro 路由 / 文档（Swagger）**。

---

## 9. 验证方法

1. **启动验证**：`mvn -pl main/manager-api spring-boot:run`，Liquibase 跑通 8 张新表；Swagger 在 `http://localhost:8002/xiaozhi/doc.html` 可见 `/subscription /item /payment` 三组接口。
2. **冒烟（mock 支付）**：
   - 用 `wechat/login` 拿 token；
   - `GET /subscription/plans` 返回种子的 3 档；
   - `POST /payment/order` 下单订阅；
   - 调用本地 `/test/payment/mock-notify`（仅 dev profile）模拟回调；
   - `GET /subscription/me` 出现 active 订阅；
   - `GET /item/inventory` 含 bonus_items；
   - 修改 companion 中 `occupation` 触发消耗 → `inventory.remain_count` 减 1，`item_consume_log` 多一条。
3. **微信沙箱**：在微信支付商户平台配置沙箱商户号，跑一次真实下单 → JS 调起 `wx.requestPayment` → 收到回调 → 履约成功。
4. **异常用例**：
   - 余额不足换职业 → `10321 ITEM_INSUFFICIENT`；
   - 非订阅用户用克隆音色 → `10312 SUBSCRIPTION_FEATURE_DENIED`；
   - 重复推送回调 → 第二次 `process_result=DUPLICATE`，订单状态不变；
   - 篡改前端金额 → 服务端忽略，最终金额仍以 plan/sku 为准；
   - 订单 15 分钟未支付 → 定时任务置 `status=5`。
5. **回归**：现有 `companion/setup`、`wechat/login`、`voiceClone/cloneAudio` 单元/接口测试不受影响。

---

## 10. 待补充（建议二期）

- **自动续费 / 签约扣款**：本期不做。
- **促销活动 / 优惠券模块**：可在 `payment_order` 增 `coupon_id` 字段后再扩展。
- **亲密度增长引擎**：玫瑰/奶茶/钻戒道具的 `attributes.intimacy_delta` 配置已就位，本期仅写消耗流水，由后续逻辑读取并更新 `ai_companion.intimacy`。
- **多端支付**：现仅支持 JSAPI，未来若上 H5 / APP 渠道，`pay_channel` 已预留。
- **运营后台**：套餐 / SKU 编辑、订单 / 退款查询，可复用 `manager-web` 现有壳子分阶段补。
