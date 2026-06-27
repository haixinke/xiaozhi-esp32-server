# 音色克隆（Voice Clone）端到端实现调研

> 本文系统梳理 xiaozhi-esp32-server 中"音色克隆 / 声音复刻"功能在前端 web、后端 Java 服务、Python 聊天服务三层的完整实现链路。聚焦"用户上传音频 → 后端复刻训练 → 聊天服务用克隆音色合成语音"这条主线。
>
> 配套文档：[`huoshan-streamTTS-voice-cloning.md`](./huoshan-streamTTS-voice-cloning.md) 侧重火山引擎 TTS 的接入与密钥配置，本文侧重跨子项目的端到端机制。

## 1. 概述

整个音色克隆功能围绕**一个统一字段 `private_voice`** 串起三层，而**真正实现"复刻训练"的外部提供商目前只有火山引擎（字节）Seed-ICL / MegaTTS 一家**：

- **前端 web（`manager-web`）**：负责"上传音频 + 触发训练 + 把克隆音色绑定到 agent"。前端无麦克风录音，只有文件上传 + 波形裁剪。
- **后端服务（`manager-api`）**：负责调火山做复刻训练，拿到 `speaker_id` 持久化；并在设备拉配置时把 `speaker_id` 注入 TTS 配置的 `private_voice` 字段下发给 Python。
- **聊天服务（`xiaozhi-server`）**：所有支持自定义音色的 TTS provider 统一约定 `if config.get("private_voice"): self.voice = ...`，屏蔽各厂商音色字段命名差异。

涉及子项目与端口：

| 子项目 | 技术栈 | 端口 | 在克隆链路中的角色 |
|---|---|---|---|
| `main/manager-web/` | Vue 2 | 8001 (dev) | 智控台：上传/训练/绑定 |
| `main/manager-api/` | Spring Boot 3.4.3 | 8002 (`/xiaozhi`) | 训练编排、火山调用、配置下发 |
| `main/xiaozhi-server/` | Python 3.10 | 8000 (WS) | TTS Provider，消费克隆音色 |

## 2. 端到端架构总览

