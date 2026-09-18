# 阿里云 AI 安全护栏用于图片内容安全检测 — 调研报告

日期：2026-09-18
背景：蛋宝宝小程序「AI写真」（AI 生图）需要对**用户上传照片**和**AI 生成结果图**做内容安全检测。聊天服务已接入阿里云 AI 安全护栏做文本检测，本报告回答：同一产品能否直接复用来做图片检测。

## 结论先行

**能。** 本仓库聊天服务用的就是阿里云「AI 安全护栏」（Green 2022-03-02 版 API 的 `MultiModalGuard` 接口），该接口**原生支持图片检测**：同一个 API、同一个 SDK 包、同一组 AccessKey，只需把 `service` 参数换成图片检测场景（`img_query_security_check` / `img_response_security_check`）、把 `serviceParameters` 里的 `content` 换成 `imageUrls` 即可。无需开通新产品、无需接「内容安全增强版 ImageModeration」那套独立接口。

需要注意：蛋宝宝 AI 写真链路**不经过** Python 聊天服务（xiaozhi-server），而是走 Java 后端（manager-api），所以图片检测应加在 manager-api 侧（Java SDK `com.aliyun:green20220302`），而不是改动现有 Python provider。

## 一、仓库现状

### 1.1 现有文本安全检测（xiaozhi-server, Python)

| 项 | 事实 | 来源 |
|---|---|---|
| 产品/API | 阿里云内容安全 Green 2022-03-02，`MultiModalGuard` 同步接口（即「AI 安全护栏」） | `main/xiaozhi-server/core/providers/content_safety/aliyun.py:69-75` |
| SDK 包 | `alibabacloud_green20220302`（Python） | `aliyun.py:8-10` |
| service 取值 | 输入检测 `query_security_check_pro`，输出检测 `response_security_check_pro` | `aliyun.py:26-31` |
| 入参 | `serviceParameters` = JSON `{content, chatId, sessionId?, done?}`，纯文本，上限 2000 字 | `aliyun.py:57-72` |
| 出参归一化 | `suggestion ∈ {pass, block, watch, mask}`，block → BLOCK；收集 `detail[].result[].label/level` 做审计 | `aliyun.py:105-148` |
| 限流 | 客户端内置 QPS 节流，默认 50 QPS（与官方同步接口 50/s 流控一致） | `aliyun.py:32-37` |
| 工厂/开关 | `content_safety.enabled` + `content_safety.provider=aliyun`，密钥读 `aliyun.access_key_id/secret`，另有 `region_id`/`endpoint` | `main/xiaozhi-server/core/utils/content_safety.py:8-21`、`aliyun.py:38-47` |
| 调用链 | `ContentSafetyGate`（`main/xiaozhi-server/core/content_safety/gate.py`）在聊天输入/输出两侧调用 provider；命中危机标签（如轻生）即使 block 也放行给 LLM 共情回复 | `gate.py:21-37` |

注：`config.yaml`（已提交版本）中没有 `content_safety` 段，实际配置在 gitignore 的 `data/.config.yaml` 中（未验证具体字段值，属本地密钥文件）。

### 1.2 蛋宝宝 AI 写真链路（egg-miniprogram → manager-api, Java)

链路完全在 manager-api，不经过 xiaozhi-server：

1. 小程序选图（≤5MB）→ `wx.uploadFile` 到 `POST /upload/image`（scene=`ai_gen`）得 OSS URL — `main/egg-miniprogram/miniprogram/utils/image-gen-api.js:13-46`
2. `POST /pet/image-gen/tasks` 创建任务 — `image-gen-api.js:53-55` → `ImageGenTaskServiceImpl.createTask()`（`main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/ImageGenTaskServiceImpl.java:84-121`）
3. `ImageGenExecutor.generate()` 异步执行：火山引擎 Seedream 多图参考生图 → 下载 → 上传 OSS — `main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/ImageGenExecutor.java:88-142`
4. 前端轮询 `GET /pet/image-gen/tasks/{taskId}` 拿 `SUCCEEDED/FAILED` — `image-gen-api.js:62-64`、`pages/photo-gen/photo-gen.js:155-176`

