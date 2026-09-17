# 蛋宝宝小程序 AI 生成图片功能技术调研

> 调研日期：2026-09-17。目标：用户拍摄/上传一张照片，服务端以"预置宠物 IP 图 + 文案"为约束调用图片生成大模型，产出合成图返回给用户。

## 1. 结论摘要

推荐方案：**全部编排放在 manager-api（Java），复用现有火山引擎 Seedream 链路，同步提交 + 数据库任务状态 + 小程序轮询**。

1. 供应商选**火山引擎 Ark Seedream**（当前配置 `doubao-seedream-5-0-lite-260128`）：官方文档确认 `image` 字段支持多图参考（5.0-lite/4.5/4.0 最多 14 张）[2]，0.22 元/张刊例价 [3]，境内 endpoint 天然满足小程序合规；仓库已集成 Ark SDK 2.0.18 且 `GenerateImagesRequest.image` 就是 `List<String>`，多参考图无需升级依赖。
2. 参考图 = 用户照片（新增 `UploadScene` 场景走现有 `/upload/image` 入 OSS）+ 预置宠物 IP 图（复用 `oss.eggbabe.com/cards-bg/` 现有素材与解析逻辑）。
3. 调用模式：**异步任务**。同步等 5~30s 会顶到小程序 `wx.request` 超时与网关限制，且 Seedream 排队时波动大；落一张 `ai_image_task` 任务表，`POST` 立即返回 taskId，小程序轮询 `GET`（小程序端已有 envelope + 401 重登的请求封装可直接用）。
4. 生成完成后结果图上传阿里云 OSS（复用 `OssService`），返回 `https://oss.eggbabe.com/...` URL，`<image>` 直显无需 downloadFile 域名。
5. 合规：用户上传图先过 `security.imgSecCheck`，AI 生成图展示前过 `security.mediaCheckAsync`（异步，适合生成链路）；详见第 4 节。
6. 生成失败不阻塞主流程（参考现有收藏卡"生成失败仅记日志"的容错风格），配额参考 `chat.quota` 先例做每日次数限制。

## 2. 推荐架构

### 2.1 流程

```
小程序                        manager-api (Java)                     外部服务
──────                        ──────────────────                     ────────
chooseMedia 拍照/选图
  │ tempFilePath
  ▼
wx.uploadFile ──────────────► POST /upload/image (scene=photo_gen)
Bearer token                  ImageUploadService 校验(类型/5MB)
                              UploadScene.PHOTO_GEN → OSS
                              ◄──────── 返回用户照片 URL
  │
  ▼ imgSecCheck(用户照片)
security.imgSecCheck ◄────►  （可先不调，后端代传 URL 校验，见 4.3）
  │
  ▼
POST /pet/image-gen/tasks
  { photoUrl, petId, caption? }        校验:登录/配额/pet归属
                              建 ai_image_task(PENDING) → 返回 taskId
                              ◄──────── { taskId }
  │
  │ 轮询 GET /pet/image-gen/tasks/{taskId}（2~3s 间隔）
  ▼
                              @Async 执行:
                              1. 取宠物原型 → 预置 IP 参考图 URL
                              2. 拼 prompt(IP一致性约束 + 文案 + 照片场景)
                              3. Ark images/generations
                                 image=[photoUrl, ipRefUrl]  ← 多图参考
                              4. 下载结果 → OSS ai-gen/{userId}/{taskId}.png
                              5. mediaCheckAsync(结果图) → PASSED 才可见
                              6. 回写 task: SUCCEEDED + resultUrl
  ◄──────── { status, resultUrl? }
  ▼
<image src=resultUrl> 展示 / 保存相册 / 分享
```

### 2.2 组件落点（全部在 manager-api）

| 组件 | 位置（建议） | 说明 |
|---|---|---|
| 任务表 `ai_image_task` | Liquibase changelog + `modules/pet`（或新 `modules/imagegen`） | id/user_id/pet_id/photo_oss_key/status/result_url/fail_reason/create_time |
| `ImageGenController` | `modules/pet/controller` | `POST /pet/image-gen/tasks`、`GET /pet/image-gen/tasks/{id}` |
| `ImageGenService` | `modules/pet/service` | 编排：参数校验 → 建任务 → 异步执行；prompt 模板组装 |
| Seedream 调用 | 复用 `SeedreamArkConfig` 的 `ArkService` Bean | `image(List.of(photoUrl, ipRefUrl))`；参考 `CollectionCardImageServiceImpl.callSeedream()` 写法 |
| OSS | 复用 `OssService` + `ImageUploadService`（新增 `UploadScene.PHOTO_GEN`） | 用户照片与结果图都入阿里云 OSS |
| 异步执行 | 复用 `AsyncConfig` 的 `taskExecutor` | 参考 `CollectionCardGenerationListener` 的 `@Async` 模式 |
| 配额 | 参考 `chat.quota` 配置先例 | 免费用户每日 N 次，防成本失控 |