```
┌─────────────────────── 前端 web (manager-web / Vue) ───────────────────────┐
│  VoiceCloneManagement.vue (列表)  ──upload──▶  VoiceCloneDialog.vue         │
│    GET /voiceClone                            el-upload 选文件 → Web Audio   │
│    POST /voiceClone/cloneAudio               解码 → Canvas 波形 → 框选裁剪   │
│    POST /voiceClone/audio+play (试听)        → 手写 audioBufferToWav(16bit) │
│                           POST /voiceClone/upload (FormData: voiceFile,id) │
└───────────────────────────────────┬────────────────────────────────────────┘
                                     ▼
┌──────────────────── 后端服务 (manager-api / Spring Boot) ──────────────────┐
│  VoiceCloneController          VoiceResourceController (超管开通槽位)       │
│   /voiceClone/upload  ─▶ ai_voice_clone.voice (LONGBLOB) train_status=0    │
│   /voiceClone/cloneAudio ─▶ VoiceCloneServiceImpl.cloneAudio()             │
│        ├ type=="huoshan_double_stream" ?                                    │
│        └ huoshanClone(): Base64(audio) ──HTTP──▶ 火山 MegaTTS /upload       │
│             Header: Bearer;{access_token}, Resource-Id: seed-icl-1.0        │
│             ◀── { speaker_id: "S_xxxxx" }                                   │
│          ai_voice_clone.voice_id = speaker_id, train_status=2              │
│                                                                             │
│  下发: ConfigController POST /config/agent-models                           │
│   └ ConfigServiceImpl.buildModuleConfig():                                  │
│      configJson.put("private_voice", voice_id)        # S_xxxxx            │
│      voice.startsWith("S_") → resource_id="seed-icl-1.0"                   │
└───────────────────────────────────┬────────────────────────────────────────┘
                                     ▼
┌─────────────────── 聊天服务 (xiaozhi-server / Python) ─────────────────────┐
│  设备 WebSocket 连接 → ConnectionHandler                                     │
│   get_private_config_from_api() ──POST /config/agent-models──▶ (上面)       │
│   self.config["TTS"] = private_config["TTS"]   # 含 private_voice          │
│   initialize_tts() → tts.create_instance(type, config["TTS"][id])          │
│   TTSProvider.__init__:  self.voice = config.get("private_voice")  ◀─ 统一约定│
│        ↓ 落到协议字段                                                       │
│   doubao→voice_type / huoshan_double_stream→speaker / aliyun→voice /        │
│   alibl→voice / cozecn→voice_id / tencent→VoiceType / xunfei→vcn ...       │
│   LLM 文本 → text_to_speak(self.voice) → 云端合成 → Opus → 设备             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. 前端 web 实现（`manager-web`）

### 3.1 功能概述与用户流程

智控台的"音色克隆"是一个**面向终端用户的两阶段"音频上传 + 后端复刻"功能**，受 `featureManager.voiceClone.enabled` 特性开关控制（`src/utils/featureManager.js`），并在 `HeaderBar.vue` 根据开关与用户角色决定入口可见性。

> 前端**没有任何麦克风录音环节**。所谓"克隆"在前端就是"上传一段 8–60 秒的音频文件 + 触发后端复刻训练"。i18n 文案中"录制"二字是历史遗留。

用户流程：

1. 顶部导航"音色克隆"进入列表页 `VoiceCloneManagement.vue`，列表由 `GET /voiceClone` 分页返回（每行代表一条**已分配给该用户的克隆音色槽位**）。
2. 点"上传"→ 弹出 `VoiceCloneDialog.vue` 两步向导：步骤 1 选本地音频（`el-upload` 拖拽），步骤 2 用 Web Audio API 解码、Canvas 绘制波形，可框选片段裁剪，再手写代码把 `AudioBuffer` 编码成 WAV。
3. 点"上传音频"，前端把 WAV 以 `FormData`（`voiceFile` + `id`）`POST /voiceClone/upload`，后端存音频，`train_status` 进入"待复刻(0)"。
4. 回列表页对该行点"立即复刻"→ `POST /voiceClone/cloneAudio?cloneId=...`，后端异步训练，状态变为成功(2)或失败(3)。
5. 训练成功的克隆音色通过 `GET /models/{modelId}/voices` 与平台音色一起返回，`roleConfig.vue` 用 `voice.isClone` 区分，填进 agent 配置的"声音音色"下拉，最终通过 `form.ttsVoiceId` 绑定到 agent。

### 3.2 关键文件

| 文件（相对仓库根） | 职责 |
|---|---|
| `main/manager-web/src/router/index.js` | 注册 `/voice-resource-management`、`/voice-clone-management` 路由 |
| `main/manager-web/src/components/HeaderBar.vue` | 导航入口；普通用户直接显示"音色克隆"，超管下拉同时显示"音色克隆"与"音色资源" |
| `main/manager-web/src/utils/featureManager.js` | `voiceClone` 特性开关 |
| `main/manager-web/src/views/VoiceCloneManagement.vue` | 克隆音色列表页（用户视角） |
| `main/manager-web/src/views/VoiceResourceManagement.vue` | 音色资源管理页（超管视角） |
| `main/manager-web/src/components/VoiceCloneDialog.vue` | 两步上传向导：选文件 → 波形裁剪 → WAV 编码 → 上传 |
| `main/manager-web/src/components/VoiceResourceDialog.vue` | 超管"新增音色资源"表单 |
| `main/manager-web/src/apis/module/voiceClone.js` | voiceClone 模块 API |
| `main/manager-web/src/apis/module/voiceResource.js` | voiceResource 模块 API |
| `main/manager-web/src/apis/module/model.js` | `getModelVoices`：合并返回可用音色（含克隆，靠 `isClone` 区分） |
| `main/manager-web/src/views/roleConfig.vue` | Agent 配置页中的音色选择器与克隆音频预览 |

### 3.3 克隆流程详解

- **进入列表**：`created()` → `fetchVoiceCloneList()` → `GET /voiceClone`，每项含 `id`、`voiceId`、`name`、`languages`、`trainStatus`、`hasVoice`、`trainError`。状态文案：`hasVoice=false→待上传`；`trainStatus 0→待复刻 / 2→成功 / 3→失败`。
- **选文件**：`handleFileChange()` 把 `file.raw` 存为 `audioFile`/`originalAudioFile`，进入步骤 2 调 `loadAudio()`。
- **解码 + 波形**：`new AudioContext().decodeAudioData()` 得 `AudioBuffer`；`generateWaveform()` 对第一声道 PCM 按宽度分桶求平均绝对值；`drawWaveform()` 用 Canvas 画绿色柱状图。
- **裁剪**：鼠标事件记录归一化起止比例，`handleTrim()` 按 `start*duration*sampleRate` 计算 offset，`createBuffer()` 新建 buffer 逐声道拷贝 PCM。
- **WAV 编码**：`bufferToFile()` → `audioBufferToWav()`，标准 PCM 16-bit RIFF/WAVE 头 + Int16 采样限幅。无第三方编码库。
- **上传**：`uploadAudio()` 校验时长 8–60s，构建 `FormData(voiceFile, id)` → `POST /voiceClone/upload`。
- **触发复刻**：`handleClone()` → `POST /voiceClone/cloneAudio`（唯一带独立 `errorCallback` 的方法，失败时显示后端 `msg`），随后刷新列表读 `trainError`。
- **试听**：两步法 `POST /voiceClone/audio/{id}` 拿临时 uuid → `GET /voiceClone/play/{uuid}` 用 `new Audio()` 播放。

### 3.4 调用的后端 API

> Base URL = `VUE_APP_API_BASE_URL`（dev 经 `vue.config.js` 代理到 `http://localhost:8002/xiaozhi`）。

