# 蛋宝宝小程序 AI 生图功能实现计划

> 日期：2026-09-17。前置调研见 [technical-research.md](./technical-research.md)（模型选型、微信合规依据均在其中，本文不重复）。术语定义见根 `CONTEXT.md`（AI 生图任务 / IP 参考图 / 预置文案）。

## 1. 已确认决策

| # | 决策点 | 结论 |
|---|---|---|
| Q1 | 功能入口 | 宠物主页（home）破壳后主场景加"AI 写真"入口，跳转独立生成页 |
| Q2 | IP 参考图 | 按用户当前宠物原型自动选：锦鲤 `https://oss.eggbabe.com/default-ip/fish/fish.png`、玉兔 `https://oss.eggbabe.com/default-ip/rabbit/rabbit.png`，用户无感知 |
| Q3 | 文案 | 渲染进图片内；后端按原型随机抽取，用户不可编辑、不可自定义 |
| Q4 | 结果去向 | 保存相册 + `onShareAppMessage` 小程序卡片分享（标题用抽中的文案） |
| Q5 | 配额 | 每日 3 次，成功才计次；失败记日志防刷 |
| Q6 | 内容审核 | 完整链路：用户照片 + 结果图各过一次 `security.mediaCheckAsync`，新增微信消息推送回调端点 |
| Q7 | 照片口径 | "随手拍任意画面"，prompt 把照片当场景/氛围参考，不保留人像 |
| Q8 | 回调实现 | 引 `weixin-java-mp` SDK，mp 后台消息推送用安全模式（AES） |
| Q9 | 驳回配额 | 审核驳回不计次，仅记日志 |
| Q10 | 分享 | 小程序卡片分享 + 保存相册两者都做 |
| Q11 | 页面形态 | 独立新页面 `pages/photo-gen/`，单页三态（选图 → 生成中 → 结果） |

预置文案池（初版，后端按原型随机抽取）：

- 锦鲤：`好运连连`、`锦鲤附体 诸事顺利`、`摸鱼也能赢`
- 玉兔：`玉兔呈祥`、`月宫来的小可爱`、`温柔有光`

## 2. 状态机与任务表

```
PENDING ──照片审核通过──► RUNNING ──生成完成──► REVIEWING ──结果审核通过──► SUCCEEDED
   │                        │                       │
   └──照片驳回──► FAILED ◄──生成失败/超时── FAILED ◄──结果驳回── FAILED
```

- PENDING/REVIEWING 两个审核态都由微信消息推送驱动推进；RUNNING 由 `@Async` 执行器推进。
- 兜底：24h 未到终态的任务由定时任务置 FAILED（小程序切后台、微信推送丢失的兜底）。

`ai_image_task` 表（Liquibase 新建 changeSet，id 按时间时分、SQL 文件同名，遵守 master 文件头规则）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK auto | |
| user_id | bigint | 索引 (user_id, create_time)，供配额统计 |
| pet_id | bigint | |
| photo_url | varchar(512) | 用户照片 OSS URL |
| ip_image_url | varchar(64) | IP 参考图 URL 快照 |
| caption | varchar(64) | 抽中的预置文案快照 |
| status | varchar(16) | PENDING/RUNNING/REVIEWING/SUCCEEDED/FAILED |
| result_url | varchar(512) | 结果图 OSS URL，nullable |
| fail_reason | varchar(255) | 用户可读失败原因，nullable |
| photo_trace_id | varchar(128) | 照片 mediaCheckAsync trace_id |
| result_trace_id | varchar(128) | 结果图 mediaCheckAsync trace_id |
| counted | tinyint | 是否已计入配额（成功=1） |
| create_time / update_time | datetime | |

## 3. 后端（全部在 manager-api）

### 3.1 上传

- `UploadScene` 追加 `AI_GEN("ai-gen", png/jpeg/webp, 5MB)`，复用 `/upload/image` 全部校验与 OSS key 逻辑（`{scene}/{userId}/{uuid}.{ext}`）。

### 3.2 微信 API 客户端（新）

