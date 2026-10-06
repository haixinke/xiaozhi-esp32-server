# 共享明信片可行性调研

调研日期：2026-10-05
问题：蛋宝宝小程序中，AI 宠物主动发明信片（AI 生成照片 + 文字），用户转发给微信好友，好友免授权查看。做成共享网页可行吗？

## 结论

**能做到，但"小程序里把网页链接分享给好友"这个原生形态不存在。** 微信小程序的分享能力（onShareAppMessage / 朋友圈）只能产出小程序卡片或小程序单页，不能把外部 H5 链接分享成聊天里的网页卡片。

好友免授权查看这一点**没有任何障碍**：纯静态 H5 在微信内置浏览器可直接打开，无需任何微信授权；小程序公开页也无需登录即可渲染（wx.login 是静默的，且仅在需要 openid 时才调用）。

推荐实现（按优先级）：

1. **小程序卡片分享到公开明信片页**（主路径）——好友点卡片直接看，零授权、零额外域名配置，复用现有 share-invite.js 模式
2. **生成图片海报**（辅助路径）——canvas 渲染明信片存相册，发图给好友，传播性最好，完全无平台依赖
3. **纯 H5 + 复制链接**（仅当需要"网页"形态）——好友点链接在微信内置浏览器看，免授权，但需要已备案域名 + HTTPS，且用户需手动复制粘贴链接，体验割裂

## 需求拆解

| 需求点 | 关键约束 |
|---|---|
| 蛋宝宝主动发明信片 | 纯后端逻辑，无平台限制 |
| 照片 + 文字内容 | 照片需已生成（项目已有 AI 照片生成链路，见 docs/ai-photo-gen-architecture.html） |
| 做成共享网页 | 微信小程序无法直接分享网页链接，只能分享小程序卡片 |
| 转发给好友 | onShareAppMessage 转发卡片；或保存图片发送 |
| 好友免授权查看 | 静态内容展示无授权要求（小程序端与 H5 端均成立） |

## 官方文档事实核查

### 1. 小程序分享形态只能是小程序卡片

官方转发文档（onShareAppMessage）描述的产物是"转发卡片"——其他用户点击卡片**打开小程序**，从未提及支持外部网页链接分享。分享图片为页面截图或自定义图。

> "转发后生成'转发卡片'，其他用户点击卡片可打开小程序"

来源：<https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/share.html>

朋友圈同理，且更受限：打开后进入"小程序单页模式"，**无登录态**（wx.login 不可用）、不允许跳转其它页面、本地存储与普通模式不共用。

> "用户在朋友圈打开分享的小程序页面，并不会真正打开小程序，而是进入一个'小程序单页模式'的页面"
> "页面无登录态，与登录相关的接口，如 wx.login 均不可用"

来源：<https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/share-timeline.html>

含义：朋友圈单页模式只能展示静态明信片内容，不能引导登录或跳转——对"明信片"这种一次性展示内容反而够用。

### 2. 纯 H5 免授权查看：成立

网页授权机制的存在意义是获取用户身份（openid / 昵称头像），不是打开网页的前提。

> "如果用户在微信客户端中访问第三方网页，服务号可以通过微信网页授权机制，来获取用户基本信息"
> "snsapi_base：用来获取进入页面的用户的openid的，并且是静默授权"

来源：<https://developers.weixin.qq.com/doc/service/guide/h5/auth.html>

即：微信内置浏览器打开普通 H5 页面展示静态内容，不需要任何授权。需要拿 openid 才走网页授权（且仅服务号可用）。

前置条件（域名层）：

- 域名必须 ICP 备案，仅支持 HTTPS（有效证书、TLS 1.2+），不能用 IP 或 localhost
- 这些是"服务器域名"（request 合法域名）的规则；纯 H5 不经小程序打开时，技术上只需可公网访问，但要在微信生态里长期稳定（不被拦截、可被微信识别为安全链接），备案域名是事实要求

来源：<https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html>

### 3. 小程序 web-view：需业务域名，个人主体不可用

若要在小程序内嵌 H5（明信片页用网页实现再嵌进小程序）：

> "个人类型的小程序暂不支持使用。"
> "其它网页需登录小程序管理后台配置业务域名。"
> "网页内 iframe 的域名也需要配置到域名白名单。"

来源：<https://developers.weixin.qq.com/miniprogram/dev/component/web-view.html>

业务域名还需在小程序管理后台上传校验文件到域名服务器。含 web-view 的页面不能发起分享。

### 4. URL Link / URL Scheme：是"拉起小程序"的链接，不是网页

URL Link 适合短信、邮件、网页、微信内**拉起小程序**。关键限制：

> "只能生成已发布的小程序的 URL Link。"
> "目前仅针对国内非个人主体的小程序开放"
> "最长有效期为30天" / "最长间隔天数为30天"
> "取消 URL Link 一人一链的限制，支持同一条连接被多名用户访问"（2023-12-19 调整后）

