# 01: 字典与建表：LISTEN_CATEGORY + 题库两表

**What to build:** 「听你说题库」的数据基础落地：运营可在智控台字典管理中维护「题目大类」（LISTEN_CATEGORY 字典类型，5 个大类由运营自行录入小类）；`ai_listen_group`（题目分组）与 `ai_listen_question`（题目，选项存 JSON 列）两张表建库，联合唯一约束（组名大类内唯一、题干组内唯一），均无 deleted 列（物理删除，例外于 ADR 0006，ADR 在 ticket 05 补写）。`listen` 模块骨架（Entity/Dao）可编译。Liquibase 新 SQL 文件按 `yyyyMMddHHmm.sql` 惯例加入并在 master changelog 末尾追加 changeset。

**Blocked by:** None (can start immediately)

**Status:** resolved

## Answer

已实现并合入 integration（commit eda872cb on listen/01-schema-and-dict）。偏差：排序列名为 `sort_order`（实体 `sortOrder`）——`order` 是 MySQL 保留字，且仓库惯例为 `sort_order`。测试：`ListenSchemaContractTest` + `ListenMigrationIntegrationTest`（OceanBase 测试库 egg_listen_test 验证联合唯一约束与幂等字典初始化），52 测试全绿。

- [ ] `sys_dict_type` 新增 LISTEN_CATEGORY（id 108），Liquibase 幂等初始化
- [ ] `Constant.DictType` 新增 LISTEN_CATEGORY 枚举
- [ ] `ai_listen_group` 建表：id/category_code/name/order/审计字段，联合唯一 `(category_code, name)`，无 deleted
- [ ] `ai_listen_question` 建表：id/group_id/text/type/options_json/order/审计字段，联合唯一 `(group_id, text)`，无 deleted
- [ ] `db.changelog-master.yaml` 末尾追加 changeset，不改动已有 changeset
- [ ] Entity/Dao 骨架可编译，应用启动 Liquibase 迁移通过
