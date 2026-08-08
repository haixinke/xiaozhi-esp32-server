# 阿里云 AI 安全护栏运维指南

本文说明 `xiaozhi-server` 接入阿里云 AI 安全护栏后的配置、上线、回滚和验收流程。审核覆盖真实用户输入，以及进入 TTS 前的 LLM 生成文本；系统提示词、记忆、工具描述和对话历史不在审核范围内。

## 前置条件

1. 为用于安全护栏的 RAM 用户或角色授予 `AliyunYundunGreenWebFullAccess`。
2. 确认目标地域已开通阿里云内容安全的 `MultiModalGuard` 服务。
3. 服务镜像需包含 Python SDK `alibabacloud_green20220302==3.2.4`。
4. 使用非生产 RAM 凭据在预发环境完成本文的验收项；不得把 AccessKey、Secret、用户输入、风险词或完整审核响应写入工单、日志或文档。

## Manager API 参数

参数由 `manager-api` 的 `sys_params` 下发。AccessKey 必须在参数管理页面的脱敏输入中录入，不能提交到仓库或写入 `xiaozhi-server` 本地配置文件。

最小配置树如下。`enabled` 默认关闭，完成预发验证前不要启用。

```yaml
aliyun:
  access_key_id: ""
  access_key_secret: ""
content_safety:
  enabled: false
  provider: aliyun
  mode: enforce
  input_service: query_security_check_pro
  output_service: response_security_check_pro
  max_request_chars: 2000
  max_qps: 45
  output_chunk_chars: 120
```

完整参数如下：

| `sys_params.param_code` | 用途与约束 |
| --- | --- |
| `aliyun.access_key_id` | 阿里云 AccessKey ID；敏感参数。 |
| `aliyun.access_key_secret` | 阿里云 AccessKey Secret；敏感参数。 |
| `content_safety.enabled` | 总开关。`false` 时不创建任何护栏请求。 |
| `content_safety.provider` | 当前支持 `aliyun` 或 `noop`。 |
| `content_safety.mode` | `observe` 仅记录审核决定；`enforce` 阻断 API 的 `block` 结果和 API 失败。 |
| `content_safety.region_id` | 阿里云地域，例如 `cn-shanghai`。 |
| `content_safety.endpoint` | 对应地域的 MultiModalGuard 服务端点。 |
| `content_safety.connect_timeout_ms` | 连接超时，默认 `3000` 毫秒。 |
| `content_safety.read_timeout_ms` | 读取超时，默认 `10000` 毫秒。 |
| `content_safety.max_qps` | 单个 `xiaozhi-server` 进程的上限，默认 `45`，不得超过 `50`。多实例的总和也不得超过账户的 50 QPS 限额。 |
| `content_safety.max_request_chars` | 单次审核最大字符数，默认及上限均为 `2000`。 |
| `content_safety.output_chunk_chars` | LLM 流式文本缓冲到该字符数或句末后再审核，默认 `120`。 |
| `content_safety.input_service` | 用户输入审核服务，使用 `query_security_check_pro`。 |
| `content_safety.output_service` | LLM 输出审核服务，使用 `response_security_check_pro`。 |
| `content_safety.input_block_message` | 数组；输入被阻断时随机选择一条可信本地回复。 |
| `content_safety.output_block_message` | 数组；输出被阻断时随机选择一条可信本地回复。 |

若两组阻断文案缺失或全为空，服务使用既有 `system_error_response` 作为可信本地兜底；该兜底不会再次经过安全护栏，防止递归审核。

## 审核行为与日志

- 输入：音频 ASR 后的文本和文字消息均在意图识别、LLM 调用、STT 回显和对话历史写入之前审核。带 `speaker` 的 JSON 消息只审核 `content` 字段。
- 输出：普通流、`direct_answer`、工具递归后的 LLM 输出和 `IntentProvider.replyResult` 的 LLM 生成结果，在放入 `tts_text_queue` 前审核。`system_error_response` 等静态可信回复不发起护栏请求。
- 决策：阿里云 `block` 为阻断；`pass`、`watch`、`mask` 放行。`observe` 仍调用 API 并记录决定，但永不阻断；`enforce` 对 `block` 和 API 异常都阻断。
- 审计：只允许查看方向、模式、决定、字符数、类别/等级摘要、耗时和 RequestId 等脱敏元数据。不得记录或检索原始文本、`RiskWords`、请求体、完整响应或凭据。

## 上线步骤

1. 部署包含内容安全参数的 `manager-api` Liquibase 迁移。
2. 部署包含 SDK 和内容安全组件的 `xiaozhi-server` 版本，但保持 `content_safety.enabled=false`。
3. 在参数管理页面为目标环境录入 RAM 凭据；确认敏感字段被遮罩显示。
4. 配置地域、端点、限流和两组阻断文案数组。多实例部署时，按实例数下调 `content_safety.max_qps`，确保总量不超过 50 QPS。
5. 将 `content_safety.enabled` 设为 `true`，先在预发以 `mode=observe` 验证，再切换到 `mode=enforce`。
6. 重启每一个 `xiaozhi-server` 进程。参数读取发生在启动/既有配置刷新时，不能假定仅保存参数即可在全部实例生效。
7. 依次执行下方的预发验收；确认日志只含脱敏元数据后，按小流量到全量的顺序发布生产。

## 预发验收脚本

使用预发 `manager-api`、预发设备和专用非生产 RAM 用户；检查设备行为及脱敏日志，不展示或保存测试的原文。

1. 发送一条正常文字消息和一条经 ASR 转写的正常消息：两者都应进入 LLM 并获得正常语音回复。
2. 发送命中预发阻断规则的输入：不得发起意图识别或 LLM 请求，不得 STT 回显；设备应仅播放 `input_block_message` 数组中随机的一条。
3. 令预发 LLM 返回命中阻断规则的输出，并覆盖 `direct_answer`：不安全文本不得播放或保留；设备应仅播放 `output_block_message` 数组中随机的一条。
4. 覆盖 `IntentProvider.replyResult`：LLM 生成的回复必须被审核；强制触发静态 `system_error_response` 时，应直接播放且不产生护栏 API 请求。
5. 设置 `content_safety.mode=observe` 并重启，重复阻断样例：日志应显示阻断决定的脱敏元数据，但内容仍会交付。完成后恢复 `enforce` 并再次重启。
6. 设置 `content_safety.enabled=false` 并重启，重复正常消息：不得产生护栏请求，原有聊天行为保持不变。

## 回滚

如需立即停用护栏，先将 `content_safety.enabled=false`，再重启每一个 `xiaozhi-server` 进程。此操作停止新的 Guardrails API 请求并恢复未接入前的聊天路径；保留参数和迁移，不需要删除凭据或回滚数据库。待问题定位后，在预发重新完成验收再启用。

## 本地自动化验证

以下命令不调用真实阿里云 API，也不需要真实凭据：

```bash
cd main/xiaozhi-server && python3.12 -m pytest tests/content_safety --cov=core.content_safety --cov=core.providers.content_safety --cov-report=term-missing -q
cd main/manager-api && mvn test -DskipTests=false -Dtest=ConfigServiceImplTest
cd main/manager-web && npm run test:unit
```
