# manager-api 接入真实微信支付 V3 实现方案

## Context

manager-api 已经搭好了**支付订单 / 履约 / 回调**的全套骨架（[`PaymentOrderServiceImpl`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java)、[`PaymentNotifyServiceImpl`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentNotifyServiceImpl.java)、[`FulfillmentDispatcher`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/FulfillmentDispatcherImpl.java)），并通过 [`WechatPayClient`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayClient.java) 接口完成了与支付通道的解耦。当前唯一的实现是 [`MockWechatPayClient`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/MockWechatPayClient.java)，仅供本地联调；生产环境需要一个 **真实的 V3 JSAPI 实现**，把订单上报到微信、接收带签名的回调、并支持关单和退款。

本方案不改动业务侧任何已存在的骨架（订单创建/查询/履约/限频/回调审计/超时关单），**只补齐**：

1. 引入官方 [`wechatpay-java`](https://github.com/wechatpay-apiv3/wechatpay-java) SDK；
2. 新增 `WechatPayV3Client` 实现 `WechatPayClient` 全部 5 个方法，并以 `@Primary` 注册；
3. 把 `WechatPayConfig` 改为可从 `sys_params` 加载、敏感字段用 `AESUtils` 解密的"配置加载器"；
4. 在 Liquibase 迁移中追加 `wechat.pay.*` 6 个 sys_params 占位项（生产由运维写入真实值）；
5. 启动校验：真实 client 模式下，缺关键配置时启动失败（防止配错上线）。

---

## 1. 您需要提供的配置

下表 6 项写入 `sys_params`（`param_type=1` 业务参数；其中 2 项私密字段以 `AESUtils` 加密入库）。

| param_code | value_type | 是否加密 | 说明 |
|---|---|---|---|
| `wechat.pay.mock` | `boolean` | 否 | 是否走 mock；生产必须 `false` |
| `wechat.pay.mchid` | `string` | 否 | 微信支付**商户号**（10 位数字） |
| `wechat.pay.serial_no` | `string` | 否 | **商户 API 证书序列号**（40 位 16 进制） |
| `wechat.pay.private_key` | `string` | **是** | 商户 API **私钥** `apiclient_key.pem` 全文（含 `-----BEGIN PRIVATE KEY-----`，AES 加密入库） |
| `wechat.pay.api_v3_key` | `string` | **是** | **APIv3 密钥**（32 字节，AES 加密入库） |
| `wechat.pay.notify_url` | `string` | 否 | 公网回调 URL，例如 `https://api.example.com/xiaozhi/payment/notify` |

> 小程序 `appid` 已存在 `wechat.miniprogram.appid`，**复用**，无需新增。
> AES 加密密钥沿用项目现成的 `Constant.SERVER_SECRET`（`server.secret`，启动期由 `SysParamsServiceImpl.initServerSecret()` 自动生成且已存在）。

**怎么生成上述值（请按这份操作单准备）：**

1. 登录 https://pay.weixin.qq.com/ 「商户平台」→「账户中心 → API 安全」：
   - 复制商户号 → `wechat.pay.mchid`；
   - 设置 / 获取 **APIv3 密钥**（32 字节随机串） → `wechat.pay.api_v3_key`；
   - 申请并下载 **API 证书** 压缩包（含 `apiclient_cert.pem` / `apiclient_key.pem` / 序列号 `serial_no.txt`），其中
     - `serial_no.txt` 内容 → `wechat.pay.serial_no`；
     - `apiclient_key.pem` 全文 → `wechat.pay.private_key`。
2. 「产品中心 → 开发配置 → 支付配置」绑定**小程序 appid**（已有 `wechat.miniprogram.appid`，需在该配置下被授权可发起 JSAPI）。
3. 在「APIv3 → 回调通知」处填我们公网入口：`https://<your-domain>/xiaozhi/payment/notify`（HTTPS 必需，需公网可达，TLS 1.2+）。
4. （可选）准备一个测试商户号或在沙箱跑通后再切线上。

> 平台证书 **不**需要您提供：`wechatpay-java` 的 `RSAAutoCertificateConfig` 会用上面 4 项秘钥自动从微信下载平台证书并周期滚动更新，生命周期由 SDK 内部管理。

---

## 2. 代码改动清单

### 2.1 [`pom.xml`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/pom.xml) —— 引入 SDK
```xml
<dependency>
    <groupId>com.github.wechatpay-apiv3</groupId>
    <artifactId>wechatpay-java</artifactId>
    <version>0.2.17</version>
</dependency>
```

### 2.2 新增 [`WechatPayProperties.java`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayProperties.java)
把现有的 [`WechatPayConfig`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayConfig.java) 重命名为 `WechatPayProperties`（`@Data` POJO 不变），并新增静态 `loadFrom(SysParamsService)`：
- 读取 6 个参数 + `wechat.miniprogram.appid`；
- `private_key` / `api_v3_key` 用 `AESUtils.decrypt(serverSecret, ...)` 解密（运行时拿 `server.secret`）；
- 字段缺失时直接抛 `RenException(PAY_CHANNEL_NOT_AVAILABLE)`，由调用方决定是否致命（启动校验里=致命，运行时=该订单失败）。

### 2.3 新增 [`WechatPayV3Client.java`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayV3Client.java)
```java
@Slf4j
@Primary
@Component("wechatPayV3Client")
@ConditionalOnProperty(name = "wechat.pay.mock", havingValue = "false")
public class WechatPayV3Client implements WechatPayClient {
    private final SysParamsService sysParamsService;
    private volatile JsapiServiceExtension jsapi;        // 下单/关单
    private volatile RefundService refundService;        // 退款
    private volatile NotificationParser notificationParser; // 验签+解密
    private volatile WechatPayProperties props;
    private volatile RSAAutoCertificateConfig sdkConfig;

    @PostConstruct void init() { rebuild(); }            // 启动期 build；缺配置直接抛错

    public boolean isMockMode() { return false; }
    public String getAppid()    { return props.getAppid(); }

    public PrepayResult jsapiPrepay(PrepayRequest req)   { /* JsapiServiceExtension.prepayWithRequestPayment */ }
    public void closeOrder(String outTradeNo)            { /* JsapiServiceExtension.closeOrder */ }
    public RefundResult refund(RefundRequest req)        { /* RefundService.create */ }
    public NotifyResult parseNotify(Map<String,String> headers, String body) {
        /* 用 NotificationParser.parse 验签+解密 → Transaction → 映射 NotifyResult */
    }
}
```
关键点：
- `jsapiPrepay`：调用 `JsapiServiceExtension.prepayWithRequestPayment(...)`，SDK 直接返回 `appId / timeStamp / nonceStr / package / signType=RSA / paySign` 6 个字段，作为 `PrepayResult.jsapiParams` 透传给小程序，**完全契合现有 `PrepayVO` 结构**；
- `parseNotify`：复用现有 [`PaymentNotifyServiceImpl`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentNotifyServiceImpl.java) 的"验签 → 去重 → 状态推进 → 履约"流水线；这里只把 `Wechatpay-Signature/Timestamp/Nonce/Serial` 四个 header + body 喂给 `NotificationParser.parse`，把解密出的 `Transaction` 映射回 `NotifyResult{outTradeNo, transactionId, amountFen, valid=true, paySuccess=Transaction.TradeStateEnum==SUCCESS}`；签名失败置 `valid=false`，由上层 controller 返回 5xx 让微信重推。
- 异常分级：业务错误（订单不存在、金额不匹配等）→ 返回 `valid=true, paySuccess=false`；签名/解密异常 → `valid=false`；网络/SDK 异常 → 抛 `RenException(PAY_CHANNEL_NOT_AVAILABLE)`，配合 [`PaymentOrderServiceImpl.createOrder`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/service/impl/PaymentOrderServiceImpl.java#L102-L109) 既有错误处理。

### 2.4 修改 [`MockWechatPayClient`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/MockWechatPayClient.java)
将 `@ConditionalOnProperty` 的 `matchIfMissing` 从 `true` 改为 `false`：
```java
@ConditionalOnProperty(name = "wechat.pay.mock", havingValue = "true")
```
原因：避免新版本默认走 mock；明确要求 dev 必须显式配 `wechat.pay.mock=true`，prod 走真实 client（`wechat.pay.mock=false`）。**配合**：在 [`application.yml`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/resources/application.yml) `spring.profiles.active: dev` 段落同步加 `wechat.pay.mock: true`，保持本地启动行为不变。

### 2.5 加强 [`WechatPayClientStartupGuard`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayClientStartupGuard.java)
新增"真实模式必须能成功 build"校验：当 `wechatPayClient.isMockMode()=false` 时，调用 `client.healthCheck()`（新增的接口默认方法，调用一次内部 props 读取）；任何配置缺失即抛 `IllegalStateException`，避免线上"启动 OK、第一次下单才报错"。

### 2.6 Liquibase 迁移：新增 `202606181100.sql`
```sql
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark) VALUES
(600, 'wechat.pay.mock',         'true',  'boolean','1','微信支付是否走 mock；生产必须 false'),
(601, 'wechat.pay.mchid',        'null',  'string', '1','微信支付商户号'),
(602, 'wechat.pay.serial_no',    'null',  'string', '1','商户API证书序列号'),
(603, 'wechat.pay.private_key',  'null',  'string', '1','商户API私钥PEM(AES加密入库)'),
(604, 'wechat.pay.api_v3_key',   'null',  'string', '1','APIv3密钥(AES加密入库)'),
(605, 'wechat.pay.notify_url',   'null',  'string', '1','公网回调URL HTTPS');
```
追加到 [`db.changelog-master.yaml`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml) 末尾。**ID 从 600 起**，避开既有段。

### 2.7 文档与运维交接
- 在 [`main/manager-api/docs/companion-subscription-items-payment.md`](file:///Users/minwang/codes/github/xiaozhi-esp32-server/main/manager-api/docs/companion-subscription-items-payment.md) 第 222 行附近补一段「上线前 6 项配置写入清单 + 加密命令样例」。
- 给运维同学一段一次性"加密小工具"调用样例（直接复用 `AESUtils.encrypt(serverSecret, "<pem文本>")`，可走 manager-api 的 SysParams 后台界面，也可以临时跑一段 main 方法）。

---

## 3. 关键文件清单

**新增**
- `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayProperties.java`
- `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayV3Client.java`
- `main/manager-api/src/main/resources/db/changelog/202606181100.sql`

**修改**
- `main/manager-api/pom.xml` — 加 `wechatpay-java` 依赖
- `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayConfig.java` — **删除**（被 `WechatPayProperties` 取代；当前没有引用方）
- `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/MockWechatPayClient.java` — `matchIfMissing=true` 改为 `false`
- `main/manager-api/src/main/java/xiaozhi/modules/payment/wechat/WechatPayClientStartupGuard.java` — 加真实模式 build 校验
- `main/manager-api/src/main/resources/application.yml` — `spring.profiles.active: dev` 时 `wechat.pay.mock: true`
- `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` — 追加 `202606181100`
- `main/manager-api/docs/companion-subscription-items-payment.md` — 补"6 项配置写入清单"

**保持不动**（已经写好骨架）
- `PaymentController` / `PaymentNotifyController`
- `PaymentOrderServiceImpl` / `PaymentNotifyServiceImpl` / `FulfillmentDispatcherImpl`
- `PaymentOrderMaintenanceTask`（超时关单已通过 `WechatPayClient.closeOrder` 抽象）
- `WechatPayClient` 接口

---

## 4. 验证方法

### 4.1 本地（mock 模式回归）
```bash
# 切到 dev profile，wechat.pay.mock=true
mvn -pl main/manager-api -am clean spring-boot:run
```
- Liquibase 应跑通新 changeSet `202606181100`，`sys_params` 多 6 条记录；
- `MockWechatPayClient` 加载，`/xiaozhi/payment/order` 返回 mock prepayParams（`signType=MOCK`）；
- 既有 mock 回调路径（如有 `/payment/notify/mock`）继续可走，订阅/道具履约不受影响。

### 4.2 本地（真实模式预演）
```bash
# 步骤
1. 在管理后台「参数管理」把 6 条 wechat.pay.* 改为商户平台**沙箱/测试**的真实值
   - private_key / api_v3_key 必须用 AESUtils.encrypt(server.secret, ...) 加密后再入库
2. 把 wechat.pay.mock 改为 false
3. 重启 manager-api
```
- 启动日志看到 `WechatPayClient startup ok, mockMode=false`；
- `POST /payment/order` 返回真实 `prepay_id` 与 6 字段 `prepayParams`；
- 用小程序真机调起 `wx.requestPayment(prepayParams)`；
- 微信推送回调到 `notify_url` → `/xiaozhi/payment/notify`：
  - `payment_callback_log.signature_valid=1`、`process_result=SUCCESS`；
  - `payment_order` 状态 `0→1→2`，`paid_at`、`transaction_id`、`fulfilled_at` 写入；
  - `user_subscription` / `user_item` 按履约链路落数据。

### 4.3 异常用例必须验证
| 用例 | 预期 |
|---|---|
| 缺 `wechat.pay.mchid`、`mock=false` 启动 | `WechatPayClientStartupGuard` 抛 `IllegalStateException` 启动失败 |
| 错误的 `private_key` | `WechatPayV3Client.init()` 抛 `PAY_CHANNEL_NOT_AVAILABLE`，启动失败 |
| 回调 body 被改一字节 | `parseNotify().valid=false` → `payment_callback_log.signature_valid=0` → 返回 5xx |
| 同一 `transaction_id` 被微信重推 | 第二次 `process_result=DUPLICATE`，`payment_order.status` 维持 2，**不**重复履约 |
| 下单后用户 15 分钟未支付 | `PaymentOrderMaintenanceTask` 调 `WechatPayClient.closeOrder` → 微信侧关单成功，`status=5` |
| 非小程序用户（`ai_wechat_user.openid` 缺失）下单 | 返回 `PAY_OPENID_REQUIRED` |

### 4.4 上线 checklist
- [ ] 6 项 `sys_params` 已写入生产库，`private_key` / `api_v3_key` 已 AES 加密；
- [ ] `wechat.pay.notify_url` 公网 HTTPS 可达，证书有效，Nginx 不剥离 `Wechatpay-*` 头；
- [ ] 商户平台「APIv3 回调」与该 URL 一致；
- [ ] 商户号已绑定小程序 appid，已开通 JSAPI；
- [ ] 启动日志确认 `mockMode=false`；
- [ ] 用 1 元真实订单跑通一次。

---

## 5. 不在本期范围

- 退款回调路由（已有 `RefundService.create` 入口；微信退款回调通常走相同 notify URL，本期默认在 `parseNotify` 中识别 `event_type=REFUND.SUCCESS` 并交由后续退款联动迭代）；
- 对账单（T-1 拉取与差异比对）；
- 自动续费 / 签约扣款。