| 端点 | 方法 | 用途 |
|---|---|---|
| `/voiceClone` | GET | 分页查询当前用户的克隆音色槽位 |
| `/voiceClone/upload` | POST (multipart) | 上传/替换某条克隆音色的源音频（WAV） |
| `/voiceClone/updateName` | POST | 改名 |
| `/voiceClone/audio/{id}` | POST | 用克隆音色 id 换取临时音频下载 uuid |
| `/voiceClone/play/{uuid}` | GET | 用 uuid 播放克隆音频（匿名，一次性） |
| `/voiceClone/cloneAudio` | POST | 触发后端复刻训练 |
| `/voiceResource` | GET/POST/DELETE | 超管视角的音色资源 CRUD |
| `/voiceResource/user/{userId}` | GET | 按用户查音色资源 |
| `/voiceResource/ttsPlatforms` | GET | 获取支持克隆的 TTS 平台下拉 |
| `/models/{modelId}/voices` | GET | 按 modelId 取该用户可用音色（含克隆，`isClone` 标记） |

### 3.5 录音 / 波形 / 音频编码技术栈

| 能力 | 实现 |
|---|---|
| 选文件 | Element UI `el-upload`（`drag`、`:auto-upload="false"`） |
| 音频解码 | 浏览器原生 `AudioContext.decodeAudioData` |
| 波形绘制 | 原生 `<canvas>` 手写 |
| 音频裁剪 | `AudioContext.createBuffer` + 逐采样拷贝 |
| WAV 编码 | 手写 `audioBufferToWav()`（44 字节头 + Int16 PCM） |
| 试听 | `<audio>` + `new Audio(url)` |
| 上传 | `FormData` + 项目自封装 `RequestService`（基于 flyio） |

> `package.json` 里的 `opus-recorder`/`opus-decoder` 仅用于 `service-worker.js` 设备 Opus 解码缓存，**与克隆流程无关**。

### 3.6 voiceClone（克隆音色）vs voiceResource（平台音色资源）

| 维度 | voiceClone | voiceResource |
|---|---|---|
| 数据来源 | 用户上传音频 + 后端复刻训练 | 管理员配置的第三方 TTS 平台现成音色 |
| 入口可见性 | 普通用户 + 超管 | 仅超管 |
| 主视图 | `VoiceCloneManagement.vue`（上传/复刻/播放/改名） | `VoiceResourceManagement.vue`（新增/删除） |
| 新增方式 | 用户**不能自建**，需超管分配槽位 | 超管主动新增 |
| 状态语义 | `trainStatus` 0待复刻/2成功/3失败 | 由系统维护 |

二者最终在 `roleConfig.vue` 的音色下拉里合并展示，靠 `isClone` 区分。

## 4. 后端服务实现（`manager-api`）

### 4.1 整体架构

后端把音色克隆拆成两个 Controller：

- **`VoiceResourceController`（`/voiceResource`，超管）**：给某用户在某 TTS 模型下"开通"一组克隆音色槽位（占位记录，`train_status=0`，分配占位 `voice_id`，火山要求形如 `S_xxxxx`）。
- **`VoiceCloneController`（`/voiceClone`，普通用户）**：上传音频 + 触发训练。

真正发起克隆训练的代码在 `VoiceCloneServiceImpl.cloneAudio()` → `huoshanClone()`，**唯一集成的外部克隆提供商是火山引擎的 MegaTTS / Seed-ICL 声音复刻 API**。阿里云、讯飞、GPT-SoVITS、Fish Speech 等在 Python 侧存在，但 Java 侧未给它们实现克隆训练调用。

下发到 Python 走通用 Config 链路（不经过克隆代码）。

### 4.2 关键文件