**原微信内容安全审核现状**（commit `d52de438`，联调阶段临时跳过，代码注释保留并标 `TODO(security)`）：

- 原设计是两轮**异步**审核：创建任务时 `PENDING` + 提交照片 `mediaCheckAsync`，回调通过才生成；生成后 `REVIEWING` + 提交结果图审核，回调通过才 `SUCCEEDED`。
- 审核实现：`WechatMediaCheckServiceImpl.mediaCheckAsync()` 调微信 `security.mediaCheckAsync 2.0`（`https://api.weixin.qq.com/wxa/media_check_async`，media_type=2 图片、scene=1 资料场景），返回 `trace_id` — `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/impl/WechatMediaCheckServiceImpl.java:25-74`
- 回调入口：`/wechat/mp/callback` → `ImageGenTaskServiceImpl.handleMediaCheckResult()` 按 trace_id 路由照片/结果图结论，条件更新保证幂等 — `ImageGenTaskServiceImpl.java:140-207`
- 当前状态：`createTask` 直接 RUNNING 触发生成（`ImageGenTaskServiceImpl.java:107-109` 注释掉了 `mediaCheckAsync`）；`ImageGenExecutor.generate` 上传 OSS 后直接 SUCCEEDED（`ImageGenExecutor.java:117-124` 注释掉了结果图审核提交）。回调链路保留未删。

## 二、阿里云产品能力（官方文档）

### 2.1 AI 安全护栏支持图片检测

`MultiModalGuard`（AI 安全护栏多模态同步检测接口）的 `Service` 取值包含：

| service | 用途 |
|---|---|
| `query_security_check_pro` / `response_security_check_pro` | AI 输入/生成**文本**检测 Pro 版（本仓库现用） |
| `img_query_security_check` | AIGC **输入图片**安全检测 |
| `img_response_security_check` | AIGC **输出图片**安全检测 |
| `text_img_mix_guard` | 文本+图片多模态混合检测 |
| `file_security_sync_check` / `text_file_sec_sync_check` | 文件/文本+文件检测 |

来源：https://help.aliyun.com/zh/document_detail/2932956.html

### 2.2 入参格式

- 图片通过 `serviceParameters` 中的 **`imageUrls`（JSONArray，公网可访问 URL）** 传入，例如 `{"imageUrls": ["https://example.com/image.png"]}` — 同上文档
- **当前只支持一张图片**；格式支持 PNG/JPG/JPEG/BMP/WEBP/TIFF/SVG/AVIF/HEIF/GIF(取第一帧)/ICO；大小 ≤ 20MB，宽高 ≤ 30000px，总像素 ≤ 2.5 亿；图片下载限时 3 秒 — https://help.aliyun.com/zh/document_detail/2937221.html
- 未在官方文档中看到 base64 入参方式（未验证，以 URL 为准）

### 2.3 出参

与文本检测同一结构：`Suggestion ∈ {pass, block, watch, mask}`；`Detail[].Result[].Label/Level`，`Level ∈ {high, medium, low, none}`。文档示例 label 为 `contraband_act`（疑似违禁行为）；图片检测的完整标签清单（涉黄/暴恐/广告等）本次未从官方文档逐条抓到，**未验证**，落地前需查标签体系文档或实测。

### 2.4 计费与开通

- 该接口为**收费接口，仅对 HTTP 200 的请求计量计费**（按成功请求次数）— https://help.aliyun.com/zh/document_detail/2937221.html
- 开通入口：AI 安全护栏产品页（commodityCode `lvwang_guardrail_public_cn`）— 同上
- RAM 权限：系统策略 `AliyunYundunGreenWebFullAccess`（Action `yundun-greenweb:MultiModalGuard`）— 同上
- endpoint 示例：`green-cip.cn-shanghai.aliyuncs.com`（多地域可选）— 同上
- 「内容安全增强版」（ImageModeration 等）是同控制台下的另一套接口体系，**本方案不需要**。是否需对图片 service 单独开通/单独计费未逐条验证，建议控制台确认。

## 三、落地方案建议

图片检测应插在 **manager-api（Java）**，复用现有任务状态机，有两个可选策略：

