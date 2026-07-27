---
kind: external_dependency
name: 火山引擎 AI 服务平台
slug: volcengine
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### 火山引擎语音和AI服务
- 角色：主要的第三方AI服务提供商，提供ASR语音识别、TTS语音合成、LLM大模型等服务
- 集成点：通过多种适配器接入，包括 DoubaoASR、DoubaoStreamASR、HuoshanDoubleStreamTTS、DoubaoLLM 等
- 使用模式：支持流式和非流式API调用，提供丰富的音色选择和情感控制参数
- 关键特性：支持双向流式TTS、边缘大模型网关、多语言识别、热词定制等功能
- 认证方式：通过 appid 和 access_token 进行API调用认证
- 验证：参考火山引擎官方控制台获取API密钥和服务配置