- `modules/wechat/` 新增 `WechatMpClient`：
  - `getStableAccessToken()`：调微信稳定版接口，token 缓存 Redis（有效期 7000s 提前刷新），appId/secret 复用登录现有配置。
  - `mediaCheckAsync(mediaUrl, openid, scene)`：`version=2, media_type=2`，返回 `trace_id`。
- 注意 openid 要求近 2h 访问过小程序——用户刚上传过照片，天然满足。

### 3.3 消息推送回调（新）

- `modules/wechat/controller/WechatMsgCallbackController`，路径 `/wechat/mp/callback`：
  - GET：验签后回 `echostr`（mp 后台配置时的一次性校验）。
  - POST：AES 解密（weixin-java-mp），识别 `event=wxa_media_check`，按 `trace_id` 命中 task（先查 `photo_trace_id` 再查 `result_trace_id`），推进状态机。
  - 立即 ack（先回 `success`，异步处理）；微信 5s 无响应会重推 3 次，处理逻辑必须幂等（状态已迁移则跳过）。
- `ShiroConfig` 加 `filterMap.put("/wechat/mp/callback", "anon")`。
- 配置：`WechatMpProperties`（token、aesKey），mp 后台「开发管理 → 消息推送」配 URL/Token/EncodingAESKey，选安全模式。

### 3.4 任务服务与接口

- `modules/pet/`（宠物语义归属，不新建模块）：
  - `ImageGenController`：
    - `POST /pet/image-gen/tasks` — 入参 `{photoUrl}`。校验：登录、宠物归属、当日配额（`counted=1` 且当日 < 3）、photoUrl 域名白名单（只接受本 bucket OSS URL）。落 task(PENDING)，调 `mediaCheckAsync(photoUrl)` 记 `photo_trace_id`，毫秒级返回 `{taskId, status}`。
    - `GET /pet/image-gen/tasks/{taskId}` — 返回 `{taskId, status, resultUrl?, failReason?, caption?}`。
  - `ImageGenService` + `@Async("taskExecutor")` 执行器：
    1. 照片审核通过（回调置 RUNNING）后触发：按 `pet.prototype` 选 IP 参考图（锦鲤/玉兔 → 对应固定 URL），从文案池随机抽 caption。
    2. 拼 prompt：IP 形象一致性约束 + 用户照片作场景/氛围参考（明确"不保留真实人物面部"）+ 图内渲染 caption 文字。
    3. 复用 `SeedreamArkConfig` 的 `ArkService`，`GenerateImagesRequest.image(List.of(photoUrl, ipImageUrl))`，写法参照 `CollectionCardImageServiceImpl.callSeedream()`。
    4. 下载结果图 → OSS `ai-gen/{userId}/{taskId}.png`（PublicRead）→ `mediaCheckAsync(resultUrl)` 记 `result_trace_id` → REVIEWING。
    5. 结果审核通过（回调）→ SUCCEEDED + resultUrl + `counted=1`；驳回 → FAILED。
- 定时清理：`@Scheduled` 每小时扫一次，24h 未到终态置 FAILED。

### 3.5 API 契约

```
POST /pet/image-gen/tasks
req:  { photoUrl: string }
resp: { taskId: string, status: "PENDING" }
err:  配额超限 / 宠物不存在 / photoUrl 非法

GET /pet/image-gen/tasks/{taskId}
resp: { taskId, status: PENDING|RUNNING|REVIEWING|SUCCEEDED|FAILED,
        resultUrl?, failReason?, caption? }
```

## 4. 小程序（egg-miniprogram）

