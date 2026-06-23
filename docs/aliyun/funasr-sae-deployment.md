# FunASR 实时语音听写服务阿里云 SAE 部署指南

> 本文档介绍如何将 FunASR 实时语音听写（online）服务部署到阿里云 SAE（Serverless Application Engine），作为 xiaozhi-esp32-server 项目的 ASR 能力补充。

---

## 一、适用场景

- 需要通过 WebSocket 提供实时语音识别能力
- 用于 xiaozhi-esp32-server 聊天服务的 ASR 输入
- 希望利用 SAE 的 Serverless 特性，按需扩缩容
- 不需要本地 GPU，使用 CPU 版 ONNX 模型即可满足需求

---

## 二、部署架构

```
┌─────────────┐     WebSocket      ┌─────────────────────────────┐
│  客户端/设备  │ ────────────────► │  SAE 公网/内网访问入口        │
└─────────────┘                   │  FunASR 容器 :10095          │
                                  │                             │
                                  │  NAS 持久化存储              │
                                  │  /workspace/models          │
                                  └─────────────────────────────┘
```

---

## 三、前置准备

### 3.1 开通服务

- 阿里云账号已开通 **SAE**
- 阿里云账号已开通 **文件存储 NAS**
- 确保 SAE 应用与 NAS 挂载点在同一 VPC

### 3.2 创建 NAS 文件系统