### 2.3 API 契约草案（envelope 统一 `{code,data,msg}`，与 `request.js` 现状一致）

```
POST /pet/image-gen/tasks
req:  { photoUrl: string, petId: string, caption?: string }
resp: { taskId: "178...", status: "PENDING" }
err:  100xx 配额超限 / 参数非法 / 图片未过审

GET /pet/image-gen/tasks/{taskId}
resp: { taskId, status: PENDING|RUNNING|SUCCEEDED|FAILED, resultUrl?: string, failReason?: string }
```

- 创建接口只做"落任务"，不等待生成，毫秒级返回。
- 轮询由小程序 `setInterval` 驱动，页面卸载时清除；后端可对轮询做简单频控。
- `caption`（文案）可选：不传时后端用宠物昵称/性格等默认文案拼 prompt。

### 2.4 为什么不是同步调用

- 图片生成普遍 5~30s 且受排队影响波动；同步挂在 HTTP 请求上会被小程序 `wx.request` 超时、网关 idle timeout 双重挤压（微信侧超时下限见 4.1）。
- 现有代码已有"事件 + `@Async` 生成、客户端刷新拉结果"的先例（收藏卡链路），轮询任务表只是把它显式化，心智成本一致。
- 若将来 Seedream 5.0 Pro（异步 task_id 模式）接入，任务表结构不用改。

## 3. 候选图片模型对比

场景：1 张用户照片 + 1 张预置宠物 IP 参考图 + 中文短文案 → 1 张融合图。多参考图支持是硬指标。

| 维度 | 火山引擎 Seedream（推荐） | 阿里通义万相 | OpenAI gpt-image-1/1.5 |
|---|---|---|---|
| 接入端点 | `POST https://ark.cn-beijing.volces.com/api/v3/images/generations` [1] | DashScope `multimodal-generation/generation` [6][7] | `POST https://api.openai.com/v1/images/edits` [12] |
| 适用模型 ID | `doubao-seedream-5-0-lite-260128`（仓库现配）、`doubao-seedream-4-5-251128`、`doubao-seedream-4-0-250828`、`doubao-seedream-5-0-pro-260628` [2][4] | `wan2.6-image`、`wan2.7-image`、`wan2.7-image-pro` [5] | `gpt-image-1`、`gpt-image-1.5` [11] |
| 多参考图 | `image` 支持 string 或 array；5.0-lite/4.5/4.0 官方文档写明**最多 14 张** [2]；5.0 Pro 官方渠道口径为 2~10 张，接入前需控制台确认 [4] | `wan2.6-image` 编辑模式必须 1~4 张；`wan2.7-image(-pro)` 0~9 张 [6][7] | `images/edits` 的 `image` 为数组，官方 SDK 写明最多 16 张 [13] |
| 同步/异步 | Lite/4.x/5.0-lite 同步返回 `data.url`；5.0 Pro 多为异步 task 模式 [2][4] | wan2.6/2.7 支持 HTTP 同步，也提供异步 [6][7] | 同步；另有 Batch API（半价、24h 周转）[11][12] |
| 图内文字渲染 | 5.0-lite 官方资料列为增强点；Pro 支持 14+ 语言图内文字 [2] | `wan2.7-image-pro` 官方场景含文字渲染 [5] | 1.5 指令遵循更好，支持图内文本 [11] |
| 官方刊例价 | 5.0 Lite **0.22 元/张**；4.5 0.25 元/张；4.0 0.20 元/张；5.0 Pro 输出 ≤261 万像素 0.30 元/张起 [3] | `wan2.6-image`/`wan2.7-image` **0.20 元/张**；`wan2.7-image-pro` 0.50 元/张 [8][9] | gpt-image-1.5 1024² 低/中/高 = $0.009/$0.034/$0.133，edit 另收 image input $8/1M tokens [11] |
| 返回格式 | `url`（约 24h 有效）或 `b64_json` [1] | 临时 URL（24h）为主 [6] | GPT Image 系列基本只返回 `b64_json` [13] |
| 内容审核 | 方舟内置安全护栏 + 隐式水印；图生图结果若仍含可识别真人身份会被拒（防深伪）[14] | 百炼内置安全护栏，输入 prompt 与输出图均审核，违规返回 `DataInspectionFailed` [10] | 提供 `v1/moderations`，境外服务 |
| 国内可达/合规 | 境内 endpoint，可直接用于国内 Spring Boot 后端 + 小程序 | 境内服务，合规 | **大陆不可直连，需代理，跨境合规与小程序审核风险高，不推荐** [11] |