| 文件（相对仓库根） | 职责 |
|---|---|
| `main/manager-api/src/main/java/xiaozhi/modules/voiceclone/controller/VoiceCloneController.java` | 终端用户音色克隆端点 |
| `main/manager-api/src/main/java/xiaozhi/modules/voiceclone/controller/VoiceResourceController.java` | 管理员音色资源开通端点 |
| `main/manager-api/src/main/java/xiaozhi/modules/voiceclone/service/impl/VoiceCloneServiceImpl.java` | **核心实现，含 `huoshanClone()` 调火山** |
| `main/manager-api/src/main/java/xiaozhi/modules/voiceclone/entity/VoiceCloneEntity.java` | `ai_voice_clone` 实体 |
| `main/manager-api/src/main/java/xiaozhi/modules/voiceclone/dao/VoiceCloneDao.java` + `mapper/voiceclone/VoiceCloneDao.xml` | Mapper，含 `getTrainSuccess` |
| `main/manager-api/src/main/java/xiaozhi/modules/voiceclone/dto/VoiceCloneDTO.java` | 管理员开通入参 |
| `main/manager-api/src/main/java/xiaozhi/common/constant/Constant.java` | `VOICE_CLONE_HUOSHAN_DOUBLE_STREAM`、`TrainStatus` |
| `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java` | 克隆错误码 10140–10160 |
| `main/manager-api/src/main/java/xiaozhi/modules/config/service/impl/ConfigServiceImpl.java` | **下发 Python 核心：注入 `private_voice`、改写 `resource_id`** |
| `main/manager-api/src/main/java/xiaozhi/modules/config/controller/ConfigController.java` | `/config/agent-models` |
| `main/manager-api/src/main/java/xiaozhi/modules/timbre/...` | 平台音色（`/ttsVoice`、`ai_tts_voice`） |
| `main/manager-api/src/main/java/xiaozhi/modules/agent/entity/AgentEntity.java` | `ttsVoiceId`/`ttsModelId` 绑定入口 |

### 4.3 REST 端点清单

> 所有路径前缀为 context-path `/xiaozhi`（如 `POST /xiaozhi/voiceClone/upload`）。

**VoiceCloneController（`/voiceClone`，`sys:role:normal`）**

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/voiceClone` | 分页查询**当前用户**的克隆音色（强制注入 `userId`） |
| POST | `/voiceClone/upload` | 上传音频（.mp3/.wav，≤10MB），更新 `voice` BLOB |
| POST | `/voiceClone/updateName` | 更新名称，清缓存 `timbre:name:{id}` |
| POST | `/voiceClone/audio/{id}` | 换取一次性音频下载 UUID（存 Redis） |
| GET | `/voiceClone/play/{uuid}` | 用 UUID 拉取 wav（匿名，UUID 一次性消费） |
| POST | `/voiceClone/cloneAudio` | **触发克隆训练**（扣 `voice_clone_quota` 道具 + 调火山） |

**VoiceResourceController（`/voiceResource`，`sys:role:superAdmin`）**

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/voiceResource` | 分页查询所有用户的克隆音色 |
| GET | `/voiceResource/{id}` | 获取单条 |
| POST | `/voiceResource` | **新增（开通）**克隆槽位（需 modelId、voiceIds[]、userId） |
| DELETE | `/voiceResource/{id}` | 批量删除 |
| GET | `/voiceResource/user/{userId}` | 按用户查（普通用户权限） |
| GET | `/voiceResource/ttsPlatforms` | 支持克隆的 TTS 平台列表（仅 `type=huoshan_double_stream`） |

**ConfigController（`/config`，机对机 server secret 鉴权）**

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/config/agent-models` | **xiaozhi-server 拉取设备对应智能体的运行时配置（含克隆音色）** |

### 4.4 实际克隆提供商：火山引擎 Seed-ICL（最重要）

**唯一集成的克隆提供商：火山引擎（字节）Seed-ICL / MegaTTS 声音复刻。**

- **客户端代码**：`VoiceCloneServiceImpl.huoshanClone()`（`cloneAudio` 仅对 `type==huoshan_double_stream` 分支生效）
- **HTTP**：`POST https://openspeech.bytedance.com/api/v1/mega_tts/audio/upload`，JDK 11 `HttpClient`
- **鉴权头**：`Authorization: Bearer;{access_token}`（注意是 `Bearer;`）、`Resource-Id: seed-icl-1.0`
- **请求体**：
  ```json
  {
    "appid": "<来自 model config_json>",
    "audios": [{"audio_bytes": "<Base64(voice)>", "audio_format": "wav"}],
    "source": 2, "language": 0, "model_type": 1,
    "speaker_id": "<训练前的占位 S_xxx>"
  }
  ```
