# Sandcastle E2E smoke-test spec

R1: 每个测试 issue 只增加范围明确的纯单元测试，不修改生产行为。
R2: 新增测试不依赖 Spring 上下文、数据库、Redis、网络或本地时区。
R3: manager-api 全量校验通过后，issue 才能关闭并合并。
R4: 单 issue 审查与最终批次审查都必须绑定实际验证过的 commit。