**结论**：选 Seedream。理由：(1) 仓库已有 Ark SDK 2.0.18、`ArkService` Bean、配置项与生成实现，边际成本最低（本地验证过该版本 `GenerateImagesRequest.image` 字段即为 `List<String>`，多图参考不需要升级 SDK）；(2) 多参考图上限 14 张为官方文档明确口径 [2]；(3) 0.22 元/张成本可控，且内置审核与隐式水印对 UGC 场景友好 [14]。即梦 Jimeng 企业 API 实际也由火山引擎承载、公开文档未给出完整价格与参数，不建议绕路 [15]。通义万相作为备选保留（若团队已有阿里云商务折扣，wan2.6-image 0.20 元/张同价位且支持同步调用 [6][8]）。

## 4. 微信小程序合规与约束

### 4.1 网络与域名白名单

- 小程序只能与**预先配置的通讯域名**通信，`wx.request` / `wx.uploadFile` / `wx.downloadFile` 均受此限 [16]。
- 域名**必须是 HTTPS、不能使用 IP 地址或 localhost（局域网 IP 仅限开发版/体验版）、必须 ICP 备案** [16]。线上 `api.eggbabe.com` 与 `oss.eggbabe.com` 已在白名单内（现有功能已在线上运行）；开发期 `192.168.x.x` 只能走"不校验合法域名"模式 [16]。
- 配置端口后只能请求该端口；服务器域名每月最多修改 50 次（开放平台接口错误码 86102），不要把临时域名反复加进白名单 [16][17]。
- `wx.request` 默认超时 60s，`app.json` 的 `networktimeout` 或单次调用的 `timeout` 可调，但最大也是 60s [16][18]。**图片生成 5~30s 的耗时放在同步 HTTP 上等于是把可靠性压在超时线上**，这是第 2 节选异步任务 + 轮询的直接依据。
- `wx.request`/`wx.uploadFile`/`wx.downloadFile` 最大并发各为 10 个 [16]；上传 + 轮询 + 正常业务并发无压力，但轮询间隔别太激进。
- 结果图用 `<image>` 组件直显 OSS URL 即可，**不需要**把 `oss.eggbabe.com` 配进 downloadFile 域名；只有"保存到相册"（`wx.saveImageToPhotosAlbum`）走本地临时文件时才需要 `wx.downloadFile`。

### 4.2 拍照/选图与上传

- `wx.chooseMedia`：`mediaType: ['image']`，`sourceType: ['album','camera']`，`count` 基础库 2.25.0+ 最多 20 个（本场景 1 张即可）；返回 `tempFiles[].size`（字节）与 `tempFilePath` [20]。
- 官方文档**未写明** `chooseMedia` 单张临时文件与 `wx.uploadFile` 单文件的大小上限 [20][21]；但后端 `ImageUploadService` 已有 5MB 白名单校验，小程序侧在上传前用 `tempFiles[0].size` 做一次前置拦截，给出友好提示。
- `wx.uploadFile` 发起 HTTPS POST（multipart/form-data），不能设置 Referer，支持 `timeout` 参数 [21]。现有 `doodle-api.js` 的封装可直接复用，只需换 `scene` 参数。

### 4.3 内容安全（强制）

