# 产品方案：小程序「我的背包」道具页面

- 日期：2026-06-15
- 子项目：`main/miniprogram/`（完美女友微信小程序）
- 关联后端方案：[`main/manager-api/docs/companion-subscription-items-payment.md`](../../../main/manager-api/docs/companion-subscription-items-payment.md)
- 状态：设计已确认，待评审

---

## 1. 背景与目标

「完美女友」小程序已落地「我的契约」（订阅）购买链路。后端的 `item` 模块（道具 SKU + 用户库存 + 核销流水）与 `payment` 模块（统一下单 + 微信支付 V3 JSAPI + 回调履约）也已在 `manager-api` 实现，但小程序侧尚未消费。

「我」页面（`pages/settings`）已预留「我的背包」入口卡片与 `onBackpackTap` 处理函数（见 `settings.wxml:36`），但点击后尚无目标页面。

**本方案的目标**：新建一个二级页面 `pages/backpack`，实现：

1. 展示后端 `/item/skus` 返回的全部有效道具，按分类组织；
2. 展示用户已拥有的道具及数量（来自 `/item/inventory`）；
3. 每个道具展示功能说明；
4. 支持购买道具（`/payment/order` → `wx.requestPayment` → 回调履约 → 刷新库存）。

**非目标**：道具的「使用 / 核销」交互（换职业、换声音、赠送礼物等）发生在其他页面（`companion` 编辑、聊天），不在本页实现；本页只负责「浏览 + 购买 + 查看持有」。

---

## 2. 范围与默认决策

### 2.1 已确认决策（来自设计阶段）

| 项 | 决策 |
|---|---|
| 信息架构 | **方案 A — 单页分类叙事流**：一条长滚动，按分类分段，每张卡片同时呈现「持有量」与「购买」 |
| 购买模型 | **确认面板**：点购买 → 底部弹出面板（说明 + 数量 + 合计 + 微信支付）→ `wx.requestPayment` |
| 视觉 | 沿用 Ethereal Companion 设计系统：樱花红 `#864e5a→#d4737a` CTA、瓷白玻璃态卡片、金色 `#b8860b` 作为「背包/持有」身份色、宋体分类标题 |

### 2.2 默认决策（实现时可调整，需在评审时确认）

| 项 | 默认 | 说明 |
|---|---|---|
| 券类重复购买 | **允许囤积** | 已拥有的 `consumable_change` 券仍显示「购买」按钮，可继续买（每次固定 1 张） |
| 道具上下架 | **由后端 `status` 控制** | `listSkus` 仅返回 `status=1`（上架）；前端只渲染下发项。亲密度道具 / 外观道具在对应能力（亲密度引擎、换装页）就绪前，由运营在 DB 设 `status=0` 下架，前端自然不展示，无需前端特殊隐藏 |
| 外观重复购买 | **不可重复** | `outfit` 已解锁（`remainCount>0`）后购买按钮替换为「去换装」（换装页就绪、outfit 上架后生效） |
| 道具图标 | 优先 `iconUrl`，缺失时按 `category` 回退到内置 emoji | 避免空图标 |

### 2.3 不在本期范围

- 道具核销 / 使用的 UI（在其他页面）
- 亲密度增长的实际效果
- 优惠券、促销活动模块
- 订单中心 / 退款入口（后端已有 `/payment/orders`、`/payment/refund`，小程序消费留待后续）
- 暗色模式以外的主题（跟随全局）

---

## 3. 信息架构与页面结构

单页面 `pages/backpack`，自上而下：

```
导航栏（返回 ‹  我的背包）
├─ 我的持有 · 概览条（金色玻璃态）
│    └─ 持有道具 chips：换职业券 ×3 · 玫瑰 ×12 · 连衣裙 ✓ …
├─ 分类：重塑命运（宋体标题）
│    └─ 道具卡片 × N（换职业券 / 换小任性券 / 换声音券 …）
├─ 分类：声音
│    └─ 道具卡片（声音克隆额度 …）
├─ 分类：外观
│    └─ 道具卡片（连衣裙 …）
├─ 分类：亲密度礼物
│    └─ 道具卡片（玫瑰花 / 奶茶 / 挚爱钻戒 …）
└─ （浮层）购买确认面板
```

分类显示顺序固定为：`重塑命运 → 声音 → 外观 → 亲密度礼物`（不由后端 `sort` 决定大类顺序；大类内按 `sku.sort` 升序）。分类 → 中文标签映射：

| `category` | 标签 |
|---|---|
| `consumable_change` | 重塑命运 |
| `voice_quota` | 声音 |
| `outfit` | 外观 |
| `intimacy` | 亲密度礼物 |

---

