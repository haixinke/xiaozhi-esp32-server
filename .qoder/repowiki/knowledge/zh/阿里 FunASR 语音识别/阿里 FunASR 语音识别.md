---
kind: external_dependency
name: 阿里 FunASR 语音识别
slug: funasr
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### 阿里 FunASR 本地语音识别服务
- 角色：开源的离线语音识别方案，主要使用 SenseVoiceSmall 模型进行语音转文字
- 集成点：支持本地部署和服务器两种模式，通过 fun_local 和 fun_server 两种类型接入
- 使用模式：本地模式需要下载模型文件到 models/SenseVoiceSmall 目录，服务器模式通过HTTP API调用
- 关键特性：支持多语言识别（中文、英文、日文、韩文等），支持实时流式识别
- 部署要求：本地模式需要较大的存储空间（模型文件约几百MB）
- 验证：参考 FunASR 官方文档获取模型下载链接和部署指南