- 《微信小程序平台运营规范》5.18 明确要求：涉及用户发布内容的小程序**必须调用内容安全检测接口校验文本/图片**，未设置过滤机制属违规 [22]。"用户上传照片 + AI 合成图展示"属于典型 UGC 场景，**必须接入**。
- `security.mediaCheckAsync`（2.0，推荐新开发使用）：异步校验图片，支持 jpg/jpeg/png/bmp/gif，单文件 ≤10M，配额 2000 次/分钟、20 万次/天/ appId；需传 `media_url`（微信服务器需可下载）、`media_type=2`、`version=2`、`scene`、`openid`（该用户近 2 小时访问过小程序）；**结果在 30 分钟内以消息推送方式发到开发者配置的服务器** [19]。
- `security.imgSecCheck`（1.0）：同步，但官方已声明 2021-09-01 起停止更新维护；图片 ≤750×1334px 且 ≤1M，同样 2000 次/分钟配额 [23]。**不建议新接入**。
- 落地建议（双层）：
  1. **生成前**：用户照片入 OSS 后，后端调 `mediaCheckAsync` 预检（scene=1 资料）；若嫌推送链路重，可先用火山方舟内置安全护栏作为生成期的硬拦截（图生图含可识别真人身份会被拒 [14]），mediaCheckAsync 作为合规层异步兜底。
  2. **生成后**：结果图 URL 调一次 `mediaCheckAsync`，推送回调里把 `task.status` 从"待审核"推进到 `SUCCEEDED`/`FAILED`；manager-api 需新增一个微信消息推送回调端点（mp 后台配置）。**注意该设计会把出图到可见之间增加秒级到分钟级延迟**，产品文案需预留"审核中"态。
  3. 用户照片本身不对外展示时，预检可以放宽为"仅生成前校验"，但 AI 合成图对外展示前必须完成审核。

### 4.4 AIGC 类目与标识（强制）

- 服务内容涉及深度合成技术（AI 绘画/AI 创作等）需补充对应服务类目（**深度合成-AI绘画/AI创作**）；资质示例要求《互联网信息服务算法备案》（生成合成类）与小程序主体同技术主体的合作协议（含算法名称/应用场景/备案编号）[24]。本项目使用火山引擎 Seedream，算法备案由火山引擎作为服务提供者承担，小程序主体通常以"使用已备案第三方服务"路径补充协议材料——**需在 mp 后台确认当前账号类目是否已覆盖，缺则补类目**。
- 微信官方公告：所有 AI 生成合成内容需在**显著位置标注**"AI生成/人工智能生成"或同等含义字样（如"图片由AI生成"），平台已于 **2025-09-01** 起正式落实 [25]。小程序结果展示页需在图片附近常驻该标识（与聊天页"内容由AI生成"标识同一产品语言）。

## 5. 现有代码基础

### 5.1 manager-api 已有完整的 Seedream（火山引擎）文生图链路

- `main/manager-api/src/main/java/xiaozhi/modules/pet/config/SeedreamProperties.java`：`seedream.*` 配置（key/url/model/size/stream/watermark），默认模型 `doubao-seedream-5-0-lite-260128`，size `2K`，见 `application.yml` 第 56-63 行。
- `main/manager-api/src/main/java/xiaozhi/modules/pet/config/SeedreamArkConfig.java`：基于 `volcengine-java-sdk-ark-runtime`（pom 中版本 2.0.18）构建 `ArkService` Bean，timeout 180s；`@ConditionalOnProperty(prefix="seedream", name="key")` 控制启停。
- `main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/CollectionCardImageServiceImpl.java`：**最直接可复用的实现**。流程：拼 prompt（模板 + 宠物昵称/生日/星座等文案槽位）→ 按宠物原型选预置参考图（`https://oss.eggbabe.com/cards-bg/card-rabbit.png`、`card-fish.png`）→ `arkService.generateImages(...)`（同步调用，`requestBuilder.image(参考图URL)` 单参考图，`responseFormat=Url`）→ RestTemplate 下载结果图 → 上传阿里云 OSS（`eggbabe/cards/{petId}.png`，PublicRead）→ 返回公网 URL。
- 注意：触发该链路的 `CollectionCardGenerationListener`（破壳后 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 异步生成、回写 `ai_pet` 表）**当前被注释禁用**，但事件驱动 + 异步线程池的模式代码仍在，可直接作为本功能的异步范式参考。

### 5.2 通用图片上传接口（用户照片入 OSS）

- `main/manager-api/src/main/java/xiaozhi/common/upload/ImageUploadController.java`：`POST /upload/image`，参数 `file` + `scene`，需登录（`sys:role:normal` 权限）。
- `ImageUploadService.java`：按 `UploadScene` 白名单校验 content-type（png/jpeg/webp）与大小（当前 5MB），key 形如 `{scene}/{userId}/{uuid}.{ext}`，上传 OSS 返回公网 URL。
- `UploadScene.java`：枚举当前只有 `DOODLE` 一个值。**新增本功能只需在枚举追加一个场景（如 `PHOTO`/`AI_GEN`），即复用全部校验与 key 生成逻辑。**
- `OssService.java`（`xiaozhi/common/oss/`）：阿里云 OSS 封装，upload/download/delete/batchDelete + `buildPublicUrl()`（默认兜底域名 `https://oss.eggbabe.com`）。

