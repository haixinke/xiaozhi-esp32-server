# NFC 功能对鸿蒙（HarmonyOS）系统的支持情况调研

> 编写日期：2026-09-10
> 依据文档：`docs/nfc/egg-nfc-feature-spec.md`、`docs/nfc/egg-nfc-lifecycle-flow.md`、`docs/nfc/egg-nfc-claimref-crypto-flow.md`、`docs/nfc/egg-nfc-env-config.md`
> 结论一句话：**当前实现依赖"手机系统读取 NDEF 标签并拉起微信"，小程序自身不使用任何 NFC API；该路径在 HarmonyOS 4.x（兼容 Android）手机上可用，在 HarmonyOS NEXT（纯血鸿蒙）微信上目前不可用。**

---

## 1. 现状实现摘要

### 1.1 实现方式：NDEF 标签 + 系统拉起微信，小程序零 NFC API

本项目的 NFC 功能不是"小程序读芯片"，而是"芯片里存一个微信专属的打开小程序链接（Scheme），由手机操作系统读标签并调起微信"：

1. 服务端调用微信 `generatenfcscheme` 接口，为每件实物生成唯一 Scheme（`weixin://platform/nfc?...` 形式的 openlink）。
   - `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcHttpTransport.java:17`（`GENERATE_URL = "https://api.weixin.qq.com/wxa/generatenfcscheme"`）
   - 请求体定义：`main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcSchemeRequest.java`
2. 工厂把 Scheme 写入 NFC 标签的 NDEF Message，包含两条 Record：
   - URI Record（TNF `0x01`，Type `U`，Payload = openlink）——iOS 只认这一条；
   - Android Application Record（TNF `0x04`，Type `android.com:pkg`，Payload = `com.tencent.mm`）——Android 靠 AAR 指定拉起微信。
   - 格式定义见 `docs/nfc/egg-nfc-feature-spec.md` §6.3。
3. 用户手机触碰标签后：**Android 由系统 NDEF 分发机制按 AAR 拉起微信；iOS 由系统读取 URI Record 后经通知横幅拉起微信**；微信再打开小程序领取页 `/pages/nfc-claim/nfc-claim?...&v=1&ref=<claimRef>`。
4. 小程序只在 `App.onLaunch` / `App.onShow` 里捕获启动参数（path + query.ref），完全不接触 NFC 硬件：
   - `main/egg-miniprogram/miniprogram/app.js:43`、`app.js:60` 调用 `captureNfcClaimIntent(options)`
   - `main/egg-miniprogram/miniprogram/utils/nfc-claim-intent.js`（意图捕获/过期/清理）
   - `main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.js`（领取页）
   - `main/egg-miniprogram/miniprogram/utils/nfc-claim-api.js:4,8`（调后端 `/pdc/nfc/claim/preview`、`/pdc/nfc/claim/confirm`）

规格文档对此有明确决策（`docs/nfc/egg-nfc-feature-spec.md:493-495`）：

> 11.5 不使用 `wx.getNFCAdapter`：本功能由系统读取标签中的 Scheme 并调起微信，小程序不主动读取芯片。

全仓代码验证：`wx.getNFCAdapter`、`IsoDep`、`startHCE` 等小程序 NFC API 在 `main/egg-miniprogram/` 中无任何调用；`main/manager-mobile/`（Uni-app）和 `main/xiaozhi-server/`（Python）中没有任何 NFC 相关代码。NFC 只分布在三个端：

| 端 | 角色 | 关键代码 |
|---|---|---|
| manager-api（Java） | 调微信 generatenfcscheme、领取核销、写卡任务 | `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/` |
| manager-web（Vue） | superAdmin 生产/写卡/库存/激活管理页 | `main/manager-web/src/views/nfc/NfcWriteJobManagement.vue` 等 |
| egg-miniprogram（微信小程序） | 领取页 + 启动意图捕获 | `pages/nfc-claim/`、`utils/nfc-claim-intent.js` |

### 1.2 这意味着鸿蒙支持问题等价于一个问题

由于小程序侧没有任何平台相关 NFC 代码，**鸿蒙是否支持不取决于小程序代码，而取决于：鸿蒙系统 + 鸿蒙版微信能否完成"读 NDEF 标签 → 拉起微信 → 打开小程序页"这一步**。因此需要分鸿蒙版本讨论。

---

## 2. 鸿蒙支持结论（分场景）

### 2.1 HarmonyOS 4.x 及更早（兼容 AOSP 的鸿蒙，如 Mate 40/50 系列、Mate 60 的 HarmonyOS 4）

**结论：支持，行为与 Android 一致。**