## 4. 组件拆分

页面内部组件（小程序自定义组件或页面内 `template`，实现时择一；优先 `template` 以保持简单）：

| 组件 | 职责 |
|---|---|
| `holder-banner` | 「我的持有」概览条，渲染已拥有道具的 chips |
| `category-section` | 分类标题（宋体 + 圆点）+ 该分类下的卡片列表 |
| `item-card` | 单张道具卡片：图标 / 名称 / 说明 / 持有徽标 / 价格 / CTA |
| `purchase-sheet` | 购买确认底部面板：说明 / 数量步进器 / 合计 / 微信支付按钮 |

每个组件接收明确 props，状态由页面统一持有（容器/展示分离）。`item-card` 的渲染完全由传入的合并对象决定，自身不发请求。

---

## 5. 数据契约（后端已实现，无改动）

### 5.1 接口

| 方法 | 路径 | 鉴权 | 用途 |
|---|---|---|---|
| GET | `/item/skus?category=` | anon | 列出全部有效 SKU（不传 `category` 即全部） |
| GET | `/item/inventory` | oauth2 | 当前用户库存 |
| POST | `/payment/order` | oauth2 | 统一下单，返回 `prepayParams` |
| GET | `/payment/order/{outTradeNo}` | oauth2 | 查询订单状态（轮询履约结果） |
| POST | `/payment/order/{outTradeNo}/cancel` | oauth2 | （可选）取消未支付订单 |

均经 `utils/request.js`，自动附加 `Bearer ${token}`，上下文 `/xiaozhi`。响应信封 `{code, msg, data}`，`code===0` 为成功。

### 5.2 数据结构

```
ItemSkuVO {
  id: Long
  skuCode: string          // occupation_change / soul_quirk_change / voice_change /
                           // voice_clone_quota / outfit_* / rose / milktea / diamond_ring …
  skuName: string
  category: string         // consumable_change / voice_quota / outfit / intimacy
  priceFen: Long           // 原价(分)
  promoPriceFen: Long?     // 促销价(分)，可空
  attributes: string?      // JSON 字符串，如 {intimacy_delta:5}
  iconUrl: string?
  description: string?
  sort: int
}

UserItemVO {
  skuCode: string
  skuName: string
  category: string
  totalCount: int          // 累计获得
  usedCount: int           // 累计消耗
  remainCount: int         // 剩余可用
}

// POST /payment/order 请求体
CreateOrderDTO {
  productType: "ITEM"      // 固定
  productRefId: Long       // = ItemSkuVO.id
  quantity: int            // 默认 1
}

// 返回
PrepayVO {
  outTradeNo: string
  amountFen: Long          // 服务端权威金额（防篡改）
  payChannel: string
  prepayParams: Map<string,string>  // 直接喂 wx.requestPayment
}

// GET /payment/order/{outTradeNo}
OrderVO {
  outTradeNo, status, fulfilledAt, …
  // status: 0待支付 1已支付 2已发货 3已取消 4已退款 5已超时
}
```

### 5.3 前端合并模型

加载后前端把目录与库存合并成渲染模型：

```
DisplayItem = ItemSkuVO & {
  remainCount: int          // 来自 inventory，缺省 0
  effectivePriceFen: Long   // promoPriceFen ?? priceFen
  hasPromo: boolean         // promoPriceFen 非空且 < priceFen
}
```

合并方式：以 `skuCode` 为键，将 `inventory` 转成 `{[skuCode]: UserItemVO}` 映射后回填 `remainCount`。

---

## 6. 数据流

### 6.1 加载

```
onLoad / 下拉刷新
  └─ Promise.all([ GET /item/skus, GET /item/inventory ])
       ├─ 合并为 DisplayItem[]
       ├─ 按 category 分组 + 固定顺序排序
       ├─ 派生 holder-banner 的 chips（remainCount>0 的项）
       └─ setData 渲染
```

两个请求并行；任一失败进入错误态（见 §9）。`/item/inventory` 失败但 `/item/skus` 成功时，降级为「全部道具、持有量均为 0」并提示「库存加载失败，仅展示道具」。

### 6.2 购买

```
点 item-card 的 CTA
  └─ 打开 purchase-sheet（预填 quantity、effectivePrice）
点「微信支付 ¥X」
  └─ POST /payment/order { productType:"ITEM", productRefId: sku.id, quantity }
       ├─ 失败 → toast「下单失败，请重试」，保留面板
       └─ 成功 → 取 PrepayVO
            └─ wx.requestPayment(prepayParams)
                 ├─ errMsg 含 "cancel" → toast「已取消支付」（订单留待后端 15 分钟自动超时关单）
                 ├─ 失败 → toast「支付失败，请重试」
                 └─ 成功 → 进入履约轮询（见 6.3）
```

