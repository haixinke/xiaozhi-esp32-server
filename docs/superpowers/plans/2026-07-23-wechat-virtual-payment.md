# WeChat Virtual Payment Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the girlfriend Mini Program's JSAPI virtual-goods checkout with WeChat Mini Program Virtual Payment while preserving the existing order, fulfillment, reconciliation, refund, and audit capabilities.

**Architecture:** Keep `ai_payment_order` and `FulfillmentDispatcherImpl` as the business source of truth. Add an isolated XPay adapter for HMAC signing and WeChat server APIs, a dedicated virtual-payment callback handler, encrypted session-key access, and one shared Mini Program payment utility. Treat WeChat callbacks and active order queries as authoritative; the client callback only starts reconciliation.

**Tech Stack:** WeChat Mini Program JavaScript, Spring Boot 3.4.3, Java 21, MyBatis-Plus, MySQL/Liquibase, Redis, JUnit 5, Mockito, Node.js `assert`.

## Global Constraints

- Follow [the approved design spec](../specs/2026-07-23-wechat-virtual-payment-design.md).
- Use TDD: add one failing test, run it to confirm the expected failure, implement the minimum behavior, then rerun.
- Do not edit existing Liquibase SQL files; create `202607231600.sql` and append one changeSet to `db.changelog-master.yaml`.
- Do not log AppKey, AppSecret, session_key, access_token, full openid, callback signature, or unredacted complaint content.
- Do not use `wx.requestPayment` as a production fallback for virtual goods.
- Keep `WECHAT_JSAPI` historical records readable and refundable through their original channel.
- Keep `main/miniprogram/utils/request.js` changes owned by the current working tree intact; adapt to its current exported API instead of reverting it.
- Every commit below is optional during local iteration but, when committed, must use the exact Conventional Commit subject shown.

---

## Task 1: Add schema and entity support for virtual products and XPay identifiers

**Files:**

- Create: `main/manager-api/src/main/resources/db/changelog/202607231600.sql`
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/subscription/entity/SubscriptionPlanEntity.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/item/entity/ItemSkuEntity.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/entity/PaymentOrderEntity.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/entity/PaymentRefundEntity.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/entity/PaymentCallbackLogEntity.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/enums/PayChannel.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/entity/VirtualPaymentSchemaContractTest.java`

- [ ] Write `VirtualPaymentSchemaContractTest` to read the migration resource and assert that it adds:
  - `virtual_product_id` to both product tables.
  - `virtual_env`, `virtual_attach`, `wx_order_id`, `signed_payload_hash` to `ai_payment_order`.
  - `transaction_id` widened to 64 characters for XPay identifiers.
  - `refund_wx_order_id`, `pay_wx_order_id`, `left_fee_snapshot`, `channel_response_hash` to `ai_payment_refund`.
  - `event_type`, `event_id`, `raw_body_hash` and a callback idempotency unique index.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=VirtualPaymentSchemaContractTest test
```

Expected: test fails because the migration does not exist.

- [ ] Create `202607231600.sql` with the exact additive columns and indexes approved in the spec. Make callback `event_id` nullable so legacy V3 callback rows remain valid.
- [ ] Append changeSet ID `202607231600`, author `codex`, and the new SQL resource to `db.changelog-master.yaml`.
- [ ] Add matching camel-case fields to all five entities.
- [ ] Add `WECHAT_VIRTUAL` to `PayChannel`; retain `WECHAT_JSAPI` and `MOCK`.
- [ ] Rerun the focused test and compile:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=VirtualPaymentSchemaContractTest test
mvn -DskipTests compile
```

Expected: both commands pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/resources/db/changelog/202607231600.sql main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml main/manager-api/src/main/java/xiaozhi/modules/subscription/entity/SubscriptionPlanEntity.java main/manager-api/src/main/java/xiaozhi/modules/item/entity/ItemSkuEntity.java main/manager-api/src/main/java/xiaozhi/modules/payment/entity main/manager-api/src/main/java/xiaozhi/modules/payment/enums/PayChannel.java main/manager-api/src/test/java/xiaozhi/modules/payment/entity/VirtualPaymentSchemaContractTest.java
git commit -m "feat: add virtual payment schema"
```

