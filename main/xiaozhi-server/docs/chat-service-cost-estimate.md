# 聊天服务单用户月度成本估算

> 估算日期：2026-06-14
> 适用范围：`main/xiaozhi-server/` 聊天服务
> 货币单位：人民币（元）

## 1. 背景与范围

本文档估算 xiaozhi-esp32-server 聊天服务在当前所选模型组合下，**单用户一个月的 API 调用成本**。仅计算以下四个按量计费组件，不含基础设施（服务器、带宽、数据库）费用：

| 组件 | 角色 | 模型 | 配置来源 |
|---|---|---|---|
| LLM | 聊天 + 记忆抽取 | `qwen3.6-flash` | `LLM.AliLLM`（`model_name: qwen-flash`）+ powermem 记忆模块 |
| 向量模型 | 记忆系统 embedding | `text-embedding-v4` | `Memory.powermem.embedder`（DashScope） |
| ASR | 语音识别（流式） | 阿里云 Paraformer 实时 | `ASR.AliyunStreamASR`（`type: aliyun_stream`） |
| TTS | 语音合成（流式） | 豆包语音合成 2.0 | `TTS.HuoshanDoubleStreamTTSV2`（`resource_id: seed-tts-2.0`） |

## 2. 单价表

| 模型 | 计费方式 | 单价 | 可靠性 |
|---|---|---|---|
| 豆包语音合成 2.0（seed-tts-2.0） | 按字符 | 3 元 / 万字符 | 已核实（火山官方计费） |
| 阿里云流式 ASR（Paraformer） | 按时长 | 0.3 元 / 分钟（0.005 元/秒） | 已核实（`config.yaml` 注释） |
| text-embedding-v4 | 按 token（仅输入） | 0.0005 元 / 千 token（0.5 元/百万） | 已核实（百炼模型价格） |
| qwen3.6-flash | 按 token | flash 档超低价，约 0.1~0.8 元 / 百万输入 | 区间估算（见 §5 说明） |

> qwen-flash 精确单价以百炼控制台为准。**好消息：该价格对总成本结论几乎无影响**——见 §6 灵敏度分析。

## 3. 记忆模块调用链（决定 LLM/embedding 用量）

记忆模块使用 `Memory.powermem`，其调用频率直接影响 LLM 与 embedding 的消耗。源码确认：

- **记忆查询** `query_memory`（`core/connection.py:934`）：**每轮用户说话**触发 1 次，执行 1 次 embedding（查询向量化）。
- **记忆抽取** `save_memory`（`core/connection.py:308`）：在 **WebSocket 会话关闭时**触发 1 次（不是每轮），对整段对话做 1 次 LLM 抽取 + 每条抽取事实 1 次 embedding。

结论：记忆场景的 LLM/embedding 调用按「轮」和「会话」计，而非每条消息都抽，比较节省。

## 4. 用量假设

### 4.1 单轮对话典型消耗

| 指标 | 每轮均值 | 说明 |
|---|---|---|
| 用户说话时长（ASR） | 6 秒 = 0.1 分钟 | 一句日常话 |
| LLM 输入 | ~1500 tokens | 系统提示词 + 截断后的历史上下文 |
| LLM 输出 | ~150 tokens ≈ 100 中文字 | 伴侣设定"绝不长篇大论" |
| TTS 合成 | ~100 字 | 等于 LLM 输出字数 |
| 记忆查询 embedding | 1 次 ~40 tokens | 每轮 |

### 4.2 三档使用强度

| 强度 | 每天对话轮数 | 每月轮数（30 天） | 每月会话数（10 轮/会话） |
|---|---|---|---|
| 轻度 | 10 | 300 | 30 |
| 中度 | 30 | 900 | 90 |
| 重度 | 60 | 1800 | 180 |

## 5. 中度用户明细计算（30 轮/天 = 900 轮/月）

| 组件 | 月用量 | 计算 | 月费用 |
|---|---|---|---|
| ASR | 900 x 0.1 = 90 分钟 | 90 x 0.3 | 27.0 |
| TTS | 900 x 100 = 9 万字符 | 9 x 3 | 27.0 |
| LLM 聊天（输入） | 1.35M tokens | 1.35 x (0.1 ~ 0.8) | 0.14 ~ 1.08 |
| LLM 聊天（输出） | 0.135M tokens | 0.135 x (0.3 ~ 2) | 0.04 ~ 0.27 |
| Embedding（查询+存事实） | ~0.044M tokens | 0.044 x 0.5 | 0.02 |
| 记忆抽取 LLM | 0.225M 入 + 0.018M 出 | - | 0.03 ~ 0.22 |
| **合计** | | | **约 54 ~ 56** |

