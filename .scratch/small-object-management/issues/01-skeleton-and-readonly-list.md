# 01: 小物件管理骨架打通（建表 + 字典大类 + 只读列表）

**What to build:** 运营在智控台"运营管理"菜单里看到"小物件管理"子项，点击能进入页面，页面顶部有"新增小物件"按钮（本片不接功能），下方是分页列表，列为图片缩略图、类型名称、状态、更新时间。空库时正常显示空态、不报错。`ai_small_object` 表与 `SMALL_OBJECT_TYPE` 字典大类随 Liquibase 迁移自动创建，部署后在字典管理页能看到这个大类并能在其下自由新增小类；给小类补上数据后，小物件列表能正确把 `dict_value` 翻译成 `dict_label` 显示，翻译不到时回落显示原始 code。列表只展示未删除记录，顺序与字典管理页中小类的排序一致。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 新增 Liquibase changeset（只新增文件，不改动任何已有 changeset），在主 changelog 末尾追加注册块；同一 changeset 内建立 `ai_small_object` 表并插入 `SMALL_OBJECT_TYPE` 字典大类，不预置任何小类示例数据；插入语句带 `delete ... where` 前缀保证可重复执行
- [x] `ai_small_object` 表含主键、物件类型（存字典 `dict_value`）、图片 URL、故事、状态、删除标记、创建/更新审计字段；主键走 `application.yml` 已配置的全局 `ASSIGN_ID`（雪花），DDL 因此不写 `AUTO_INCREMENT`——这是与 `ai_game` 表 DDL 的刻意差异，照功能分层照抄 game 模块，但主键策略跟随全局配置；建物件类型与状态的普通索引；不建任何唯一索引
- [x] 新增错误码"物件不存在"，并同步到全部七套 `messages_*.properties`（含无语言后缀的 `messages.properties` 基包，`application.yml` 的 `basename: i18n/messages` 使它是默认兜底包；只补六套会漏掉默认 locale，且后端没有像前端 `check:i18n` 那样的自动对齐校验）
- [x] 新建后端模块（entity/dao/service+impl/controller/dto/vo），分页接口只返回未删除记录，详情接口对不存在或已删除的记录返回"物件不存在"
- [x] 列表排序按物件类型的字典 sort 升序，而非按 id 或更新时间
- [x] 详情/列表中的类型名称由字典 `dict_label` 翻译，翻译不到时回落显示原始 `dict_value`
- [x] 管理端接口挂 superAdmin 权限，与运营管理其他子菜单一致
- [x] 前端运营管理下拉菜单新增"小物件管理"子项（硬编码中文，不走 i18n），菜单高亮的两处路径判断都要补上新路由
- [x] 前端路由表与菜单路径映射各加一项
- [x] 小物件管理页面：分页列表，列含图片缩略图、类型名称、状态标签、更新时间；显示"新增小物件"按钮但本片不实现其功能
- [x] 根 GLOSSARY 新增「小物件 (Small Object)」词条，明确与已有的「道具 (Item)」是两个不同概念（道具是消耗品/服装/音量配额那套，有库存与消耗语义；小物件是纯内容记录），并记录"字典删除被引用类型时不做引用检查"是已知缺口
- [x] Service 集成测试：空库分页正常、只返回未删除记录、按字典 sort 排序、详情查不存在时报"物件不存在"、类型名称能按字典翻译、字典中不存在的 code 回落显示原始值

## Comments

**2026-10-27 实现（RALPH）**

- Liquibase `202610271000`（只新增文件，主 changelog 末尾注册）：建 `ai_small_object` 表 + 插入 `SMALL_OBJECT_TYPE` 字典大类（id 107）。不预置任何小类；字典插入带 `delete from sys_dict_type where id = 107` 前缀，可重复执行。dev 库已实跑：表与字典大类均创建，`DATABASECHANGELOG` 单次 `EXECUTED`，小类 0 条。
- 表结构：主键走全局 `ASSIGN_ID`（雪花，DDL 不写 `AUTO_INCREMENT`，与 `ai_game` 的刻意差异）、`object_type` / `image_url` / `story` / `status` / `deleted` + 审计字段；`object_type`、`status` 各建普通索引；**不建任何唯一索引**（软删下唯一索引会阻止同一类型删除后重建，唯一性留给后续票的 Service 层，见 ADR 0006）。
- 后端新模块 `xiaozhi.modules.smallobject`（entity/dao/service+impl/controller/vo）：`GET /admin/small-object/page`、`GET /admin/small-object/{id}`，均挂 `sys:role:superAdmin`。
- 排序在 SQL 侧 `LEFT JOIN sys_dict_data` 取字典 `sort` 完成（小类被删导致 `sort` 为 NULL 的记录排末尾，同 sort 用 `o.id` 兜底），物件表不存排序字段；`page` 内剔除调用方传入的 `orderField`/`order`，防止客户端改写顺序。
- 读取点显式过滤 `deleted = 0`，不引入 `@TableLogic`；`get` 对不存在与已软删统一抛 `SMALL_OBJECT_NOT_FOUND`(10360)。类型名称由字典 `dict_label` 实时翻译，翻译不到回落原始 `dict_value`。
- 错误码 10360 已同步全部七套 `messages_*.properties`（含无语言后缀的基包 `messages.properties`）。
- 前端：运营管理下拉新增「小物件管理」（硬编码中文），补齐菜单高亮两处 `$route.path` 判断、路由表与 `handleRouter` 路径映射；页面为分页列表（图片缩略图 / 类型名称 / 状态标签 / 更新时间）+ 占位「新增小物件」按钮（本片不接功能），列表请求带序号丢弃过期响应。
- `GLOSSARY.md` 新增「小物件 (Small Object)」词条，明确与「道具 (Item)」是不同概念，并记录"字典删除被引用类型时不做引用检查"这一已知缺口。
- `SmallObjectServiceTest` 12 例：空分页 / 软删过滤（含 total）/ 按字典 sort 排序 / 字典改序后列表跟随 / 详情不存在与已删除 / 类型名称翻译 / 未知名 code 回落 / 小类删除后仍可见并回落 / 客户端 orderField 不生效。
- 验证：`mvn ... clean verify` 988 tests, 0 failures, BUILD SUCCESS；`manager-web` 的 `test:unit`(135 pass)、`check:i18n`(6 locale × 1554 keys)、`check:template` 均通过。
- 说明：本分支从 `467c5214` 起，先前一次实现落在另一条 sandcastle 分支（`6cd954fa`）且其 changeset 已应用到共享 dev 库。为不破坏 Liquibase 校验和，本分支复用该 changeset 的完全相同内容。