## Task 2: Encrypt session_key and expose a narrow signing-key service

**Files:**

- Create: `main/manager-api/src/main/java/xiaozhi/modules/wechat/security/WechatSessionKeyCipher.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/WechatSessionKeyService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/impl/WechatSessionKeyServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/impl/WechatServiceImpl.java`
- Modify: `main/manager-api/src/main/resources/application-dev.yml`
- Modify: `main/manager-api/src/main/resources/application-prod.yml`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/wechat/security/WechatSessionKeyCipherTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/wechat/service/impl/WechatSessionKeyServiceImplTest.java`

- [ ] Write cipher tests covering:
  - AES-256-GCM round trip.
  - Output prefix `v1:`.
  - Different nonce/ciphertext for repeated encryption of the same value.
  - Tamper detection.
  - Rejection of a key that is not Base64-encoded 32 bytes.
- [ ] Write service tests covering:
  - Decrypting `v1:` data for the requested user.
  - Rejecting a missing session key.
  - Rejecting legacy plaintext and returning a domain error that the client maps to re-login.
  - Never returning another user's session key.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=WechatSessionKeyCipherTest,WechatSessionKeyServiceImplTest test
```

Expected: compilation fails because the new types do not exist.

- [ ] Implement `WechatSessionKeyCipher` with `AES/GCM/NoPadding`, a fresh 12-byte nonce, a 128-bit tag, and `v1:<base64(nonce+ciphertext+tag)>`.
- [ ] Implement `WechatSessionKeyService` with only:

```java
String requireSessionKey(Long userId);
```

The implementation reads `ai_wechat_user`, verifies the `v1:` format, decrypts in memory, and never logs the value.

- [ ] Change `WechatServiceImpl.login` so `code2Session.session_key` is encrypted before insert/update.
- [ ] Bind `wechat.virtual-pay.session-key-encryption-key` to `WECHAT_SESSION_KEY_ENCRYPTION_KEY` in dev and prod configuration.
- [ ] Rerun the focused tests and the existing AES utility regression test:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=WechatSessionKeyCipherTest,WechatSessionKeyServiceImplTest,AESUtilsTest test
```

Expected: tests pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/wechat main/manager-api/src/main/resources/application-dev.yml main/manager-api/src/main/resources/application-prod.yml main/manager-api/src/test/java/xiaozhi/modules/wechat
git commit -m "feat: protect WeChat session keys"
```

## Task 3: Implement virtual-payment configuration and deterministic HMAC signing

**Files:**

- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/VirtualPayProperties.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/VirtualPaySigner.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/VirtualPayJson.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/VirtualPayStartupGuard.java`
- Modify: `main/manager-api/src/main/resources/application.yml`
- Modify: `main/manager-api/src/main/resources/application-dev.yml`
- Modify: `main/manager-api/src/main/resources/application-prod.yml`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/virtual/VirtualPaySignerTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/virtual/VirtualPayStartupGuardTest.java`

- [ ] Write signing tests from the official algorithm:

```text
paySig = hex_lower(HMAC-SHA256(appKey, uri + "&" + exactBody))
signature = hex_lower(HMAC-SHA256(sessionKey, exactBody))
```

Cover `requestVirtualPayment`, `/xpay/query_order`, Unicode UTF-8, `env=0` selecting the production key, `env=1` selecting the sandbox key, and JSON byte stability.

- [ ] Write startup-guard tests proving:
  - Disabled virtual payment accepts blank credentials.
  - Enabled sandbox requires OfferID, AppID, AppSecret, sandbox AppKey, notify URL, and session-key encryption key.
  - Enabled production requires the production AppKey.
  - New-order enablement is rejected unless the integration itself is enabled.
  - Production Spring profile rejects `env=1`.
  - Secrets are not included in exception text.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=VirtualPaySignerTest,VirtualPayStartupGuardTest test