### 灵敏度说明

即便把 LLM 按贵得多的 qwen-plus 档（0.8 元/百万输入、2 元/百万输出）计算：

- LLM 聊天：1.35 x 0.8 + 0.135 x 2 = 1.35 元
- 记忆抽取：0.225 x 0.8 + 0.018 x 2 = 0.22 元
- LLM 合计仅约 1.6 元/月

因此 LLM 的精确单价无论落在 flash 还是 plus 档，**对总成本影响都不到 3%**。

## 6. 三档结果汇总

| 强度 | ASR | TTS | LLM + 向量 | 月成本 |
|---|---|---|---|---|
| 轻度（10 轮/天） | 9 | 9 | <1 | 约 18 |
| 中度（30 轮/天） | 27 | 27 | <1.6 | 约 54 |
| 重度（60 轮/天） | 54 | 54 | <3 | 约 110 |

## 7. 关键结论

1. **成本 99% 来自语音（ASR + TTS），二者基本对半开**；LLM 和向量模型在 qwen-flash + text-embedding-v4 这套配置下几乎是零头（合计 <2 元/月），换不换更贵的 LLM 对总价几乎无感。

2. **总成本对「每天聊几轮」和「回复长短」高度线性**——想压成本主要砍这两头，而不是换 LLM。

3. **容易被忽略的固定费**：火山 TTS 有**并发资源费**（`config.yaml` 注释提到"起步价 30 元就有 100 并发"），这是**按月固定、不随用户走**的。用户量少时该费用摊不掉：1 个用户时实际约为 54 + 30 = 84 元/月。

4. **核心经济账**：语音（ASR + TTS）是唯一值得优化的成本块，LLM/embedding 已无优化空间。

## 8. 优化建议（按收益排序）

| 方向 | 措施 | 节省幅度 | 代价 |
|---|---|---|---|
| TTS | 改用豆包 1.0 普通音色（`BV001_streaming`） | 低于 2.0 单价 | 音色/情感略差 |
| TTS | 改用 EdgeTTS（`TTS.EdgeTTS`） | 完全免费 | 音色/情感明显下降，仅 2 并发 |
| ASR | 部署本地 FunASR（`fun_local` 或 `fun_server`） | ASR 成本清零 | 吃机器资源（local 需 >2G 内存） |
| ASR | 改用非流式 AliyunASR | 省 20%（0.24 元/分钟） | 延迟升高 |
| LLM/embedding | 维持现状 | - | 无需改动 |

> 关于本地 FunASR 的部署配置，见 `config.yaml` 中 `ASR.FunASRServer` 的 docker 命令注释（2pass 模式，CPU 版镜像 `funasr-runtime-sdk-online-cpu-0.1.12`）。当同时在线用户超过 3~4 个时，部署 FunASR server（一次性硬件投入）比阿里云按量 ASR 划算；用户越多，省得越多。

## 9. 局限性

- 单价随时间变化，本文档基于 2026-06-14 的公开信息，实际以各云厂商控制台为准。
- 用量假设（轮数、回复长度、说话时长）基于伴侣型语音助手的典型场景，实际成本因用户行为而异。
- 未计入：意图识别 LLM 调用（`Intent: function_call` 模式下由主 LLM 承担，已含在聊天 LLM 内）、标题生成 LLM 调用（每会话 1 次，量小）、VAD（本地 SileroVAD，免费）、MySQL/Redis/带宽、服务器固定成本。

## 10. 参考来源

- [火山引擎豆包语音计费说明](https://www.volcengine.com/docs/6561/1359370)
- [阿里云百炼模型价格](https://help.aliyun.com/zh/model-studio/model-pricing)
- [阿里云向量化 Embedding 计费](https://help.aliyun.com/zh/model-studio/embedding)
- 项目 `main/xiaozhi-server/config.yaml`（ASR/TTS 注释）
- 项目 `main/xiaozhi-server/core/connection.py`（记忆调用链）
- 项目 `main/xiaozhi-server/core/providers/memory/powermem/powermem.py`（记忆抽取实现）
