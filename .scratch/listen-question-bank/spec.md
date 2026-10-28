# 听你说题库运营 (Listen-to-You Question Bank Operations)

Status: ready-for-agent

## Problem Statement

「听你说」是蛋宝宝小程序规划中的一个小游戏（「游戏类型」字典中已存在 `LISTEN_TO_YOU`），通过与用户答题交互。目前题目只有一份离线 JSON 文件（`听你说_题库.json`），后端没有任何题库表或接口，运营无法维护题目内容——新增题目要改文件、改错字要发版，完全不可运营。

## Solution

在智控台「运营管理」菜单下新增「题库运营」页面：运营选择一个「题目大类」（字典驱动），通过 JSON 文件增量导入题目分组与题目；已有分组可在页面中查看、整组编辑（组名、题干、选项）、删除（单题或整组）。本期仅做运营端，不向小程序暴露取题接口。

## User Stories

1. As an 运营, I want 在智控台运营管理菜单下看到「题库运营」入口, so that 我能在统一位置维护听你说题库
2. As an 运营, I want 页面顶部以 Tab 展示全部题目大类（从字典 LISTEN_CATEGORY 读取）, so that 我能按大类切换查看题库
3. As an 运营, I want 每个大类下看到题目分组列表（组名、题数、排序）, so that 我能了解题库结构
4. As an 运营, I want 点击题组查看组内 5 道题的题干与选项, so that 我能核对题目内容
5. As an 运营, I want 上传 JSON 文件增量导入题库, so that 一次性完成初始题库录入
6. As an 运营, I want 导入时系统自动校验大类 id 必须已在字典管理中存在, so that 不会引入未配置的大类
7. As an 运营, I want 导入遇到同大类下重复组名或组内重复题干时整单拒绝并列出冲突明细, so that 我能修正文件后重传，不产生半截数据
8. As an 运营, I want 增量导入的新分组追加在现有顺序之后（order 按现有最大值递增）、新题追加在组内末尾, so that 已上线的题目顺序不被打乱
9. As an 运营, I want 导入成功 / 失败有明确反馈（成功写入 N 组 M 题；失败列出冲突项）, so that 我能确认导入结果
10. As an 运营, I want 在题组编辑弹窗中一次修改组名与组内全部题目（题干、选项文本）, so that 一次保存完成一组的调整
11. As an 运营, I want 编辑保存保留已有题目 id, so that 题目身份稳定
12. As an 运营, I want 能删除单道题, so that 不合适的题目可单独移除
13. As an 运营, I want 能删除整个题组（含组内题目）, so that 过时的一组内容可整体下线
14. As an 运营, I want 删除有二次确认, so that 物理删除不可恢复的风险被明示
15. As a 超级管理员, I want 题库运营接口仅超管可用, so that 普通用户无法篡改题目内容
16. As a 开发者, I want 组名在同一大类内唯一、题干在同一分组内唯一并由数据库联合唯一约束兜底, so that 脏数据无法落库
17. As a 运营, I want 大类名称由字典实时翻译展示, so that 字典管理中改名后页面同步生效

## Implementation Decisions

- **模块**：manager-api 新增 `listen` 模块，分层照抄 smallobject 先例（Controller / Service / ServiceImpl / Dao / Entity / DTO / VO / mapper XML）。manager-web 新增页面组件、API 模块、路由、HeaderBar 菜单项。
- **表结构（两张表，选项内联）**：
  - `ai_listen_group`：id（雪花 BIGINT）、category_code（字典 code）、name、order、审计字段（creator/create_date/updater/update_date），**无 deleted 列**。联合唯一约束 `(category_code, name)`。
  - `ai_listen_question`：id、group_id、text、type（当前仅 SINGLE）、options_json（选项 JSON 列，格式 `[{"key":"A","text":"..."}]`）、order、审计字段，**无 deleted 列**。联合唯一约束 `(group_id, text)`。
  - 选项不单独建表：固定 5 项、无独立生命周期，JSON 列足够。