```

Expected: compilation fails because the new types do not exist.

- [ ] Implement immutable configuration binding under `wechat.virtual-pay`.
- [ ] Implement one canonical JSON serializer. It must serialize once to a compact UTF-8 string and return that same string for signing and transport.
- [ ] Implement lowercase hex HMAC-SHA256 without adding a new crypto dependency.
- [ ] Add all environment-variable bindings listed in the spec. Defaults:
  - `enabled=false`
  - `new-order-enabled=false`
  - `env=1` outside production
  - `ios-enabled=false`
  - `notify-format=JSON`
  - connect/read timeout `3000/5000` ms
- [ ] Implement the startup guard and ensure its error messages name missing variable identifiers without printing values.
- [ ] Rerun focused tests:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=VirtualPaySignerTest,VirtualPayStartupGuardTest test
```

Expected: tests pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/payment/virtual main/manager-api/src/main/resources/application.yml main/manager-api/src/main/resources/application-dev.yml main/manager-api/src/main/resources/application-prod.yml main/manager-api/src/test/java/xiaozhi/modules/payment/virtual
git commit -m "feat: add virtual payment signing"
```

## Task 4: Add access-token caching and the XPay server client

**Files:**

- Create: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/WechatAccessTokenService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/impl/WechatAccessTokenServiceImpl.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/WechatVirtualPayClient.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/WechatVirtualPayHttpClient.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/model/VirtualOrderResult.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/model/VirtualRefundRequest.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/model/VirtualRefundResult.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/wechat/service/impl/WechatAccessTokenServiceImplTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/virtual/WechatVirtualPayHttpClientTest.java`

- [ ] Write access-token tests for Redis hit, refresh lock, `expires_in - 300` TTL, minimum 60-second TTL, one retry after token invalidation, and redacted failures.
- [ ] Use a local mock HTTP server in client tests. Assert exact method, URI, Query parameters, JSON request bytes, and `pay_sig` for:
  - `/xpay/query_order`
  - `/xpay/refund_order`
  - `/xpay/notify_provide_goods`
- [ ] Assert response mapping for query statuses `2`, `4`, `5`, `6`, `7`, and `8`.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=WechatAccessTokenServiceImplTest,WechatVirtualPayHttpClientTest test
```

Expected: compilation fails because the client types do not exist.

- [ ] Implement `WechatAccessTokenService` using Redis Key `wechat:access-token:{appid}` and a short distributed refresh lock.
- [ ] Implement `WechatVirtualPayClient` with:

```java
VirtualOrderResult queryOrder(String openid, String outTradeNo);
VirtualRefundResult refund(VirtualRefundRequest request);
void notifyProvideGoods(String outTradeNo, String wxOrderId);
```

- [ ] For each request, serialize the Body once and sign those exact bytes. Never log the signed Body when it contains openid.
- [ ] Before completing the refund implementation, run a real sandbox contract probe with a non-production test user:
  - First send the officially documented `access_token + pay_sig`.
  - If WeChat returns a missing user-signature error, add `signature=HMAC-SHA256(session_key, exactBody)` as required by the current endpoint.
  - Record only the redacted request shape, HTTP status, `errcode`, and `errmsg` in the implementation PR.
  - Encode the observed contract in `WechatVirtualPayHttpClientTest`.
- [ ] Map WeChat failures to domain errors without leaking Query parameters.
- [ ] Rerun the focused tests:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=WechatAccessTokenServiceImplTest,WechatVirtualPayHttpClientTest test
```

Expected: tests pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/wechat/service main/manager-api/src/main/java/xiaozhi/modules/payment/virtual main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java main/manager-api/src/test/java/xiaozhi/modules/wechat main/manager-api/src/test/java/xiaozhi/modules/payment/virtual
git commit -m "feat: add WeChat XPay client"
```

## Task 5: Return signed virtual-payment parameters from order creation

**Files:**

- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/vo/VirtualPayParamsVO.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/vo/PrepayVO.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/PaymentOrderService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/controller/PaymentController.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/dao/PaymentOrderDao.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImplVirtualPayTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/controller/PaymentControllerVirtualPayTest.java`

- [ ] Write service tests covering:
  - Subscription normal price: no `activitySellingPrice`.
  - Subscription promo price.
  - Subscription upgrade credit with `goodsPrice=price_fen` and `activitySellingPrice=amount_fen`.
  - Item quantity and promotional unit price.
  - Rejection of missing/unpublished `virtual_product_id`.
  - Rejection of invalid price or amount overflow.
  - `signData` includes an opaque `attach` and no user ID.
  - `pay_channel=WECHAT_VIRTUAL`, `prepay_id=NULL`.
  - Missing/legacy session_key returns the re-login domain error without creating reusable signed output.