### 5.3 小程序端调用模式

- `main/egg-miniprogram/miniprogram/config/api.js`：`API_BASE_URL`（开发为本机 IP，线上 `https://api.eggbabe.com/xiaozhi`）。
- `miniprogram/utils/request.js`：统一 envelope（`{code,data,msg}`）+ Bearer token + 401 静默重登。
- `miniprogram/utils/doodle-api.js`：`wx.uploadFile` 上传范例——`POST {API_BASE_URL}/upload/image`、formData 带 `scene`、header 带 `Authorization`。本功能上传用户照片可完全照抄。
- 小程序目前**没有** `wx.chooseMedia` 使用先例（涂鸦是 Canvas 作画），拍照/选图入口为新增。

### 5.4 编排归属判断

- 图片生成编排应放在 **manager-api（Java）**：Seedream/Ark SDK、OSS、上传白名单、登录鉴权全在 Java 侧；`xiaozhi-server`（Python）只做语音流水线，与图片无关。
- xiaozhi-server 的 provider 生态（`core/providers/`）以 ASR/LLM/TTS/VAD 为主，config.yaml 中虽大量出现 Doubao/Volcengine（DoubaoASR、DoubaoStreamASR 等语音服务），但无图片生成 provider，无需扩展。

### 5.5 其他可复用点

- 配额先例：`application.yml` `chat.quota.free-daily-limit`（免费用户每日聊天轮次上限）——图片生成可按同样思路加每日次数限制。
- 宠物 IP 预置图已按原型存放在 OSS `cards-bg/` 目录，并在 `CollectionCardImageServiceImpl.resolveReferenceImageUrl()` 有解析逻辑可复用/扩展。

## 6. 风险与开放问题

1. **mediaCheckAsync 推送链路成本**：结果是微信推送到开发者服务器（30 分钟内），不是同步返回 [19]。这意味着"生成完成 → 审核通过 → 用户可见"之间有一段不可控延迟，且 manager-api 要新增一个对外回调端点并处理微信的 token 校验消息。若产品不能接受，可降级为：方舟内置审核（生成期硬拦截）+ 展示前 imgSecCheck 1.0（同步，≤1M/750×1334，虽已停更但仍可用）或阿里云 OSS 图片审核服务做服务端审核。**需产品确认审核延迟容忍度后定案。**
2. **Seedream 生成含真人照片的限制**：方舟安全护栏会拒绝"图生图结果仍含可识别真人身份"的请求 [14]。本功能恰好是用户照片 + IP 图融合，**必须在开发初期就用真实照片验证通过率**；若误杀率高，需调整 prompt（让用户照片退化为"场景/氛围参考"而非人像保留）或改用 Seedream 4.x 的多图参考（主体一致性模式）。
3. **5.0 Pro vs 5.0 Lite**：仓库默认配的是 5.0-lite（0.22 元/张、同步），文字渲染与多图能力够用；若后续要更强图内排版，Pro 按像素档计费且偏异步 task 模式 [3][4]，切换时需改任务表状态机，先按 Lite 落地。
4. **成本控制**：单张 0.22 元对免费用户必须配每日限额（参考 `chat.quota` 先例），并在任务表记录用户当日次数；失败任务不计费但要把 Volcengine 返回的失败也纳入配额（防刷）。
5. **类目与备案**：mp 后台是否已覆盖"深度合成-AI绘画/AI创作"类目、火山引擎备案编号与合作协议材料是否齐备，是上架前硬性检查项 [24]；AI 生成标识 2025-09-01 起已强制 [25]。
6. **轮询与任务残留**：小程序切后台/杀进程会中断轮询，任务表需有兜底清理任务（如 24h 未终态标记 FAILED），避免僵尸任务堆积；可参考 `CollectionCardGenerationListener` 失败后仅记日志的容错风格，但任务表场景建议落库状态而不是静默丢弃。
7. **OSS 图片治理**：用户照片与生成图按 `{scene}/{userId}/{uuid}` 隔离（现成逻辑），需明确 retention 策略（如生成失败 7 天后删照片），避免 bucket 膨胀。
8. **多参考图上限口径**：5.0 Pro 多图上限官方公开页面为 JS 渲染未直接抓取（官方渠道口径 2~10 张）[4]；本方案只用 2 张参考图，任何档位都够用，无实际风险。