这类设备运行的是 Android 兼容版微信（APK），系统的 NFC 标签分发机制（`NfcAdapter` / NDEF dispatch / AAR）与原生 Android 相同，标签中的 AAR（`com.tencent.mm`）可以正常把标签分发给微信。微信官方要求"安卓微信客户端 8.0.14 及以上"（来源见 §4），在这些设备的微信 Android 版上均满足。

### 2.2 HarmonyOS NEXT（纯血鸿蒙，不再兼容 AOSP，如 2024 年底起的 Mate 70 / Mate X6 等新机型）

**结论：目前不支持"碰一碰打开小程序"领取，属于微信侧能力缺口，而非本仓库代码问题。**

三点依据：

1. **微信官方文档未把鸿蒙列为支持平台。** 《NFC 标签打开小程序》官方文档只写明"安卓微信客户端 8.0.14 开始支持，iOS 现网版本均已覆盖"，并明确要求 Android 端写入 AAR（`android.com:pkg`）来指定拉起微信——AAR 是 Android 专有机制，HarmonyOS NEXT 完全不识别（来源见 §4）。
2. **微信开放社区已有实证反馈。** 2025 年 3 月有开发者在微信开放社区反馈"鸿蒙版微信开放标签无法唤起小程序"（来源见 §4），说明鸿蒙原生版微信的标签/链接唤起小程序链路当时尚未适配。截至本调研日期（2026-09），微信官方 NFC 文档仍未新增 HarmonyOS 平台说明。
3. **系统能力存在，缺口在微信适配。** HarmonyOS NEXT 本身具备完整的 NFC 标签读写能力（`@ohos.nfc.tag`，属 ConnectivityKit），系统也支持基于 URI scheme 的 want 匹配拉起应用；支付宝鸿蒙原生版 2024 年 10 月即已上线 NFC"碰一下"支付，证明"系统读标签 → 拉起目标应用"的链路在鸿蒙上走得通。但微信鸿蒙原生版需要主动注册处理 `weixin://platform/nfc` 这类 NFC 标签分发，目前没有公开证据表明已完成该适配（来源见 §4）。

补充说明：标签里多写的 AAR Record 在鸿蒙/iOS 上会被安全忽略，不构成兼容性问题；真正的单点是 URI Record 的分发目标——即鸿蒙版微信是否注册为 NFC URI（`weixin://platform/nfc?...`）的处理者。

### 2.3 微信小程序内的 `wx.getNFCAdapter`（备用路径）

本仓库未使用该 API，仅作参考：该 API 的官方文档同样只覆盖 Android/iOS 微信客户端，鸿蒙版微信不支持（来源见 §4）。即使未来考虑"小程序主动读标签"作为降级方案，在鸿蒙微信上此路也不通。

### 2.4 Uni-app（manager-mobile）与其他端

`main/manager-mobile/` 当前没有任何 NFC 功能（管理端的写卡/激活均在 manager-web 完成），不存在"Uni-app 原生插件 / Android HCE 依赖"之类的 Android-only 路径需要评估。若未来把"扫码激活/写卡"搬到移动端 App，则属于新增功能，鸿蒙支持需按 HarmonyOS NEXT 的 `@kit.ConnectivityKit` 单独评估。

---

## 3. 如需支持鸿蒙的改造建议

按投入从小到大排序：

1. **真机验证与文档标注（立即，低成本）。** 用一台 HarmonyOS NEXT 真机（如 Mate 70）的微信触碰量产标签，记录实际表现；在产品说明和领取页 `UNAVAILABLE` 状态的文案中区分"设备/系统不支持 NFC 拉起"与"资产不可领取"。当前 `nfc-claim` 页已有 `NETWORK_ERROR`/`UNAVAILABLE` 状态，只需确保微信被拉起失败后用户有可理解的提示（触碰无响应时系统无任何反馈，用户可能重复触碰——这属于体验问题而非代码问题）。
2. **增加降级领取入口（需要产品决策，改动小）。** 规格文档当前明确"不提供二维码或短领取码降级入口"（`docs/nfc/egg-spec` 决策表"降级入口"行）。若鸿蒙用户占比上升，可考虑：在商品包装/说明书放置"领取二维码"或在小程序内提供"手动输入领取码"入口，复用现有 `/pdc/nfc/claim/preview` + `/confirm` 接口（仅需一个新的入口页，后端零改动，因为 claimRef 校验与传输渠道无关）。这是对鸿蒙 NEXT 用户最直接的可行补偿。
3. **等待/推动微信适配（不可控）。** 关注微信官方 NFC 文档与开放社区：一旦鸿蒙版微信注册 NFC 标签分发，本项目的标签无需任何改动即可直接可用（NDEF 内容不变，只是分发目标多了鸿蒙版微信）。建议在量产验收清单中增加一条"HarmonyOS NEXT 微信真机触碰回归"，作为长期跟踪项。
4. **华为生态原生路径（大投入，仅在战略需要时考虑）。** 若需要在鸿蒙上获得与 Android/iOS 对等的"碰一碰"体验，可走华为元服务/原子化服务 + HarmonyOS 碰一碰能力，为鸿蒙用户单独构建领取链路；这与现有微信小程序链路完全平行，意味着新增一个鸿蒙端入口和后端对接，不建议在第一版做。