- **物理删除（例外决策）**：题库不做软删，删除即物理删除。理由：题目无用户侧历史记录关联（答题记录本期不存在），软删的保护对象不存在；这与 ADR 0006 的软删惯例相左，需写新 ADR 记录（计划 `0010-listen-question-physical-delete.md`）。
- **大类来源**：大类取值由字典 `LISTEN_CATEGORY` 驱动（`Constant.DictType` 新增枚举；Liquibase 初始化 `sys_dict_type`，id 从 108 起；5 个小类由运营在字典管理页自行维护，不预置）。题库表只存字典 code，展示时字典实时翻译。导入时校验大类 id 必须已存在于字典，否则整单拒绝。
- **JSON 导入语义（增量）**：
  - 导入文件结构：大类数组，每大类含 groups，每组 5 题，每题含题干与 5 个选项（见 `听你说_题库.json`）。
  - 只解析最外层大类 id 用于字典校验；JSON 内的组 id、题 id **不入库**（库内 id 由后端生成）。
  - 判重（Q10-B 整单失败制）：同大类下组名重复、或同组内题干重复（含与库内已有数据比对），任一冲突即整个文件拒绝，返回冲突明细列表；无任何写入（单事务）。
  - 排序追加：新组 order = 该大类现有 max(order)+1 起递增；组内新题 order 同理。JSON 内的 order 字段仅用于决定同次导入内的相对顺序，不直接落库。
- **编辑语义**：整组提交（PUT 组详情，含组名+全部题目）。后端单事务内 diff：带 id 的更新、无 id 的新增、缺失的物理删除。题目 id 保持稳定。
- **接口契约**（前缀 `/admin/listen`，全部 `@RequiresPermissions("sys:role:superAdmin")`）：
  - `GET /group/page?categoryCode=&page=&limit=` — 题组分页（PageData 范式）
  - `GET /group/{id}` — 组详情（含组内题目与选项）
  - `PUT /group/{id}` — 整组保存（diff 写入）
  - `DELETE /group/{id}` — 整组物理删除
  - `DELETE /question/{id}` — 单题物理删除
  - `POST /import`（multipart JSON 文件）— 增量导入，返回成功统计或冲突明细
- **错误码**：ErrorCode 新增 10370 段（大类不存在、组名重复、题干重复、组不存在、题不存在、JSON 格式非法等），7 个 i18n properties 同步；DTO 校验文案同步 validation properties。
- **前端页面形态**：单页 — 顶部大类 Tab（调 `/admin/dict/data/type/LISTEN_CATEGORY`）、题组列表、题组编辑弹窗（组名 + 5 题题干 + 各 5 选项）、导入按钮（el-upload，失败弹窗展示冲突明细）、删除二次确认。
- **菜单**：前端写死菜单（HeaderBar.vue 三处改动：dropdown-item、routerPaths、active-tab OR 链），运营管理区已有 4 项（故事引擎/反馈管理/游戏管理/小物件管理），新项排在其后。路由 `requiresAuth`，可见性由 HeaderBar `superAdmin` 控制，与其他运营页一致。
- **Liquibase**：新 SQL 文件 `yyyyMMddHHmm.sql` 扁平放 `db/changelog/`，master yaml 末尾追加 changeset；只增不改已有 changeset。
- **GLOSSARY**：已新增词条——「听你说题库」「题目大类」「题目分组」「题目」，实施沿用此词汇。
- **权限**：Shiro 注解式，无需改 ShiroConfig；题库无普通用户可读接口（本期小程序不消费）。

## Testing Decisions

- **只测外部行为，不测实现细节**：Service 层入参 → DB 状态 / 异常；前端源码断言测试只断言关键接线（路由、菜单、API 注册），不断言 markup。
- **Seam（唯一新 seam 不需要——复用现有）**：
  - 后端：Service 层单测，先例 `SmallObjectServiceTest`（`xiaozhi.modules.smallobject`）。覆盖：导入校验（大类缺失拒绝 / 组名重复拒绝 / 题干重复拒绝 / 冲突明细内容 / 拒绝时零写入）、order 追加（新组接在现有 max 之后）、整组保存 diff（更新保 id / 新增 / 物理删除）、物理删除（单题、整组级联）。
  - 前端：`main/manager-web/tests/*.test.mjs`（node:test 源码断言，`npm run test:unit`），覆盖路由注册、菜单项、API 模块导出。
- 覆盖目标沿用仓库惯例（关键业务规则必有测试：判重、order 追加、diff、物理删除级联）。

## Out of Scope

- 小程序取题接口与游戏页（「听你说」游戏本身未实现，取题协议待游戏立项时定）
- 答题记录、用户侧任何功能
- 题目类型扩展（当前仅 SINGLE 单选）
- 题目的启用/停用状态（物理删除即下线）
- 大类在智控台的维护界面（复用现有字典管理页）
- manager-web 的 i18n（运营管理区菜单均为裸中文，新页面沿用同风格）

## Further Notes

- 导入文件样例：仓库根目录 `听你说_题库.json`（5 大类 × 1 组 × 5 题 × 5 选项）。
- 删除为物理删除且不可恢复，前端删除确认文案需明示。
- 参考实现：smallobject 模块（manager-api）与 SmallObjectManagement.vue（manager-web）；Excel 导入的逐行结果反馈参考 storyengine `importCaptions`。