- **响应**：`{BaseResp:{StatusCode,StatusMessage}, speaker_id}` → 成功回填 `voice_id`、`train_status=2`；失败 `train_status=3`、`train_error=msg`
- **密钥来源**：**不在 application.yml**，而在 DB 表 `ai_model_config.config_json`（`appid`/`access_token`），由超管在智控台填入覆盖 seed 占位值
- **配额**：`cloneAudio` 前扣 `voice_clone_quota` 道具（changelog `202606101030.sql`）
- **voice_id 格式约束**：开通时若 type 是火山双流式，每个 voiceId 必须含 `"S_"` 子串，否则报 `VOICE_CLONE_HUOSHAN_VOICE_ID_ERROR`

### 4.5 数据模型：`ai_voice_clone`

| 列 | 类型 | 含义 |
|---|---|---|
| `id` | VARCHAR(32) PK | UUID |
| `name` | VARCHAR(64) | 名称（开通时自动生成 `MMddHHmm_index`） |
| `model_id` | VARCHAR(32) | 关联 `ai_model_config.id`（必须 `type=huoshan_double_stream`） |
| `voice_id` | VARCHAR(32) | **核心**：训练前占位 `S_xxx`，训练后被火山返回的 `speaker_id` 覆盖 |
| `languages` | VARCHAR(50) | 语言 |
| `user_id` | BIGINT | 拥有者，关联 `sys_user.id` |
| `voice` | LONGBLOB | 原始上传音频（wav/mp3），训练时 Base64 发送 |
| `train_status` | TINYINT | 0待训练 / 1训练中 / 2成功 / 3失败 |
| `train_error` | VARCHAR(255) | 失败原因 |

索引：`(model_id, user_id, train_status)`、`(voice_id)`、`(user_id)`、`(model_id, voice_id)`。

**绑定关系**：没有独立关联表，通过 `ai_agent.tts_voice_id` 直接指向音色主键（既可能指向 `ai_tts_voice.id`，也可能指向 `ai_voice_clone.id`），由 `ConfigServiceImpl.getAgentModels()` 运行时按"先查 timbre 表，未命中再查 voice_clone 表"区分。

### 4.6 下发 Python 的机制（对接关键）

**端点**：`POST /xiaozhi/config/agent-models`（server secret 鉴权）
**入参**：`AgentModelsDTO { macAddress, selectedModule }`

`ConfigServiceImpl.getAgentModels()` + `buildModuleConfig()` 关键逻辑：

1. **解析 voice**：先 `timbreService.get(agent.ttsVoiceId)` 查平台音色表；未命中 → `cloneVoiceService.selectById(...)` 查 `ai_voice_clone`，取 `voice = voiceClone.getVoiceId()`（火山 `speaker_id`，`S_` 开头）。
2. **注入 TTS 模型 config**：
   ```java
   if (voice != null)
       configJson.put("private_voice", voice);              // 克隆音色标识
   // 火山双流式 + voice 以 "S_" 开头时改写 resource_id
   if (VOICE_CLONE_HUOSHAN_DOUBLE_STREAM.equals(map.get("type"))
           && voice != null && voice.startsWith("S_")) {
       map.put("resource_id", "seed-icl-1.0");              // 走克隆推理通道
   }
   ```
3. 整个 TTS 模型 `config_json`（含 `appid/access_token/ws_url/resource_id/speaker` + 注入的 `private_voice`）以 `{modelId: configJson}` 形式放进 `result["TTS"]`。

### 4.7 克隆（VoiceClone）vs 平台音色（Timbre）

| 维度 | `ai_voice_clone`（克隆） | `ai_tts_voice`（平台音色，Timbre） |
|---|---|---|
| 本质 | 用户私有、上传音频训练得到的复刻音色 | 平台预设、全局共享 |
| Controller | `/voiceClone` + `/voiceResource` | `/ttsVoice` |
| 绑定 TTS 模型类型 | 必须 `huoshan_double_stream` | 任意 |
| 标识字段 | `voice_id`（火山 `speaker_id`，`S_` 开头） | `tts_voice`（厂商原始音色编码） |
| 音频来源 | `voice` LONGBLOB（用户上传原文件） | `reference_audio` URL（预置参考音频） |
| 用户维度 | 有 `user_id`，按用户隔离 | 无，全局共享 |
| 训练流程 | 有 `train_status` 状态机 | 无 |
| 下发 voice 字段 | `private_voice` | `tts_voice` |
| 下发 resource_id 改写 | 是（`S_` → `seed-icl-1.0`） | 否 |

> 命名歧义：中文"音色"在 Java 侧同时对应两套表——管理员 `/voiceResource` 操作的也是 `ai_voice_clone` 表（不是 `ai_tts_voice`）。

