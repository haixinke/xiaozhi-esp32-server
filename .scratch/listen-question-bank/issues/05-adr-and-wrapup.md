# 05: 收尾：ADR 0010 + 图谱更新 + 全量验证

**What to build:** 功能完成后的文档与验证收尾：撰写 ADR 0010 记录「题库物理删除」决策——题库无用户侧历史记录关联，软删保护对象不存在，为例外于 ADR 0006 软删惯例的有意决策；运行 `graphify update .` 更新知识图谱；manager-api 与 manager-web 全量测试通过。

**Blocked by:** 03, 04 (JSON 导入、编辑与删除)

**Status:** ready-for-agent

- [ ] `docs/adr/0010-listen-question-physical-delete.md` 按 ADR 格式撰写（背景：ADR 0006 惯例；决策：物理删除；理由：无答题记录等历史关联、低频运营操作、二次确认兜底）
- [ ] `graphify update .` 执行，图谱含 listen 模块
- [ ] manager-api 全量测试绿
- [ ] manager-web `npm run test:unit` 绿