## 7. 参考来源

1. 火山方舟图片生成 API 参考（images/generations 端点、response_format、URL 时效）：https://www.volcengine.com/docs/82379/1548482
2. 火山引擎 Seedream 模型能力/参数（模型 ID、多图参考上限 14 张、文字渲染）：https://docs.volcengine.com/docs/6492/2172373
3. 火山方舟模型价格（Seedream 各档位刊例价）：https://docs.volcengine.com/docs/82379/1544106
4. Seedream 5.0 Pro 教程（Pro 模型 ID、异步任务模式）：https://www.volcengine.com/docs/82379/2582774
5. 阿里云图片生成与编辑模型列表（wan2.x 模型 ID 与适用场景）：https://help.aliyun.com/zh/model-studio/image-model/
6. 通义万相 2.6 API 参考（编辑模式 1~4 张输入、同步调用）：https://help.aliyun.com/zh/model-studio/wan-image-generation-api-reference
7. 通义万相 2.7 API 参考（0~9 张输入）：https://help.aliyun.com/zh/model-studio/wan-image-generation-and-editing-api-reference
8. wan2.6-image 价格（0.20 元/张）：https://help.aliyun.com/zh/model-studio/wan2-6-image
9. wan2.7-image-pro 价格（0.50 元/张）：https://help.aliyun.com/zh/model-studio/wan2-7-image-pro
10. 阿里云百炼内容安全（输入输出审核、DataInspectionFailed）：https://help.aliyun.com/zh/model-studio/content-security
11. OpenAI gpt-image-1.5 模型页（价格、能力、端点）：https://developers.openai.com/api/docs/models/gpt-image-1.5
12. OpenAI Image generation guide（images/edits、Batch API）：https://developers.openai.com/api/docs/guides/image-generation
13. openai-python image_edit_params（images/edits 最多 16 张输入图、b64_json）：https://github.com/openai/openai-python/blob/f16fbbd2/src/openai/types/image_edit_params.py
14. 火山方舟隐式水印与安全（内置审核、防深伪限制）：https://docs.volcengine.com/docs/82379/1810470 、https://docs.volcengine.com/docs/82379/2223965
15. 即梦 AI 企业接口文档（经火山引擎接入）：https://docs.volcengine.com/docs/85621/2164806
16. 小程序网络使用说明（域名白名单、HTTPS/ICP 备案、并发 10、超时 60s）：https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html
17. 服务器域名修改接口（每月 50 次限制，错误码 86102）：https://developers.weixin.qq.com/doc/oplatform/openApi/OpenApiDoc/miniprogram-management/domain-management/modifyServerDomainDirectly.html
18. wx.request API（timeout 默认 60000ms）：https://developers.weixin.qq.com/miniprogram/dev/api/network/request/wx.request.html
19. security.mediaCheckAsync 服务端文档（异步审核、10M、配额、推送回调、openid 要求）：https://developers.weixin.qq.com/miniprogram/dev/server/API/sec-center/sec-check/api_mediacheckasync.html
20. wx.chooseMedia API（mediaType/sourceType/count/size）：https://developers.weixin.qq.com/miniprogram/dev/api/media/video/wx.chooseMedia.html
21. wx.uploadFile API（HTTPS multipart、timeout）：https://developers.weixin.qq.com/miniprogram/dev/api/network/upload/wx.uploadFile.html
22. 微信小程序平台运营规范 5.18（UGC 必须接入内容安全检测）：https://developers.weixin.qq.com/miniprogram/product
23. security.imgSecCheck 1.0（停止维护声明、750×1334/1M 限制）：https://developers.weixin.qq.com/miniprogram/dev/framework/security.imgSecCheck.html
24. 微信小程序深度合成类目要求（算法备案、合作协议）：https://developers.weixin.qq.com/miniprogram/product/material/shenduhecheng/Aiwenda.html
25. 微信开放社区 AIGC 内容标识公告（2025-09-01 起强制"AI生成"标注）：https://developers.weixin.qq.com/community/minihome/doc/000ce4a4234cb8bee4c3d28436b009