## 5. 聊天服务使用（`xiaozhi-server`）

### 5.1 TTS Provider 模式

xiaozhi-server 的 TTS 完全围绕 **Provider 模式**：`TTSProviderBase`（`core/providers/tts/base.py`）是抽象基类，由 `core/utils/tts.py:create_instance()` 根据配置中的 `selected_module.TTS` 动态实例化具体 provider。

克隆音色统一通过 **`private_voice`** 配置字段注入。所有支持自定义音色的 provider 遵循同一约定：构造函数里 `if config.get("private_voice"): self.voice = config.get("private_voice")`，否则回退到 `voice`/`speaker` 等默认字段。

### 5.2 TTS Provider 清单

`core/providers/tts/` 目录下的 provider（仅列与克隆/自定义音色相关者）：

| Provider 文件 | 平台 / 类型 | 接收克隆音色字段 | 是否支持克隆 |
|---|---|---|---|
| `gpt_sovits_v2.py` | GPT-SoVITS v2 | `ref_audio`/`ref_text` | 是（参考音频零样本） |
| `gpt_sovits_v3.py` | GPT-SoVITS v3 | `ref_audio`/`ref_text` | 是（参考音频零样本） |
| `fishspeech.py` | Fish Speech | `reference_id`/`reference_audio` | 是（reference_id 即克隆音色 ID） |
| `doubao.py` | 火山 Doubao（HTTP） | `private_voice`→`voice_type` | 是（声音复刻 voice_type） |
| `huoshan_double_stream.py` | 火山双流（seed-tts-2.0 / seed-icl-1.0） | `private_voice`→`speaker` | 是（S_ 开头触发 seed-icl-1.0） |
| `aliyun.py` | 阿里云 NLS（非流式） | `private_voice`→`voice` | 是（CosyVoice 自定义音色） |
| `aliyun_stream.py` | 阿里云 FlowingSpeech（流式） | `private_voice`→`voice` | 是 |
| `alibl_stream.py` | 阿里云百炼 CosyVoice DashScope | `private_voice`→`voice` | 是（CosyVoice 克隆） |
| `cozecn.py` | Coze | `private_voice`→`voice_id` | 是 |
| `tencent.py` | 腾讯云 TTS | `private_voice`→`VoiceType`（int） | 是 |
| `xunfei_stream.py` | 科大讯飞流式 | `private_voice`→`vcn` | 是 |
| `minimax_httpstream.py` | MiniMax 流式 | `private_voice`→`voice` | 是 |
| `siliconflow.py` / `openai.py` / `edge.py` | 兼容协议 | `private_voice`→`voice` | 是 |
| `index_stream.py` | IndexTTS（自建） | `private_voice`→`voice`/`character` | 是 |
| `paddle_speech.py` | PaddleSpeech | `private_voice`→`spk_id`（int） | 是 |

> "支持克隆"在多数云端 provider 中含义是"接受用户自定义/复刻出来的音色 ID"。真正的零样本克隆（上传参考音频即可合成）仅 `gpt_sovits_v2/v3` 与 `fishspeech` 实现，它们通过参考音频/`reference_id` 而非 `private_voice` 标识。
>
> 注意：Java 侧 `VoiceCloneServiceImpl.cloneAudio()` 只为 `huoshan_double_stream` 实现了训练分支。其它 provider 虽能消费 `private_voice`，但 Java 侧没有给它们实现"训练得到音色 ID"的入口。

### 5.3 典型 provider 如何消费 `private_voice`

**火山 Doubao（`doubao.py`）**
```python
if config.get("private_voice"):
    self.voice = config.get("private_voice")
else:
    self.voice = config.get("voice")
# 请求体 audio.voice_type = self.voice
```

**火山双流（`huoshan_double_stream.py`）**
```python
if config.get("private_voice"):
    self.voice = config.get("private_voice")
else:
    self.voice = config.get("speaker")
# start_session() payload.speaker = self.voice
# resource_id 来自 config（Java 侧在 S_ 前缀时改写为 seed-icl-1.0）
```

**阿里云百炼 CosyVoice（`alibl_stream.py`）**
```python
self.voice = config.get("voice", "longxiaochun_v2")
if config.get("private_voice"):
    self.voice = config.get("private_voice")
# run-task.parameters.voice = self.voice，model 默认 cosyvoice-v2
```

**Fish Speech（`fishspeech.py`）**：不走 `private_voice`，走 `reference_id`（克隆音色在 fish.audio 平台的短 ID），也支持 `references`（参考音频+文本）即时零样本克隆。