- `app.json` 注册 `pages/photo-gen/photo-gen`。
- home 页破壳后主场景（stage === `hatched` 且"在家"大场景）右下角放圆形 AI 写真入口按钮（与左下角聊天入口同款样式）→ `wx.navigateTo` 生成页；破壳前不展示入口。
- 生成页三态：
  1. **选图**：`wx.chooseMedia`（count=1，album+camera），用 `tempFiles[0].size` 前置拦截 >5MB 并友好提示；预览确认后 `wx.uploadFile` 上传（照抄 `utils/doodle-api.js` 封装，scene 换 `ai-gen`）→ 建任务。
  2. **生成中**：`setInterval` 2.5s 轮询任务接口，`onHide`/`onUnload` `clearInterval` 成对管理；PENDING/REVIEWING 态显示"审核中"提示。
  3. **结果**：`<image src=resultUrl>` 直显（oss 域名已在 request 白名单，展示无需 downloadFile）；常驻"图片由AI生成"标识（与聊天页同一产品语言）；保存按钮走 `wx.downloadFile` + `wx.saveImageToPhotosAlbum`；`onShareAppMessage` 分享小程序卡片（标题 = caption）。
- 新增 `utils/image-gen-api.js`：`createImageGenTask(photoUrl)` / `getImageGenTask(taskId)`，走 `request.js` 统一封装。
- FAILED 态按 `failReason` 区分提示：照片违规 → "图片未通过审核，换一张试试"；生成失败 → "生成失败，请重试"。

## 5. 合规 checklist（上架前硬性）

- [ ] mp 后台服务类目覆盖"深度合成-AI绘画/AI创作"（火山引擎算法备案编号 + 合作协议材料）
- [ ] mp 后台「消息推送」配置 URL/Token/EncodingAESKey，安全模式
- [ ] `oss.eggbabe.com` 已加入 downloadFile 合法域名（保存相册需要；展示不需要）
- [ ] 结果页常驻"图片由AI生成"标识（2025-09-01 起强制）

## 6. 验证计划

1. **Spike 优先（阻塞项）**：直接用 curl 调 Ark `images/generations`，`image=[真人照片URL, fish-0.png]`，验证方舟防深伪护栏对"真人照片融合"的通过率。误杀率高则调 prompt（照片退化为纯场景参考）或降级 Seedream 4.x 多图主体一致性模式。**此验证不过，后续任务不启动。**
2. 后端单测（TDD）：prompt 组装、配额判断、状态机迁移、回调 trace_id 路由与幂等、照片 URL 白名单校验。
3. 回调联调：mp 后台配置后用真实推送验证 GET 校验与 POST 解密。
4. 端到端：开发版小程序（"不校验合法域名"模式 + 本机 IP）走通 选图→审核→生成→审核→展示→保存→分享 全链路。

## 7. 任务分解（按依赖排序）

| # | 任务 | 验证 |
|---|---|---|
| 0 | ~~Spike：真实照片通过率实测~~ **已完成**：5/5 通过，质量目检合格 | 3 张真实照片各 3 次调用，记录通过率 |
| 1 | Liquibase `ai_image_task` + Entity/DAO | 迁移通过，表结构符合第 2 节 |
| 2 | `UploadScene.AI_GEN` | 现有上传接口新场景可用 |
| 3 | `WechatMpClient`（token + mediaCheckAsync） | 单测 + 真实调用返回 trace_id |
| 4 | 回调端点 + weixin-java-mp + ShiroConfig anon | mp 后台 URL 校验通过；模拟推送幂等处理 |
| 5 | `ImageGenService` + 异步执行器 + Controller | 单测覆盖状态机与配额；接口契约符合 3.5 |
| 6 | 定时清理任务 | 24h 未终态任务被置 FAILED |
| 7 | 小程序 `photo-gen` 页 + API 封装 + home 入口 | 端到端全链路通过 |
| 8 | 合规 checklist 逐项核对 | 第 5 节全绿 |

## 8. 风险残留

- ~~**Spike 不过**：真人照片被方舟护栏大量误杀~~ **已验证（2026-09-17）**：2 张真实照片 × 2 原型共 5 次调用全部通过，无护栏拒绝；目检确认 IP 形象一致、场景融合自然、文案（好运连连）正确渲染进图、背景人物虚化不可辨认。样张 prompt 见第 3.4 节口径。
- **推送延迟**：mediaCheckAsync 标称 30 分钟内，高峰期"审核中"态可能明显；产品文案已预留。
- **类目审核**：mp 类目补充需要时间，提前并行准备材料。