### 方案 A（推荐）：阿里云护栏同步检测替换微信异步审核

- 新增 `AliyunImageCheckService`（Java SDK `com.aliyun:green20220302`，与 Python 侧同一产品同一套 AK）：
  - `createTask` 中：同步调 `MultiModalGuard(service=img_query_security_check, imageUrls=[photoUrl])`，block → 直接 FAILED「图片未通过审核」，pass → RUNNING 触发生成。插入点：`ImageGenTaskServiceImpl.java:107-121`
  - `ImageGenExecutor.generate()` 中：结果图上传 OSS 后、置 SUCCEEDED 前，同步调 `img_response_security_check`，block → FAILED「生成内容未通过审核」。插入点：`ImageGenExecutor.java:113-132`
- 优点：**同步接口**，不需要回调/trace_id/REVIEWING 状态，可把 `PENDING`/`REVIEWING` 链路简化掉（前端 `photo-gen.js:170-171` 的 REVIEWING 提示可保留兜底）；图片已在阿里云 OSS 公网 URL 上，满足 `imageUrls` 要求
- 缺点：同步调用增加生图链路耗时（一次检测通常秒级）；检测异常时的兜底策略（放行/拦截）需定

### 方案 B：恢复微信 mediaCheckAsync 两轮审核

即把 `d52de438` 注释掉的代码恢复（PENDING/REVIEWING + 回调）。优点：微信小程序生态内审核，平台合规口径最直接；免费。缺点：异步链路复杂（trace_id 路由、回调幂等、30 分钟超时清理），已被联调跳过说明推进成本不低。

也可两者叠加（阿里云拦生成侧、微信保平台合规），但一期建议只做方案 A。

### 改动范围估算（方案 A）

| 文件 | 改动 |
|---|---|
| manager-api `pom.xml` | 加 `com.aliyun:green20220302` 依赖 |
| 新增 `AliyunImageCheckService`（参考 Python 侧 `aliyun.py` 的归一化逻辑） | ~150 行 |
| `application-*.yml` | 加 AK/endpoint 配置项（复用或并列于现有 OSS 阿里云配置） |
| `ImageGenTaskServiceImpl.createTask` | 照片同步检测 ~20 行 |
| `ImageGenExecutor.generate` | 结果图同步检测 ~20 行 |
| 对应单测 `ImageGenTaskServiceImplTest` / `ImageGenExecutorTest` | 更新 |

小程序端无需改动（FAIL 走现有 `failReason` 展示）。Python 侧 `content_safety` provider 无需改动——其 `check()` 接口只收文本（`base.py:29-37`），且图片链路不过 Python 服务。

## 四、不确定 / 未验证的点

1. 图片检测的**完整标签体系**（涉黄/暴恐/广告等具体 label 值）未逐条从官方文档抓到，落地前需查标签文档或用样图实测。
2. 图片 service 是否与文本 service **共用已开通的护栏实例和计费项**，未验证，需在阿里云控制台确认（commodityCode `lvwang_guardrail_public_cn`）。
3. `imageUrls` 是否要求同 region OSS 或有防盗链限制未验证；蛋宝宝 OSS 图是 PublicRead（`ImageGenExecutor.java:114`），公网可下，预计没问题。
4. 本仓库 `data/.config.yaml` 中 `content_safety` 实际配置值未查看（gitignore 密钥文件），报告中的配置字段名以代码默认值为准。
5. 微信小程序平台是否**强制要求**走微信自身的内容安全接口（平台合规层面），未验证；若有此要求则需方案 A+B 叠加。

## 参考来源

- 仓库代码（路径:行号见上文表格）
- [MultiModalGuard - AI安全护栏多模态同步检测接口](https://help.aliyun.com/zh/document_detail/2932956.html)
- [多模态实时审核API接入指南](https://help.aliyun.com/zh/document_detail/2937221.html)
- [AI安全护栏的核心技术优势](https://help.aliyun.com/zh/document_detail/2873212.html)
- [OpenAPI 门户 MultiModalGuard](https://api.aliyun.com/document/Green/2022-03-02/MultiModalGuard)