### 5.4 音色选择链路

克隆音色是 **agent 维度**配置，由 Java 根据设备绑定的 agent 解析并注入。完整路径：

1. 设备 WebSocket 连接 → `ConnectionHandler` 初始化（`core/connection.py`）。
2. 拉私有配置：`get_private_config_from_api()`（`config/config_loader.py`）→ `POST /config/agent-models`。
3. Java `ConfigServiceImpl` 根据 `macAddress → device → agent → tts_voice_id`，先查 `Timbre`，未命中查 `VoiceCloneEntity`，取 voice。
4. Java 注入 `private_voice` 等字段到 TTS configJson（含火山 `S_` 前缀时改 `resource_id`）。
5. Python 接收：`self.config["TTS"] = private_config["TTS"]`（`connection.py`）。
6. 重新实例化 TTS Provider：`initialize_tts()` → `tts.create_instance(...)`。
7. 具体 provider 读取 `private_voice`，落到协议字段发往云端。

> TTS 实例是**连接级**（每个 `ConnectionHandler` 一份），克隆音色在连接整个生命周期内固定；要换音色需重新拉配置重连。

### 5.5 端到端 TTS 数据流

```
设备 WebSocket 连接 (port 8000)
    │  ConnectionHandler.__init__
    ▼
get_private_config_from_api() ──POST /config/agent-models──▶ Java ConfigServiceImpl
    │  按 macAddress → device → agent → tts_voice_id → voice
    ▼
private_config: { TTS: { <id>: {..., private_voice: "S_xxxx"} }, selected_module: {...} }
    │  self.config["TTS"] = private_config["TTS"]
    ▼
initialize_tts() → tts.create_instance(type, config["TTS"][id])
    │  TTSProvider.__init__: self.voice = config["private_voice"]
    ▼
LLM 流式输出文本 → tts_text_queue
    │  tts_text_priority_thread 按标点切句
    ▼
text_to_speak(text) ← 用 self.voice 作为 voice_type/speaker/voice_id 调云端
    │  音频字节 → opus 编码 → tts_audio_queue
    ▼
sendAudioMessage → 设备
```

### 5.6 与 manager-api 字段对接

| 维度 | Java 下发字段 | Python 消费字段 | 是否一致 |
|---|---|---|---|
| 克隆/自定义音色标识 | `private_voice` | `config.get("private_voice")`（19 个 provider） | 一致 |
| 参考音频路径 | `ref_audio` | `config.get("ref_audio")` / `reference_audio` | 一致（Python 兼容两种 key） |
| 参考文本 | `ref_text` | `config.get("ref_text")` / `reference_text` | 一致 |
| 音量/语速/音调 | `ttsVolume`/`ttsRate`/`ttsPitch` | 同名，经 `TTS_PARAM_CONFIG` 映射 | 一致 |
| 火山双流 resource_id | `S_` 前缀 → `seed-icl-1.0` | 直接读 `config.get("resource_id")` | 一致 |

## 6. 核心字段 `private_voice` 生命周期

| 阶段 | 位置 | 值 | 说明 |
|---|---|---|---|
| 训练前占位 | `ai_voice_clone.voice_id` | `S_xxx`（管理员开通时分配） | 火山要求 speaker_id 以 `S_` 开头 |
| 训练后回填 | `ai_voice_clone.voice_id` | 火山返回的新 `speaker_id` | `train_status: 0→2` |
| Agent 绑定 | `ai_agent.tts_voice_id` | 指向 `ai_voice_clone.id` | agent 维度（非 device 维度） |
| 下发注入 | `ConfigServiceImpl` | `configJson.private_voice` | 覆盖默认 `voice`/`speaker` |
| 火山路由 | `ConfigServiceImpl` | `resource_id="seed-icl-1.0"` | 仅当 `voice` 以 `S_` 开头时改写 |
| Python 消费 | 各 TTS provider `__init__` | `self.voice = config["private_voice"]` | 19 个 provider 统一约定 |
| 协议落地 | 云端请求体 | `voice_type`/`speaker`/`voice`/`voice_id`/`VoiceType`... | 因 provider 而异 |

## 7. 关键设计点

1. **粒度是 agent，不是 device**：克隆音色通过 `ai_agent.tts_voice_id` 绑定，一设备绑一 agent → 一连接只用一个克隆音色，连接生命周期内固定。换音色需重连重拉配置。
2. **平台音色 vs 克隆音色双表合一**：Java 下发时先查 `ai_tts_voice`，未命中再查 `ai_voice_clone`，两者最终都汇入同一个 `private_voice`，Python 不区分来源。前端用 `isClone` 在下拉里区分展示。
3. **约定优于配置**：19 个 TTS provider 全部遵循 `if config.get("private_voice"): self.voice = config.get("private_voice")` 的统一约定，屏蔽各厂商音色字段命名差异。