- [ ] Write controller serialization tests proving `signData` is a JSON string, while `mode`, `paySig`, and `signature` are sibling fields.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=PaymentOrderServiceImplVirtualPayTest,PaymentControllerVirtualPayTest test
```

Expected: tests fail because `virtualPayParams` is absent.

- [ ] Add `VirtualPayParamsVO`:

```java
private String mode;
private String signData;
private String paySig;
private String signature;
```

- [ ] Add `virtualPayParams` to `PrepayVO`; keep `prepayParams` only for historical/mock compatibility.
- [ ] Refactor order creation so virtual-payment mode:
  - Performs no V3 prepay network call.
  - Loads product ID and original/promotional prices only from the database.
  - Persists `virtualProductId`, `virtualEnv`, random `virtualAttach`, and SHA-256 of the exact `signData`.
  - Uses `activitySellingPrice` only when actual unit price differs from original price.
  - Uses `buyQuantity=1` for subscriptions and the sanitized quantity for items.
- [ ] Keep the existing five-second dedup lock. Never reuse an older `outTradeNo` after a signing or session error.
- [ ] Add `GET /payment/capabilities` returning:

```json
{
  "enabled": true,
  "minSdkVersion": "2.19.2",
  "minIosWechatVersion": "8.0.68",
  "iosEnabled": false,
  "mode": "short_series_goods"
}
```

- [ ] Set `enabled` in the capability response from `new-order-enabled`; the integration-level `enabled` flag must not be exposed as permission to create an order.
- [ ] For cancellation, skip `WechatPayClient.closeOrder` when `pay_channel=WECHAT_VIRTUAL`; retain it for historical JSAPI.
- [ ] Rerun tests:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=PaymentOrderServiceImplVirtualPayTest,PaymentControllerVirtualPayTest test
```

Expected: tests pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/payment main/manager-api/src/test/java/xiaozhi/modules/payment
git commit -m "feat: create virtual payment orders"
```

## Task 6: Centralize Mini Program virtual-payment behavior

**Files:**

- Create: `main/miniprogram/utils/virtual-payment.js`
- Create: `main/miniprogram/utils/virtual-payment.test.js`
- Modify: `main/miniprogram/pages/subscription/subscription.js`
- Modify: `main/miniprogram/pages/backpack/backpack.js`

- [ ] Write Node tests with a mocked `global.wx` for:
  - Capability rejection when `wx.requestVirtualPayment` is unavailable.
  - iOS purchase rejection when the backend capability is disabled.
  - `wx.checkSession` success path.
  - Session refresh through `wx.login` and `/wechat/login`.
  - Exact pass-through of `mode`, `signData`, `paySig`, and `signature`.
  - `-2` cancellation without marking the order failed.
  - `-15007` re-login followed by creation of a new order, with no reuse of the previous order number.
  - Success initiating active query and local-status polling.
  - No reference to `wx.requestPayment`.
- [ ] Run:

```bash
node main/miniprogram/utils/virtual-payment.test.js
```

Expected: test fails because the utility does not exist.

- [ ] Implement an exported `purchaseVirtualProduct(options)` function. Required options:

```javascript
{
  productType: 'SUBSCRIPTION' | 'ITEM',
  productRefId: Number,
  quantity: Number,
  onFulfilled: Function
}
```

- [ ] Read the current `main/miniprogram/utils/request.js` API and use it without discarding unrelated working-tree changes.
- [ ] Ensure the utility:
  - Checks `/payment/capabilities`.
  - Checks the platform and `wx.canIUse('requestVirtualPayment')`.
  - Refreshes login before order creation when `wx.checkSession` fails.
  - Passes backend strings through unchanged.
  - Calls `POST /payment/order/{outTradeNo}/query` after client success.
  - Polls `GET /payment/order/{outTradeNo}` until fulfilled, terminal, or timeout.
  - Maps official error codes to user-safe Chinese messages.
- [ ] Replace duplicated `wx.requestPayment` logic in subscription and backpack pages with the shared utility.
- [ ] Run:

```bash
node main/miniprogram/utils/virtual-payment.test.js
node main/miniprogram/pages/backpack/logic.test.js
rg -n "wx\\.requestPayment" main/miniprogram/pages/subscription main/miniprogram/pages/backpack main/miniprogram/utils
```

Expected: tests pass and the final search returns no matches in the migrated paths.

- [ ] Commit:

```bash
git add main/miniprogram/utils/virtual-payment.js main/miniprogram/utils/virtual-payment.test.js main/miniprogram/pages/subscription/subscription.js main/miniprogram/pages/backpack/backpack.js
git commit -m "feat: use Mini Program virtual payment"
```

## Task 7: Receive XPay events and fulfill idempotently

**Files:**

- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/notify/WechatMessageCrypto.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/notify/VirtualPayEvent.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/VirtualPaymentNotifyService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/VirtualPaymentNotifyServiceImpl.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/controller/VirtualPaymentNotifyController.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/security/config/ShiroConfig.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/dao/PaymentOrderDao.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/FulfillmentDispatcherImpl.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/virtual/notify/WechatMessageCryptoTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/service/impl/VirtualPaymentNotifyServiceImplTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/controller/VirtualPaymentNotifyControllerTest.java`