**金额权威性**：卡片与面板上显示的金额为前端预览（`effectivePriceFen × quantity`）；真实扣款以下单返回的 `PrepayVO.amountFen` 为准（服务端按 SKU 计算，前端不可覆盖，防篡改）。支付按钮文案用 `amountFen`。

### 6.3 履约轮询（关键）

微信支付成功回调时，履约（`ItemFulfillmentService.grant` → `user_item.remainCount += quantity`）在服务端回调链路中异步完成，可能滞后于 `wx.requestPayment` 的成功回调 1–2 秒。因此：

```
wx.requestPayment 成功
  └─ 关闭面板，显示「支付成功，道具入包中…」轻提示
  └─ 轮询 GET /payment/order/{outTradeNo}
       ├─ 每 1s 一次，最多 12 次（与「我的契约」`_waitOrderFulfilled` 一致）
       ├─ status===2（已发货）→ 成功：GET /item/inventory 刷新 + toast「购买成功」+ 更新 chips
       ├─ 超时仍未到 2（停在 1 已支付）→ 视为成功但延迟：toast「支付成功，道具稍后到账」，
       │    本地乐观 +1 remainCount（下次 onShow 再校正）
       └─ status∈{3,4,5} → 异常：toast 提示并引导联系客服
```

每次返回本页 `onShow` 都重新拉取 `/item/inventory`，保证库存最终一致。

---

## 7. 卡片状态机

单张 `item-card` 由 `(category, remainCount, effectivePriceFen, hasPromo)` 决定状态：

| 状态 | 条件 | 持有徽标 | CTA |
|---|---|---|---|
| 可购买 | `remainCount===0` 且非 outfit | — | `购买`（或显示 `¥价`） |
| 促销可购买 | `hasPromo` | — | `¥促销价 ~~¥原价~~` + `购买` |
| 已拥有·可叠加 | `remainCount>0`，非 outfit | `拥有 ×N`（金色） | `购买`（默认允许囤积） |
| 已解锁·永久 | `category==="outfit"` 且 `remainCount>0` | `已解锁`（绿色） | `去换装`（跳转伴侣/外观页） |
| 外观未拥有 | `category==="outfit"` 且 `remainCount===0` | — | `¥价` + `购买` |

`去换装` 的跳转目标为外观选择入口（后续页面）；本期内为占位跳转或 toast「敬请期待」也可，取决于外观页进度。

---

## 8. 交互细节

### 8.1 数量规则（purchase-sheet）

| `category` | 数量控件 | 范围 |
|---|---|---|
| `consumable_change`（券） | 固定 1，隐藏步进器 | 1 |
| `voice_quota`（克隆额度） | 步进器 | 1–9 |
| `intimacy`（礼物） | 步进器 | 1–99 |
| `outfit`（外观） | 固定 1（无步进器） | 1 |

`outfit` 未拥有时进入面板、数量固定 1；已解锁（`remainCount>0`）不进入面板，直接走「去换装」。

### 8.2 确认面板文案

复用契约浮窗的语气（温暖亲密）：

- 面板顶部一句浪漫引导，如「想给她一点小惊喜吗？」「为她挑一件心意吧」（可随机或按分类选）
- 说明区显示 `skuName` + 一句话用途（取 `description`）
- 底部备注：`券类道具数量固定为 1`（仅当 `category==="intimacy"` 时追加「亲密度效果将在后续版本生效」——该类道具上架前不会出现）

### 8.3 动效

- 面板上滑：`transform: translateY` + `cubic-bezier(0.16, 1, 0.3, 1)`，与 `settings.wxss` 的 `.contract-panel` 一致
- 卡片按下：`transform: scale(0.97)`，与 `.entry-card:active` 一致
- 购买成功后卡片持有徽标数字滚动 +1（轻量，可选）

### 8.4 入口

`settings.js` 的 `onBackpackTap` 实现：

```js
onBackpackTap() {
  wx.navigateTo({ url: '/pages/backpack/backpack' });
}
```

`app.json` 的 `pages` 数组新增 `"pages/backpack/backpack"`。

---

## 9. 空 / 加载 / 错误态

| 态 | 表现 |
|---|---|
| 加载中 | 3–4 张骨架卡片（玻璃态占位） |
| 目录为空 | 居中插画 + 「道具马上就来」 |
| 库存为空（新用户） | 正常展示全部道具；`holder-banner` 隐藏或显示「还没有道具，为她挑选一份心意吧」 |
| 网络错误 | 居中「加载失败」+「重试」按钮，重试触发 §6.1 |
| 下单/支付失败 | toast，不阻塞，面板保留可重试 |

