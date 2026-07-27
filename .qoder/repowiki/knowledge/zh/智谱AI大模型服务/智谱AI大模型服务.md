---
kind: external_dependency
name: 智谱AI大模型服务
slug: zhipuai
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### 智谱AI大语言模型服务
- 角色：免费的LLM服务提供商，主要使用 glm-4-flash 模型进行对话生成
- 集成点：通过 OpenAI 兼容接口接入，配置在 ChatGLMLLM 适配器中
- 使用模式：支持标准OpenAI格式的API调用，可配置temperature、max_tokens等参数
- 关键特性：提供免费额度，适合个人使用和测试环境
- 认证方式：通过api_key进行身份验证，需要在官网注册获取
- 验证：参考智谱AI开放平台获取API密钥和模型配置