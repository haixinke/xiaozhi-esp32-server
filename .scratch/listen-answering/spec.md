# 听你说·小程序答题后端

Status: ready-for-agent

## Problem Statement

蛋宝宝小程序用户想玩「听你说」：点开入口后回答一组题目，表达自己的偏好与想法。目前题库已在智控台建好（题目分组 + 题目），但小程序端没有任何后端能力支撑答题交互：没有取题接口，用户答案也无处保存。

## Solution

后端服务（manager-api）新增两个面向小程序的接口：

1. **抽题**：用户点开「听你说」入口，后端随机抽取一个该用户**未答过的题目分组**，返回组内全部题目与选项。
2. **提交答案**：用户答完该组所有题目后，一次性提交整组答案，后端保存。

每道题从 A-E 五个选项中选一个；E 选项为「都不是」，选中后用户**可以**（非必须）输入自定义文本答案（≤200 字）。当题库中所有分组用户都已答过时，抽题接口返回专用业务码，前端据此展示提示文案。

只实现后端服务部分，不含小程序前端。

## User Stories

1. As a 蛋宝宝小程序用户, I want to 点开听你说入口后获得一组题目, so that 我可以开始答题
2. As a 蛋宝宝小程序用户, I want to 获得的题目组是我从未答过的, so that 我不会重复回答相同内容
3. As a 蛋宝宝小程序用户, I want to 每道题从 A-E 五个选项中选一个, so that 我可以快速作答
4. As a 蛋宝宝小程序用户, I want to 选 E（都不是）时可以输入自己的文字答案, so that 现成选项不符合时我也能表达
5. As a 蛋宝宝小程序用户, I want to 选 E 时不强制输入文字, so that 我不想写字时也能完成作答
6. As a 蛋宝宝小程序用户, I want to 自定义文本限制在 200 字以内, so that 答案长度可控
7. As a 蛋宝宝小程序用户, I want to 答完一组后一次性提交, so that 中途退出不会留下半截数据
8. As a 蛋宝宝小程序用户, I want to 中途退出后重新进入能再次抽到同一组（或另一组未答组）, so that 我的进度不会因退出而丢失答案
9. As a 蛋宝宝小程序用户, I want to 所有组都答完后看到明确提示, so that 我知道没有新题可答了
10. As a 小程序前端开发者, I want to 抽题接口返回组 id、组名、题目列表（题干+选项）, so that 我可以直接渲染答题页
11. As a 小程序前端开发者, I want to 全部答完时接口返回可识别的业务码（HTTP 200）, so that 我可以据此切换文案而不必解析错误
12. As a 小程序前端开发者, I want to 重复提交已答题目不会报错, so that 网络重试/双击不会产生失败弹窗
13. As a 运营者, I want to 在智控台删除题目后用户历史答案仍保留, so that 用户数据不因内容调整而丢失
14. As a 后端维护者, I want to 答题接口复用现有 Shiro Bearer token 鉴权, so that 不引入新的认证体系

## Implementation Decisions

### 模块归属

- 全部落在 manager-api 的 `xiaozhi.modules.listen` 模块，与题库管理代码同包。
- 不涉及 xiaozhi-server。

### 抽题接口

- `GET /listen/draw`，需认证（`@RequiresPermissions("sys:role:normal")`，与 Pet 模块普通用户接口一致），用户身份取 `SecurityUser.getUserId()`。
- 抽题单位 = **题目分组**（`ai_listen_group`），不跨组拼题。组内有几道题就返回几道（不固定 5）。
- 排除逻辑：用户在 `ai_listen_answer` 中已有**该组任何一题**的答案，整组即视为已答过，不再被抽到。
- 随机实现：SQL `ORDER BY RAND() LIMIT 1`（已在 Oceanbase 开发库实测可用）。
- 返回：`{groupId, groupName, questions: [{id, text, options: [{key, text}]}]}`。**不返回分类相关信息**。
- 全部答完：HTTP 200 + `Result{code: 10382}`（ErrorCode 听你说段续号），data 为空，前端识别该码出文案。

### 提交答案接口

- `POST /listen/answers`，需认证。
- 请求体：`{groupId, answers: [{questionId, optionKey, customText?}]}`，整组一次性提交。
- 校验：仅 `customText` ≤ 200 字；**不校验** customText 必须对应 E 选项（信任前端只在选 E 时传文本）。
- 幂等：`(user_id, question_id)` 已存在则忽略该条，整体仍返回成功（支持网络重试）。
- optionKey 必填（A-E）；customText 可空。

### Schema 变更（Liquibase 新 changeset）

新表 `ai_listen_answer`：

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键（雪花 ASSIGN_ID） |
| user_id | BIGINT NOT NULL | 答题用户（sys_user.id） |
| group_id | BIGINT NOT NULL | 所属题目分组（冗余，便于按组判答过） |
| question_id | BIGINT NOT NULL | 题目 id |
| option_key | CHAR(1) NOT NULL | 所选选项 A-E |
| custom_text | VARCHAR(200) NULL | 自定义文本（选 E 时可填，非强制） |
| creator/create_date/updater/update_date | 沿用惯例 | |

- UNIQUE KEY `(user_id, question_id)` —— 幂等由数据库兜底。
- KEY `(user_id, group_id)` —— 支撑抽题排除查询。
- **物理删除**，不设 deleted 列，与 ADR 0010 听你说物理删除决策一致。
- **不建外键**：题目/分组物理删除后答案保留（用户历史数据），查询只 join 存活题目。

### 术语

- 业务名为「**听你说**」（GLOSSARY.md 已有词条），用户口语「听我说」为同一物。
- GLOSSARY.md「听你说题库」词条需更新：去掉「本期不向小程序暴露取题接口」；新增答题相关词条。

## Testing Decisions

- **测试缝**：Service 层 `@SpringBootTest` 集成测试打真实 dev 库，单一缝。
- **先例**：`ListenGroupServiceTest` / `ListenImportServiceTest`（自造 LISTEN_CATEGORY 字典小类与题库数据，AfterEach 物理清理，避免污染 dev 库）。
- 只测外部行为（抽题返回内容、排除语义、全答完信号、提交幂等、超长文本拒绝），不测实现细节（具体 SQL、内部方法）。
- 覆盖用例：
  - 抽题：无任何答案时返回某组全部题目与选项
  - 抽题：已答组被排除（组内只答过一题也排除整组）
  - 抽题：所有组都答过 → 返回 10382 业务码
  - 提交：整组保存成功，答案字段正确落库
  - 提交：重复提交同一题幂等（不报错、不产生重复行）
  - 提交：customText 超过 200 字 → 参数校验失败
  - 提交：optionKey 为空 → 参数校验失败
- Controller 层不单独测：薄层，权限注解与 Pet 模块同构。

## Out of Scope

- 小程序前端（答题页 UI、状态管理、调用封装）。
- 智控台侧任何改动（题库管理已完成）。
- 题目类型扩展（当前仅 SINGLE 单选）。
- 跨组随机拼题。
- 用户查看/修改历史答案的接口。
- 答题数据统计/分析。

## Further Notes

- `ai_listen_answer` 表中的 `group_id` 是冗余列：可通过 question_id join 推出，但按组排除是抽题热路径，冗余可避免 join。
- 题目被物理删除后，该组对老用户「部分答过」——按组判答过语义下整组自然不再出现，无需特殊处理。
- 提交接口是否校验「answers 必须覆盖组内全部题目」未做强制：前端整组提交是约定，后端按落库行数判答过，漏交的题该组仍算已答（组内任一题有答案即排除）。如后续出现漏交问题再加校验。