---

## 10. 视觉规范（复用现有 token）

直接复用 `settings.wxss` / `DESIGN.md` 的取值，不在本页引入新主色：

| 用途 | 值 |
|---|---|
| 页面底 | `#f6f3f2` |
| 玻璃态卡片 | `rgba(255,255,255,.75)` + `backdrop-filter: blur(40rpx)` + `1rpx solid rgba(255,255,255,.8)` + `box-shadow: 0 4rpx 16rpx rgba(134,78,90,.06)` |
| 购买 CTA | `linear-gradient(135deg, #864e5a, #d4737a)` + 白字 + `box-shadow: 0 8rpx 24rpx rgba(134,78,90,.3)` |
| 持有身份色（徽标 / 概览条） | 金色 `#b8860b`，底 `rgba(184,134,11,.12)` |
| 价格 | `#864e5a` 加粗；原价划线 `#c2b4a2` |
| 已解锁徽标 | 绿 `#5a8a4e`，底 `rgba(90,138,78,.12)` |
| 分类标题 | 宋体 `"Songti SC","STSong","Noto Serif CJK SC",serif`，`#864e5a`，字距 `2rpx` |
| 底部面板 | `#fbf9f8`，`border-radius: 32rpx 32rpx 0 0`，上滑动效同 `.contract-panel` |
| 图标分类底色 | 重塑命运=粉系、声音=紫系、外观=粉系、礼物=金系（均 12%→4% 渐变） |

---

## 11. 暗色模式

遵循项目暗色规范（`miniprogram/CLAUDE.md`）：

- JS：`data: { darkMode: getTheme() }`，`onShow() { applyTheme(this); }`
- WXML：根容器 `class="backpack-container {{darkMode ? 'dark' : ''}}"`
- WXSS：根元素复合选择器 `.backpack-container.dark {}`，子元素后代选择器 `.dark .item-card {}`
- 取值对齐 `settings.wxss` 暗色：页面底 `#121220`、玻璃卡 `rgba(30,28,46,.85)`、主色不变、金色 `#daa520`、面板底 `#1e1c2e`

---

## 12. 文件清单与改动

### 新增（仅小程序）

```
main/miniprogram/pages/backpack/
├── backpack.wxml      # 页面结构 + 购买面板
├── backpack.wxss      # 样式（含暗色）
├── backpack.js        # 数据加载 / 合并 / 购买 / 轮询
└── backpack.json      # { "usingComponents": {}, "navigationBarTitleText": "我的背包" }
```

### 修改

```
main/miniprogram/app.json                      # pages 数组新增 pages/backpack/backpack
main/miniprogram/pages/settings/settings.js    # onBackpackTap 实现 navigateTo（若仍为空）
```

### 不改动

- 后端 `manager-api`：`item` / `payment` 模块已实现，接口已就绪，**无后端改动**。
- DB：道具 SKU 由后端迁移维护；本次新增的 `voice_change`（`202606151832.sql`）会自动出现在「重塑命运」分类，¥99。

---

## 13. 待评审确认项汇总（已确认）

1. **券类囤积**：✅ 允许囤积（已拥有的券仍可购买，每次固定 1 张）。
2. **亲密度道具**：✅ 由后端 `status` 控制。引擎未接入前，运营在 DB 设 `status=0` 下架，前端不展示；上架后再展示。
3. **「去换装」目标**：✅ 外观页未就绪前，运营将 `outfit` 道具设 `status=0` 下架，前端不展示「去换装」；换装页就绪 + outfit 上架后自然生效。
4. **取消支付**：✅ 不主动调 `/cancel`，依赖后端 15 分钟自动超时关单。

---

## 14. 验收标准

- [ ] 从「我」→「我的背包」可进入；返回键可回到「我」。
- [ ] 页面展示后端返回的全部有效道具，按 重塑命运/声音/外观/亲密度礼物 四类分段、类内按 `sort` 排序。
- [ ] 每张卡片含图标、名称、功能说明、价格（促销价正确划线）、持有量徽标。
- [ ] 「我的持有」概览条正确反映 `remainCount>0` 的道具。
- [ ] 购买流程：确认面板 → 微信支付 → 履约轮询 → 库存 +N、徽标与概览条刷新。
- [ ] 支付取消 / 下单失败 / 网络错误 有对应提示，不卡死。
- [ ] 新用户（库存空）页面正常，概览条降级文案。
- [ ] 暗色模式样式正确，与「我」页面一致。
- [ ] 金额以前端预览展示、以服务端 `amountFen` 实扣，篡改前端金额无效。
- [ ] 不引入新的主色；样式 token 与现有系统一致。