- [ ] Add message-crypto tests for URL verification, signature mismatch, replay timestamp rejection, encrypted payload round trip, wrong AppID rejection, and JSON/XML response format.
- [ ] Add service tests for:
  - Valid `xpay_goods_deliver_notify`.
  - Product, quantity, amount, openid, env, and attach mismatches.
  - Duplicate event after `FULFILLED`.
  - Retry from `PAID` after a prior fulfillment failure.
  - Callback and query racing to fulfill only once.
  - Redacted callback log.
  - Complaint and risk events producing audit/alert records without changing entitlement.
- [ ] Add controller tests asserting `{"ErrCode":0,"ErrMsg":"success"}` on success and nonzero `ErrCode` on retryable processing failure.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=WechatMessageCryptoTest,VirtualPaymentNotifyServiceImplTest,VirtualPaymentNotifyControllerTest test
```

Expected: compilation fails because the new types do not exist.

- [ ] Implement `WechatMessageCrypto` for the exact push mode configured in the WeChat console. Support plaintext signature mode and safe AES mode; reject unsigned requests in all enabled environments.
- [ ] Add `POST /payment/notify/virtual` and the GET verification handshake if the WeChat console requires it.
- [ ] Add only this route to the anonymous Shiro filter; do not widen `/payment/**`.
- [ ] Parse and dispatch:
  - `xpay_goods_deliver_notify`
  - `xpay_refund_notify`
  - `xpay_complaint_notify`
  - `xpay_wxpay_callback_notify`
- [ ] Validate every goods-delivery field before state transition.
- [ ] Reuse `FulfillmentDispatcherImpl`, but enforce an entitlement idempotency key derived from `payment_order.id`.
- [ ] Store only allow-listed headers, redacted Body, and SHA-256 hashes.
- [ ] Rerun focused tests:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=WechatMessageCryptoTest,VirtualPaymentNotifyServiceImplTest,VirtualPaymentNotifyControllerTest test
```

Expected: tests pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/payment main/manager-api/src/main/java/xiaozhi/modules/security/config/ShiroConfig.java main/manager-api/src/test/java/xiaozhi/modules/payment
git commit -m "feat: handle virtual payment events"
```

## Task 8: Replace V3 reconciliation with channel-aware XPay reconciliation

**Files:**

- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/task/PaymentOrderMaintenanceTask.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/dao/PaymentOrderDao.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/service/impl/PaymentOrderReconciliationTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/task/PaymentOrderMaintenanceTaskTest.java`

- [ ] Write reconciliation tests proving:
  - `WECHAT_VIRTUAL` uses `/xpay/query_order`.
  - Historical `WECHAT_JSAPI` still uses `WechatPayClient.queryOrder`.
  - Status `2` can mark paid and fulfill.
  - Status `4` can repair missing local fulfillment.
  - Status `5`/`8` moves into refund reconciliation, not fulfillment.
  - Amount, openid, environment, or order-number mismatch blocks fulfillment.
  - Successful local fulfillment followed by remote status not equal to `4` calls `notifyProvideGoods`.
  - One failing order does not stop the scheduled batch.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=PaymentOrderReconciliationTest,PaymentOrderMaintenanceTaskTest test
```

Expected: tests fail because reconciliation is still hard-wired to V3.

- [ ] Route queries by persisted `pay_channel`, never by the current feature flag.
- [ ] Persist `wx_order_id` and `wxpay_order_id`/`transaction_id` from query results.
- [ ] Keep atomic `PENDING -> PAID -> FULFILLED` transitions and transaction boundaries.
- [ ] After local fulfillment, call `notifyProvideGoods` only when the XPay order is not already status `4`.
- [ ] Keep callback and reconciliation active when new-order creation is disabled.
- [ ] Rerun focused tests:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=PaymentOrderReconciliationTest,PaymentOrderMaintenanceTaskTest test
```

Expected: tests pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java main/manager-api/src/main/java/xiaozhi/modules/payment/task/PaymentOrderMaintenanceTask.java main/manager-api/src/main/java/xiaozhi/modules/payment/dao/PaymentOrderDao.java main/manager-api/src/test/java/xiaozhi/modules/payment
git commit -m "feat: reconcile virtual payment orders"
```

## Task 9: Implement asynchronous XPay refunds and entitlement rollback safety

**Files:**

- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/PaymentRefundService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentRefundServiceImpl.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/controller/PaymentRefundController.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/dto/RefundDTO.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/dao/PaymentRefundDao.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/dao/PaymentOrderDao.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/VirtualPaymentNotifyServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/payment/task/PaymentOrderMaintenanceTask.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/subscription/service/SubscriptionFulfillmentService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/subscription/service/impl/SubscriptionFulfillmentServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/item/service/ItemFulfillmentService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/item/service/impl/ItemFulfillmentServiceImpl.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/service/impl/PaymentRefundServiceImplTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/payment/controller/PaymentRefundControllerTest.java`

- [ ] Write refund tests for:
  - Admin permission required.
  - Querying the latest `left_fee` before refund.
  - Rejecting zero, negative, over-limit, duplicate, pending, and cross-channel refund requests.
  - Persisting `PROCESSING` before the external call.
  - Not marking refund success when `refund_order` merely starts a task.
  - Completing from `xpay_refund_notify`.
  - Completing from scheduled `query_order` when the notification is lost.
  - Idempotent duplicate refund notification.
  - Entitlement rollback never creating negative item balances or subscription duration.
- [ ] Run:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=PaymentRefundServiceImplTest,PaymentRefundControllerTest test
```

Expected: compilation fails because the refund service/controller do not exist.

- [ ] Make `RefundDTO` accept only `orderId`, `refundFen`, `refundReason` enum `0..5`, and `reqFrom` enum `1..3`; resolve openid and channel IDs on the server.
- [ ] Generate `refund_order_id` as `VR + yyyyMMddHHmmss + 14 alphanumeric characters`, within 32 characters.
- [ ] Add `POST /payment/refund` with `payment:refund` permission.
- [ ] Route historical JSAPI refunds to the existing V3 client and virtual refunds to XPay based on the persisted order channel.
- [ ] Mark a virtual refund successful only after `RetCode=0` notification or final query status.
- [ ] Make subscription/item rollback idempotent. When consumed entitlement cannot be fully recovered, mark the refund for manual review rather than creating an invalid negative state.
- [ ] Add scheduled reconciliation for refund records stuck in `PROCESSING` over five minutes.
- [ ] Rerun focused tests:

```bash
cd main/manager-api
mvn -DskipTests=false -Dtest=PaymentRefundServiceImplTest,PaymentRefundControllerTest test
```

Expected: tests pass.

- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/payment main/manager-api/src/main/java/xiaozhi/modules/subscription/service main/manager-api/src/main/java/xiaozhi/modules/item/service main/manager-api/src/test/java/xiaozhi/modules/payment
git commit -m "feat: support virtual payment refunds"
```

## Task 10: Add operational checks, run the full suite, and complete sandbox acceptance

**Files:**

- Create: `main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/VirtualProductStartupValidator.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/payment/virtual/VirtualProductStartupValidatorTest.java`
- Modify: `main/manager-api/docs/companion-subscription-items-payment.md`
- Modify: `main/miniprogram/README_en.md`
- Verify: `docs/superpowers/specs/2026-07-23-wechat-virtual-payment-design.md`

- [ ] Write validator tests proving `new-order-enabled=true` refuses to start when an active plan/SKU lacks `virtual_product_id` or has an invalid original/promotional price.
- [ ] Implement the validator without calling WeChat at startup; it validates local completeness and logs only product codes.
- [ ] Update the existing payment documentation to point to the new spec and label JSAPI virtual-goods sections as historical.
- [ ] Document operator setup:
  - All environment variables and ownership.
  - OfferID/AppKey locations.
  - Product ID mapping and publish order.
  - Callback URL and push mode.
  - iOS gate.
  - Secret rotation.
  - Shutdown behavior that keeps callbacks/reconciliation alive.
- [ ] Run backend tests with the POM's default test skip explicitly disabled:

```bash
cd main/manager-api
mvn -DskipTests=false test
```

Expected: all tests pass.

- [ ] Run all Mini Program unit files:

```bash
for test_file in $(find main/miniprogram -name '*.test.js' -type f | sort); do node "$test_file"; done
```

Expected: every test exits 0.

- [ ] Run static safety searches:

```bash
rg -n "wx\\.requestPayment" main/miniprogram/pages/subscription main/miniprogram/pages/backpack main/miniprogram/utils
rg -n "sessionKey|session_key|access_token|app-key|AppKey" main/manager-api/src/main/java/xiaozhi/modules/payment main/manager-api/src/main/java/xiaozhi/modules/wechat
git diff --check
```

Expected:

- No `wx.requestPayment` in migrated purchase paths.
- Secret-related hits are declarations or redacted handling, not value logging.
- `git diff --check` reports no errors.

- [ ] Complete the sandbox matrix:
  - Standard/promo/upgrade subscription.
  - Single/multiple/promo item.
  - Cancellation and expired session.
  - Unpublished product, price mismatch, wrong environment.
  - Lost callback reconciliation.
  - `notify_provide_goods` recovery.
  - Full/partial/repeated refund.
  - Complaint and risk-event audit.
  - One-fen upgrade. If WeChat rejects it, activate the zero-value entitlement-change branch defined in the spec.
- [ ] Verify in production-like configuration:
  - `env=0`.
  - Production AppKey selected.
  - Unsupported/iOS-disabled platforms cannot purchase and never see JSAPI fallback.
  - `new-order-enabled=false` stops new sales without stopping callbacks, reconciliation, or refunds.
- [ ] Request code review focused on payment security, signature byte identity, idempotency, callback authentication, refund state transitions, and sensitive-data redaction. Resolve every P0/P1 finding and rerun the affected tests.
- [ ] Commit:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/payment/virtual/VirtualProductStartupValidator.java main/manager-api/src/test/java/xiaozhi/modules/payment/virtual/VirtualProductStartupValidatorTest.java main/manager-api/docs/companion-subscription-items-payment.md main/miniprogram/README_en.md
git commit -m "docs: finalize virtual payment rollout"
```

## Completion Gate

Implementation is complete only when all conditions are true:

- [ ] All virtual-goods purchase entries call `wx.requestVirtualPayment`.
- [ ] The exact `signData` string signed by the backend is passed unchanged to WeChat.
- [ ] Product ID, price, quantity, openid, env, and attach are validated before fulfillment.
- [ ] Duplicate callback/query races fulfill once.
- [ ] session_key is encrypted at rest and absent from logs.
- [ ] XPay query, delivery confirmation, refund, refund reconciliation, complaint, and risk events are covered.
- [ ] Full backend and Mini Program test suites pass.
- [ ] Sandbox matrix passes with redacted evidence.
- [ ] Production configuration rejects sandbox mode and has no JSAPI virtual-goods fallback.
