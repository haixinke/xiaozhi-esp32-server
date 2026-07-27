---
kind: external_dependency
name: OceanBase PowerMem 智能记忆系统
slug: oceanbase-powermem
category: external_dependency
category_hints:
    - vendor_identity
    - migration_status
scope:
    - '**'
---

### OceanBase 开源的记忆存储组件
- 角色：Agent记忆系统的后端存储，支持用户画像和长期记忆功能
- 集成点：通过 powermem Python库接入，配置在 Memory.powermem 适配器中
- 使用模式：支持多种数据库后端（sqlite、oceanbase、seekdb、postgres），默认使用sqlite
- 关键特性：支持用户画像提取、向量存储、智能检索等功能
- 迁移状态：当前项目从 mem0ai 迁移到 powermem，但保留了 mem0ai 的兼容性
- 成本说明：PowerMem本身免费，实际费用取决于所选LLM和数据库
- 验证：参考 OceanBase PowerMem 官方文档了解数据库配置和向量维度设置