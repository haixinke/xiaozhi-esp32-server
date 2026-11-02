# Refactor: options_json 解析逻辑三处合一

Status: resolved

## Answer

已落地：新增 `xiaozhi.modules.listen.util.ListenOptionsParser`（`Option(key, text)` record + `parse()` 静态方法，空/坏 JSON/非数组统一返回空 List + warn 日志）。三处调用点各自薄映射到目标类型：

- `ListenAnswerServiceImpl`：`submitAnswers` 的选项文本回填 + `toQuestionVo` 抽到 VO
- `ListenGroupServiceImpl`：`toQuestionVo` 抽到 VO（顺带补上了原先缺失的 warn 日志，行为不变）
- `ListenFeedbackService`：转 Map 喂 prompt 组装

新增 `ListenOptionsParserTest` 5 条用例（正常解析 / 空输入 / 非数组 / 坏 JSON / 缺字段容忍）。
`mvn test -Dtest='Listen*Test'` 64 个全绿。
Type: task

## Problem

`options_json`（`[{"key":"A","text":"..."}]`）的解析逻辑当前存在于三处，形状几乎一致（blank 检查 → readTree → isArray 检查 → 遍历 key/text → try/catch 兜底空集合），仅返回类型不同：

- `ListenAnswerServiceImpl.parseOptions`（`main/manager-api/src/main/java/xiaozhi/modules/listen/service/impl/ListenAnswerServiceImpl.java`，返回 `List<ListenDrawVO.Option>`）
- `ListenFeedbackService.parseOptions`（`main/manager-api/src/main/java/xiaozhi/modules/listen/service/ListenFeedbackService.java`，返回 `Map<String, String>`）
- `ListenGroupServiceImpl.parseOptions`（`main/manager-api/src/main/java/xiaozhi/modules/listen/service/impl/ListenGroupServiceImpl.java`，返回 `List<...>`）

「听你说反馈文案 LLM 生成」改造（2026-11-02）新增了第二处；Standards review 将其识别为低优先级 Duplicated Code smell，本期不修，留作后续 refactor 候选。

## Proposal

抽到共享工具，例如：

- `xiaozhi.modules.listen.util.ListenOptionsParser`（静态方法），或
- `ListenQuestionEntity` 的实例方法（`getOptions()` 返回解析后的 `List<Option>`）

三处统一调用，各自在共享结果上做一层薄映射（`List<Option>` ↔ `Map<key,text>`）。

## Why now / why not now

- 为什么当时不修：三处各自承载略有差异的返回类型，坏数据兜底语义独立成立；LLM 反馈改造属于功能性新增，夹带重构会扩大 diff、违反 Surgical Changes。
- 什么时候修：下一次需要动 options_json 解析行为时（例如选项结构扩展、解析失败监控、新增第四种使用方）顺手收敛；或 listen 模块稳定期做一次专项清理。

## Acceptance criteria

- 三处调用点共用同一份解析实现
- 现有测试全部通过：`ListenAnswerServiceTest` / `ListenFeedbackServiceTest` / `ListenGroupServiceTest` / `ListenGroupEditDeleteServiceTest` 等
- 坏数据兜底语义不变（解析失败返回空集合，不抛异常）