---

## 4. 依据与来源

### 4.1 本仓库代码与文档

- `docs/nfc/egg-nfc-feature-spec.md` §6（微信平台配置、generatenfcscheme、NDEF 格式含 AAR）、§11.5（不使用 wx.getNFCAdapter）、§17.4（真机验收 Android 微信 8.0.14+、iPhone XS+）
- `docs/nfc/egg-nfc-lifecycle-flow.md` §1（"不是让小程序直接读取芯片内容，而是把微信可识别的打开小程序链接写进 NFC 标签里"）
- `docs/nfc/egg-nfc-claimref-crypto-flow.md`（Scheme 生成与加密链路）
- `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcHttpTransport.java:17`
- `main/egg-miniprogram/miniprogram/app.js:43,60`、`utils/nfc-claim-intent.js`、`pages/nfc-claim/nfc-claim.js`、`utils/nfc-claim-api.js`

### 4.2 微信官方文档

- NFC 标签打开小程序（支持平台、NDEF/AAR 要求、使用限制）：
  https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/NFC.html
  - 关键原文："安卓微信客户端 8.0.14 开始支持，iOS 现网版本均已覆盖"；"iOS 只识别 URI Record，安卓还需要 AAR 来指定拉起微信"。文档未提及 HarmonyOS。
- wx.getNFCAdapter API（仅 Android/iOS 端支持）：
  https://developers.weixin.qq.com/miniprogram/dev/api/device/nfc/wx.getNFCAdapter.html
- generatenfcscheme 服务端接口：
  https://developers.weixin.qq.com/miniprogram/dev/server/API/qrcode-link/url-scheme/api_generatenfcscheme.html
- 微信开放社区：鸿蒙版微信开放标签无法唤起小程序（2025-03 反馈，实证鸿蒙版微信唤起链路缺口）：
  https://developers.weixin.qq.com/community/develop/doc/0000ac6abb09b8d478d22276d61400

### 4.3 华为 / HarmonyOS 官方资料

- HarmonyOS NEXT NFC 开发（ConnectivityKit / `@ohos.nfc.tag`，基于"碰一碰"的功能开发实践与权限要求）：
  https://developer.huawei.com/consumer/cn/forum/topic/0202185648760294015
- HarmonyOS NEXT 系统"碰一碰"特性官方展示（文件分享、照片互传、Wi-Fi 共享、游戏组队）：
  https://www.sohu.com/a/835516993_104421

### 4.4 旁证（鸿蒙 NFC 应用适配生态）

- 支付宝鸿蒙原生版 2024-10 上线"碰一下"NFC 支付并支持小程序（证明 HarmonyOS NEXT 系统级 NFC 拉起链路可行，关键在各 App 自身适配）：
  https://www.ithome.com/0/804/342.htm
- uni-app 文档：通过 URL Scheme 唤起鸿蒙应用（HarmonyOS 支持 scheme 级 want 匹配，应用需在 module.json5 的 skills 中声明 uri scheme）：
  https://uniapp.dcloud.net.cn/tutorial/app-nativeresource-harmony-urlscheme.html

---

## 5. 术语区分备忘

| 术语 | 含义 | 与本功能的关系 |
|---|---|---|
| HarmonyOS 4.x | 基于 AOSP 兼容层，可运行 Android 应用 | 运行 Android 版微信，NFC 碰一碰领取**可用** |
| HarmonyOS NEXT（纯血鸿蒙） | 自研内核，不兼容 Android APK，应用需鸿蒙原生版 | 运行鸿蒙原生版微信，NFC 碰一碰领取**当前不可用**（微信未适配 NFC 标签分发） |
| AAR | Android Application Record，NDEF 的一种 Record 类型 | 仅 Android 识别；iOS/鸿蒙忽略；本项目的标签写有 AAR |
| 元服务/原子化服务 | 华为 HarmonyOS 的轻量应用形态 | 潜在的鸿蒙原生降级/替代路径，当前未采用 |