1. 登录 [NAS 控制台](https://nas.console.aliyun.com/)
2. 创建通用型 NAS 文件系统（如预算允许，建议选性能型，模型加载对 I/O 有一定要求）
3. 添加挂载点，记录挂载点地址
4. 确保挂载点与 SAE 应用使用同一 VPC

> 存储容量建议至少 10 GB。模型文件约 500 MB - 1 GB，需预留日志和缓存空间。

---

## 四、SAE 应用创建

### 4.1 基础信息

| 配置项 | 建议值 |
|--------|--------|
| 应用名称 | `funasr-online` |
| 地域 | 与 NAS 同地域 |
| 命名空间 | 默认或自定义 |
| 技术栈语言 | **其它语言 / 自定义运行时 / 容器镜像** |

> 不要选 Java/Python/Go，因为 FunASR 是完整的容器镜像，自带运行时环境。

### 4.2 镜像配置

| 配置项 | 值 |
|--------|-----|
| 镜像类型 | 镜像仓库 - 公网镜像 |
| 镜像地址 | `registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-online-cpu-0.1.13` |

### 4.3 端口配置

| 配置项 | 值 |
|--------|-----|
| 容器端口 | `10095` |
| 协议 | TCP |
| 访问方式 | 公网访问 / 内网访问（按业务需要选择） |

---

## 五、持久化存储配置

在 SAE 控制台找到**持久化存储**区域，启用 NAS：

| 配置项 | 建议值 | 说明 |
|--------|--------|------|
| NAS 文件系统 | 提前创建的 NAS 实例 | 与 SAE 同地域、同 VPC |
| 挂载点 | NAS 文件系统的挂载点 | 例如 `xxx.cn-hangzhou.nas.aliyuncs.com` |
| 挂载目录 / NAS 子目录 | `/funasr/models` 或 `/` | NAS 上的子目录，`/` 表示根目录 |
| 容器路径 | **`/workspace/models`** | 必须固定为此路径 |

> **容器路径必须是 `/workspace/models`**，因为 FunASR 启动命令中 `--download-model-dir` 指定了该路径。

---

## 六、启动命令配置

### 6.1 推荐启动命令

在 SAE 控制台**启动命令**处填写以下内容：

```bash
bash -c "cd /workspace/FunASR/runtime && exec bash run_server_2pass.sh \
  --download-model-dir /workspace/models \
  --vad-dir damo/speech_fsmn_vad_zh-cn-16k-common-onnx \
  --model-dir damo/speech_paraformer-large-vad-punc_asr_nat-zh-cn-16k-common-vocab8404-onnx \
  --online-model-dir damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online-onnx \
  --punc-dir damo/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727-onnx \
  --itn-dir thuduj12/fst_itn_zh \
  --decoder-thread-num 8 \
  --model-thread-num 1 \
  --io-thread-num 8"
```

### 6.2 参数说明

| 参数 | 含义 | 说明 |
|------|------|------|
| `--download-model-dir` | 模型下载/缓存目录 | 必须指向 `/workspace/models` |
| `--vad-dir` | VAD 模型 | 语音活动检测 |
| `--model-dir` | 2pass 模型 | 非实时+实时混合识别 |
| `--online-model-dir` | 实时模型 | 流式识别核心模型 |
| `--punc-dir` | 标点预测模型 | 自动添加标点 |
| `--itn-dir` | 逆文本规范化 | 把口语数字转为书面数字 |
| `--decoder-thread-num` | 解码线程数 | 根据 vCPU 调整 |
| `--model-thread-num` | 模型内部线程数 | 通常设为 1 |
| `--io-thread-num` | IO 线程数 | 通常设为 8 |

### 6.3 关于语言模型

本配置**去掉了 `--lm-dir`（N-gram 语言模型）**，原因：

- 通用聊天场景下，Paraformer-large 基础模型准确率已足够
- LM 模型文件较大，会显著增加内存占用
- 如后续发现专业词汇识别不准，可再加回

保留 `--itn-dir` 的原因：

- 聊天场景中，用户说出"百分之五十"、"二零二五年"等，通常希望输出书面数字
- ITN 模型体积小，资源开销低

### 6.4 为什么用 `exec` 而不是 `nohup ... &`

- `nohup ... &` 会让服务在后台运行，容器主进程会立即退出
- SAE 会认为容器已停止，导致应用不断重启
- `exec` 让服务进程替换当前 shell，成为容器主进程，保持容器存活

---

## 七、网络访问配置

### 7.1 公网访问

如果客户端需要从外网连接：

1. 在 SAE 控制台开启**公网访问**
2. 记录 SAE 分配的公网地址和端口
3. 客户端连接时使用该地址

### 7.2 内网访问

如果客户端（如 ECS、ACK、函数计算）与 SAE 在同一 VPC：

- 使用 SAE 提供的**内网访问地址**
- 延迟更低，且不产生公网流量费用

### 7.3 客户端调用示例

```bash
python3 funasr_wss_client.py \
  --host <SAE地址> \
  --port <SAE端口> \
  --mode 2pass \
  --audio_in test.wav
```

> 注意：SAE 公网访问如果是网关形态，需确认是否支持长连接 WebSocket。如有超时，建议使用内网访问或调整网关配置。

---

## 八、首次启动与模型下载

### 8.1 首次启动会自动下载模型

由于镜像中不包含模型文件，首次启动时会从 ModelScope 自动下载：

- `damo/speech_fsmn_vad_zh-cn-16k-common-onnx`
- `damo/speech_paraformer-large-vad-punc_asr_nat-zh-cn-16k-common-vocab8404-onnx`
- `damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online-onnx`
- `damo/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727-onnx`
- `thuduj12/fst_itn_zh`

下载时间取决于网络状况，通常需要几分钟到十几分钟。

### 8.2 确保首次启动成功

- 确认 SAE 实例能访问公网
- 确认 NAS 挂载成功
- 耐心等待日志输出

### 8.3 首次启动成功后

模型文件会持久化保存在 NAS 的 `/workspace/models` 目录中。后续重启或重新部署时：

- 直接从 NAS 读取模型
- 启动时间从"几分钟"缩短到"几十秒"
- 不再依赖公网下载

---

## 九、验证服务

### 9.1 查看启动日志

在 SAE 控制台查看应用日志，或在实例 Webshell 中执行：

```bash
tail -f /workspace/models/log.txt
```

> 注：由于推荐配置使用 `exec` 直接输出到 stdout，日志主要在 SAE 控制台日志中查看。

### 9.2 成功标志

当日志中出现以下内容，表示服务启动成功：

```
asr model init finished. listen on port: 10095
```

### 9.3 功能测试

在本地或测试机上执行：

```bash
# 下载测试客户端
wget https://raw.githubusercontent.com/alibaba-damo-academy/FunASR/main/runtime/python/wss_client_asr/funasr_wss_client.py

# 执行测试
python3 funasr_wss_client.py \
  --host <SAE地址> \
  --port <SAE端口> \
  --mode 2pass \
  --audio_in test.wav
```

---

## 十、资源配置建议

### 10.1 内存

FunASR Paraformer-large ONNX 模型加载后占用约 3.8 - 4.2 GB 内存。

| 场景 | 建议内存 |
|------|---------|
| 最小可用 | 8 GB |
| 32 路并发 | 8 - 16 GB |
| 64 路并发 | 16 - 32 GB |

### 10.2 CPU

核心原则：

```
decoder-thread-num × model-thread-num ≈ vCPU 核心数
```

| 并发路数 | vCPU | 内存 | decoder-thread-num | model-thread-num | io-thread-num |
|---------|------|------|-------------------|------------------|---------------|
| 16 路 | 4 | 8 GB | 4 | 1 | 4 |
| 32 路 | 8 | 16 GB | 8 | 1 | 8 |
| 64 路 | 16 | 32 GB | 16 | 1 | 8 |

> SAE 的 vCPU 为逻辑核，按实际并发需求选择，避免过度配置。

### 10.3 存储

| 项目 | 建议 |
|------|------|
| NAS 容量 | 至少 10 GB |
| 存储类型 | 通用型即可，性能型更好 |
| 容器临时存储 | 不存放模型，仅用于镜像层 |

---

## 十一、运维与优化

### 11.1 日志管理

推荐配置已将日志输出到 SAE 控制台（stdout），不会占用 NAS 空间。

如果确实需要文件日志，建议配置日志轮转，避免单文件无限增长。

### 11.2 热词更新

热词文件路径：`/workspace/models/hotwords.txt`

格式示例：

```
阿里云 100
语音识别 90
```

修改后重新部署生效。

### 11.3 模型更新

- 更新启动命令中的模型 ID
- 重新部署后，FunASR 会自动下载新模型到 NAS
- 或手动上传新模型到 NAS 对应目录

### 11.4 高可用

- SAE 支持多实例和弹性伸缩
- 多实例前建议增加 SLB 或 API 网关做负载均衡
- 单实例并发超过 64 路时，优先考虑横向扩容

---

## 十二、常见问题

### Q1：SAE 技术栈语言选什么？

选**其它语言 / 自定义运行时 / 容器镜像**。FunASR 是完整容器镜像，不需要 SAE 提供 Java/Python/Go 运行时。

### Q2：`--privileged=true` 在 SAE 是否必需？

FunASR 官方 Docker 命令包含 `--privileged=true`，主要用于本地访问声卡设备。SAE 通常不支持 privileged 模式，但实时语音听写通过 WebSocket 接收音频流，不依赖宿主机声卡，一般可以正常运行。

### Q3：模型下载失败怎么办？

- 确认 SAE 实例能访问公网
- 确认 NAS 挂载成功
- 检查 SAE 安全组是否放行出站访问

### Q4：内存 OOM 怎么办？

- 提升 SAE 实例内存至 8 GB 以上
- 减小 `--decoder-thread-num`
- 确认已去掉 `--lm-dir`

### Q5：实时性不够怎么办？

- CPU 版在高并发时 RTF 可能接近或超过 1
- 增加 vCPU 或实例数量
- 如需严格实时，考虑 GPU 部署方案

---

## 参考文档

- [FunASR 实时语音听写服务开发指南](https://github.com/modelscope/FunASR/blob/main/runtime/docs/SDK_advanced_guide_online_zh.md)
- [Docker 安装部署 FunASR - 阿里云开发者社区](https://developer.aliyun.com/article/1708604)
- [挂载 NAS 存储卷实现持久化存储 - 阿里云帮助中心](https://help.aliyun.com/zh/cs/user-guide/use-nas-dynamic-storage-volumes)
- [FunASR 语音识别性能优化：CPU 线程数配置指南](https://devpress.csdn.net/v1/article/detail/151247521)