## 8. 已知问题与风险

| 严重度 | 问题 | 位置 |
|---|---|---|
| 高 | `huoshanClone()` 与 `buildModuleConfig()` 有 `System.out.println` 残留，会把 `appid`/`access_token` 打到 stdout | `VoiceCloneServiceImpl.java`、`ConfigServiceImpl.java` |
| 中 | HttpClient 无超时/重试；`@Transactional` 被注释掉，训练失败时状态可能不一致 | `VoiceCloneServiceImpl.java` |
| 中 | 上传允许 `.mp3`，但训练写死 `audio_format:"wav"`，可能不匹配 | `VoiceCloneController` vs `huoshanClone` |
| 低 | `train_status=1`（训练中）从未被写入（火山是同步 HTTP，无异步轮询） | `Constant.TrainStatus` |
| 低 | 前端无麦克风录音功能（只有上传），i18n 文案"录制"是历史遗留 | `VoiceCloneDialog.vue`、`zh_CN.js` |
| 低 | `private_voice` 跨 provider 存在 int/str 混用风险（腾讯/百度需 `int()`，火山是字符串） | `tencent.py`、`paddle_speech.py` |

## 附录 A：超管登录看到"暂无音色资源"的原因

「音色克隆」页（`VoiceCloneManagement.vue`）调用的 `GET /voiceClone` 在后端**强制按"当前登录用户自己的 user_id"过滤**：

```java
// VoiceCloneController.page()
UserDetail user = SecurityUser.getUser();
params.put("userId", user.getId().toString());   // 写死 = 当前登录用户
```

因此**超管在该页面看到的是"分配给超管本人账号"的克隆槽位**，不是全平台的。超管账号本身未被分配过槽位 → 空列表 → 前端提示"您的账号暂无音色资源，请联系管理员分配"。

克隆槽位（`ai_voice_clone` 记录）只能由超管通过另一个接口创建：

| 页面 | 接口 | 权限 | 看到什么 | 能新增吗 |
|---|---|---|---|---|
| 音色克隆 | `/voiceClone` | `normal` | 仅当前登录用户自己的槽位 | 不能，只能上传/复刻/改名 |
| 音色资源 | `/voiceResource` | `superAdmin` | 全平台所有用户的槽位 | 能，顶部「新增」分配 |

**处理方法**：超管应进入「音色资源」页 → 点「新增」→ 选 TTS 平台（`huoshan_double_stream`）+ Voice ID（含 `S_`）+ 目标账号 + 语言 → 提交。此后被选中的用户登录「音色克隆」即可看到槽位。若超管想自己也能看到，把账号选成超管本人即可。

## 附录 B：关键文件索引

**前端 web（`main/manager-web/`）**
- `src/views/VoiceCloneManagement.vue`、`src/views/VoiceResourceManagement.vue`
- `src/components/VoiceCloneDialog.vue`、`src/components/VoiceResourceDialog.vue`
- `src/apis/module/voiceClone.js`、`src/apis/module/voiceResource.js`
- `src/views/roleConfig.vue`（音色绑定到 agent）

**后端 Java（`main/manager-api/`）**
- `src/main/java/xiaozhi/modules/voiceclone/controller/VoiceCloneController.java`
- `src/main/java/xiaozhi/modules/voiceclone/controller/VoiceResourceController.java`
- `src/main/java/xiaozhi/modules/voiceclone/service/impl/VoiceCloneServiceImpl.java`（`huoshanClone()`）
- `src/main/java/xiaozhi/modules/voiceclone/entity/VoiceCloneEntity.java`
- `src/main/java/xiaozhi/modules/config/service/impl/ConfigServiceImpl.java`（`private_voice` 注入）
- `src/main/resources/db/changelog/202510071522.sql`（建表）

**聊天服务 Python（`main/xiaozhi-server/`）**
- `core/providers/tts/base.py`（`TTSProviderBase`）
- `core/utils/tts.py`（`create_instance` 工厂）
- `core/utils/modules_initialize.py`（`initialize_tts`）
- `core/connection.py`（连接生命周期、私有配置注入）
- `config/config_loader.py`、`config/manage_api_client.py`
- `core/providers/tts/huoshan_double_stream.py`、`doubao.py`、`aliyun.py`、`alibl_stream.py`、`fishspeech.py`、`gpt_sovits_v2.py`、`gpt_sovits_v3.py` 等
