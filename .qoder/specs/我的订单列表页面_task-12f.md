# 我的订单列表页面实现计划

## Context

用户在微信小程序"我的"页面（settings页面）中需要一个订单列表入口，用于查看所有消费记录（订阅套餐订单 + 道具购买订单）。当前小程序有购买功能（契约订阅、道具购买），但用户无法回溯历史订单记录。

后端 `GET /payment/orders` 接口已存在，返回 `List<OrderVO>`，但 OrderVO 缺少商品名称字段（商品名存储在 entity 的 productSnapshot JSON 中，未暴露给前端）。前端需要新建订单列表页面，并在"我的"页面增加入口。

## 实现方案

### 任务1：后端 - OrderVO 增加商品名称字段

**文件**: `main/manager-api/src/main/java/xiaozhi/modules/payment/vo/OrderVO.java`

改动：
1. 新增 `productName` 字段
2. 新增 import: `cn.hutool.json.JSONObject`, `cn.hutool.json.JSONUtil`, `org.apache.commons.lang3.StringUtils`
3. 在 `toVO()` 方法末尾增加快照解析逻辑，调用新增的 `extractProductName()` 私有方法
4. 新增 `extractProductName(snapshot, productType)` 私有静态方法：解析 productSnapshot JSON，SUBSCRIPTION 取 `planName`，ITEM 取 `skuName`，解析失败回退为"订阅套餐"/"道具"

参考已有解析模式（PaymentOrderServiceImpl.java 第89-98行 createOrder 中的快照解析逻辑）。

**无需修改**: `PaymentOrderServiceImpl.myOrders()`（循环调用 toVO，自动生效）、`PaymentController`（接口签名不变）。

### 任务2：前端 - 新建订单列表页面

**新建4个文件**:
- `main/miniprogram/pages/orders/orders.json` — 页面配置（导航栏标题"我的订单"，开启下拉刷新）
- `main/miniprogram/pages/orders/orders.wxml` — 页面结构
- `main/miniprogram/pages/orders/orders.wxss` — 页面样式
- `main/miniprogram/pages/orders/orders.js` — 交互逻辑

#### 页面结构（WXML）
- **Tab分段器**：全部 / 订阅套餐 / 道具（3个Tab，客户端筛选，切换零延迟）
- **订单卡片**：三段式布局
  - 头部：商品名称 + 状态标签（颜色区分：待支付-橙、已支付/已发货-绿、已取消/已超时-灰、已退款-红）
  - 主体：类型标签（订阅套餐-粉、道具-金）+ 金额（¥XX.XX）+ 数量（×N，仅>1时显示）
  - 底部：订单号 + 下单时间（YYYY-MM-DD HH:mm）
- **骨架屏**：4个占位卡片（加载中）
- **错误状态**：加载失败 + 重试按钮
- **空状态**：根据当前Tab动态文案（暂无订单/暂无订阅订单/暂无道具订单）
- 仅展示，不含操作按钮

#### 交互逻辑（JS）
- `onLoad`: applyTheme + loadOrders
- `onShow`: applyTheme（不重复拉数据）
- `onPullDownRefresh`: 重新拉取，finally 中 stopPullDownRefresh
- `loadOrders()`: 调用 `GET /payment/orders`，map 装饰每条订单
- `_decorate(raw)`: 格式化金额（分→元，整元不显示小数）、状态码→文案+样式类、类型→文案+样式类、时间格式化
- `onTabTap(e)`: 切换 activeTab，调用 `_applyFilter()`
- `_applyFilter()`: 按 activeTab 过滤 allOrders → filteredOrders
- `onRetry()`: 重新 loadOrders
- 状态映射表 STATUS_MAP: `{0:待支付, 1:已支付, 2:已发货, 3:已取消, 4:已退款, 5:已超时}`
- 类型映射表 TYPE_MAP: `{SUBSCRIPTION:订阅套餐, ITEM:道具}`

#### 样式设计（WXSS）
- 复用 Ethereal Companion 设计系统（与 backpack.wxss 一致）
- Tab分段器：瓷白玻璃态容器 + 激活项柔粉渐变背景
- 订单卡片：`rgba(255,255,255,0.78)` + `backdrop-filter: blur(40rpx)` 瓷白玻璃态
- 状态标签6色方案
- 深色模式完整适配：背景 `#121220`，卡片 `rgba(30,28,46,0.85)`

### 任务3：前端 - settings页面增加入口

**修改3个文件**:

#### settings.wxml
在 entry-row 内 `onCompanionTap` 卡片后，新增第4个入口卡片：
```xml
<view class="entry-card" bindtap="onOrdersTap">
  <text class="entry-title">我的订单</text>
  <text class="entry-desc">消费记录</text>
</view>
```

#### settings.wxss
将 entry-row 从单行3列改为2x2网格：
- `.entry-row`: 增加 `flex-wrap: wrap`
- `.entry-card`: `flex: 1` → `flex: 0 0 calc(50% - 10rpx)`

#### settings.js
新增跳转方法（与 onBackpackTap 平级）：
```javascript
onOrdersTap() {
  wx.navigateTo({ url: '/pages/orders/orders' });
},
```

### 任务4：注册新页面

**修改文件**: `main/miniprogram/app.json`

在 pages 数组末尾（`"pages/voice-call/voice-call"` 之后）新增：
```json
"pages/orders/orders"
```

## 关键文件清单

| 操作 | 文件 |
|------|------|
| 修改 | `main/manager-api/src/main/java/xiaozhi/modules/payment/vo/OrderVO.java` |
| 新建 | `main/miniprogram/pages/orders/orders.json` |
| 新建 | `main/miniprogram/pages/orders/orders.wxml` |
| 新建 | `main/miniprogram/pages/orders/orders.wxss` |
| 新建 | `main/miniprogram/pages/orders/orders.js` |
| 修改 | `main/miniprogram/app.json` |
| 修改 | `main/miniprogram/pages/settings/settings.wxml` |
| 修改 | `main/miniprogram/pages/settings/settings.wxss` |
| 修改 | `main/miniprogram/pages/settings/settings.js` |

## 验证方案

1. **后端验证**：启动 manager-api，调用 `GET /payment/orders`（需Bearer Token），确认返回数据中包含 `productName` 字段且值正确（订阅订单显示planName，道具订单显示skuName）
2. **前端验证**：
   - 打开小程序"我的"页面，确认入口区显示2x2网格（我的契约/我的背包/我的女友/我的订单）
   - 点击"我的订单"进入订单列表页
   - 验证Tab切换（全部/订阅套餐/道具）筛选正确
   - 验证订单卡片信息完整（商品名、状态标签颜色、金额、数量、订单号、时间）
   - 验证下拉刷新正常
   - 验证深色模式切换后样式正确
   - 验证空状态和错误重试
3. **兼容性验证**：确认 settings 页面现有功能（契约购买/背包/女友资料/深色模式/信件弹窗）不受影响