来源：<https://developers.weixin.qq.com/miniprogram/dev/server/API/qrcode-link/url-link/api_generateurllink.html>

含义：URL Link 发到聊天里，好友点开是**进入小程序**（可用作免分享面板的兜底入口），但产物仍是小程序页不是网页，且 30 天过期、要求已发布 + 非个人主体。对本需求不推荐。

### 5. 小程序公开页免登录访问：成立

好友点分享卡片进入小程序页面，页面渲染本身不需要登录。wx.login 获取 code 换 openid 的流程对用户无感知（无授权弹窗），仅在页面需要用户身份时才调用。明信片公开页可以完全不调用 wx.login，用 URL 里的明信片 ID 查后端渲染。

> "调用 wx.login() 获取临时登录凭证code，并回传到开发者服务器"
> "之后开发者服务器可以根据用户标识来生成自定义登录态"

来源：<https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html>

注意：小程序正式发布后要求网络请求域名全部配置为 HTTPS 合法域名（已备案），蛋宝宝生产域名已满足。

## 四条路径对比

| 路径 | 好友免授权 | 好友看到什么 | 前置条件 | 主要缺点 |
|---|---|---|---|---|
| A. 小程序卡片分享 | 是 | 小程序公开页（可做全屏明信片视觉） | 无新增（复用现有分享链路） | 好友需安装/加载小程序（微信自动拉起，几乎无感） |
| B. 生成图片海报 | 是（无任何平台交互） | 一张图 | 无新增（canvas + 保存相册） | 失去跳转引导；文字排版定死在图里 |
| C. 纯 H5 + 复制链接 | 是 | 微信内置浏览器网页 | 备案域名 + HTTPS 静态托管 | 用户须手动复制粘贴链接，聊天里显示为普通链接卡片，体验最差 |
| D. URL Link | 是（点开即小程序） | 小程序页面 | 小程序已发布、非个人主体 | 30 天过期；本质仍是拉起小程序；生成有配额 |

## 推荐方案

**主路径 A + 辅助路径 B 组合**，放弃"网页"执念：

1. 后端（manager-api）新增明信片资源表：ID、图片 URL、文案、生成时间，配一个匿名可查的公开接口 `GET /postcard/{id}`（无鉴权，仅返回公开字段）
2. 小程序新增明信片展示页（独立页面，路由带 id），页面不调 wx.login，纯展示照片 + 文字 + 品牌落款
3. 用户侧分享：onShareAppMessage 返回 `{ path: '/pages/postcard/view?id=xxx', imageUrl: 明信片图 }` ——好友点卡片直接进明信片页
4. 同一页面提供"保存为图片"按钮（canvas 合成），用户也可直接发图
5. 蛋宝宝"主动发送"：沿用现有推送/消息触达机制，用户在小程序内收到明信片通知，点开即见，再由用户决定转发

若未来确实需要网页形态（例如投放站外、二维码扫码场景），再走路径 C：静态 H5 托管在已备案域名下，内容接口复用同一个公开明信片接口。

## 前置条件清单

- [x] 已备案生产域名 + HTTPS（项目已有）
- [ ] 明信片公开接口（后端新增，匿名只读）
- [ ] 明信片展示页 + 分享配置（小程序新增）
- [ ] 分享图片素材（AI 生成图直接用作卡片缩略图）
- [ ] 若走海报：canvas 合成工具函数
- [ ] 若走 H5：静态页面 + 后端托管（暂缓）

## 审核与合规注意

- 转发规范：官方明确"不应该成为一个诱导或强制行为，如转发后才能解锁某项功能等"——蛋宝宝发明信片是主动赠送，不与转发行为挂钩，合规
- 明信片文案由 LLM 生成，属 UGC 范畴，公开页需注意内容审核兜底（至少关键词过滤，避免 AI 生成不当文案公开展示）
- 公开接口无鉴权，注意：ID 用不可枚举的随机串（防爬全量明信片）、接口限流、返回字段最小化（不含用户任何个人信息）
- 照片若含儿童用户相关内容，公开分享前应经用户确认（现有照片生成流程已由用户触发，保持"用户主动转发"而非系统直发好友的设计）

## 来源汇总

| 事实 | 来源 |
|---|---|
| 分享仅产小程序卡片 | developers.weixin.qq.com/miniprogram/dev/framework/open-ability/share.html |
| 朋友圈单页模式限制 | developers.weixin.qq.com/miniprogram/dev/framework/open-ability/share-timeline.html |
| H5 静态展示无需授权 | developers.weixin.qq.com/doc/service/guide/h5/auth.html |
| 域名 HTTPS/ICP 备案要求 | developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html |
| web-view 业务域名 + 个人主体限制 | developers.weixin.qq.com/miniprogram/dev/component/web-view.html |
| URL Link 发布/主体/有效期限制 | developers.weixin.qq.com/miniprogram/dev/server/API/qrcode-link/url-link/api_generateurllink.html |
| wx.login 静默无授权弹窗 | developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html |
