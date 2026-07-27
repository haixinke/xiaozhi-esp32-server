---
kind: external_dependency
name: 微软 Edge TTS 语音合成
slug: edge-tts
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### 微软 Edge TTS 免费语音合成服务
- 角色：免费的在线TTS服务，提供高质量的中文语音合成能力
- 集成点：通过 edge_tts Python库接入，配置在 EdgeTTS 适配器中
- 使用模式：支持多种音色选择（如 zh-CN-XiaoxiaoNeural），可配置语速、音量等参数
- 关键特性：完全免费，无需API密钥，支持多语言语音合成
- 限制：并发连接数有限制，高并发场景可能需要付费服务替代
- 验证：参考 edge_tts 官方文档了解支持的音色和参数配置