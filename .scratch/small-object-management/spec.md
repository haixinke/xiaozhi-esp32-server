# 小物件管理 (Small Object Management)

Status: ready-for-agent

## Problem Statement

运营需要在智控台维护一批"小物件"：每个物件有一张图片和一段"物件背后的故事"，供后续内容侧使用。目前没有任何入口可以录入这类内容——运营既没有地方建物件，也没有地方维护物件类型。

同时，物件类型必须可扩展：运营会不断新增类型，改一次类型不该发一次版。项目已有的字典管理（`system-web.menu` 之外那套 `sys_dict_type` / `sys_dict_data`）正是为这种运营可维护的枚举准备的，但字典管理页本身只负责建设类型，没有任何业务页面能消费一个"物件"类型下的内容。

对照参考：游戏管理（`ai_game`）已经验证了"业务表存字典 `dict_value`、名称由字典 `dict_label` 翻译显示、页面在运营管理菜单下"这套模式，小物件应当沿用同一套模式，而不是另起一套。

## Solution

在智控台"运营管理"菜单下新增"小物件管理"子菜单。运营在字典管理中新建一个「小物件类型」大类并维护其小类，然后到小物件管理页面为每个小类配一张图片和一段故事。

小物件管理页面提供：分页列表（图片、类型、状态、更新时间）、新增（从字典下拉选择尚未被使用的物件类型，上传图片，填写故事）、编辑、启用/停用、删除（软删，列表不再显示）。

小物件表不保存物件名称，只保存字典 `dict_value`；页面上的类型名称实时由字典翻译。这样运营在字典管理里改了一个小类的显示名，小物件管理页立刻跟着变，两处不会出现名字不一致。

本期只交付配置层：不建立"某个用户/设备拥有某个物件"的归属关系，也不向小程序或设备暴露任何读取接口。

## Requirements

R1: 运营管理菜单下存在"小物件管理"子项，可见性跟随同组菜单（superAdmin），点击可进入页面。

R2: 数据库迁移自动创建 `ai_small_object` 表与 `SMALL_OBJECT_TYPE` 字典大类；不预置任何小类；迁移只新增 changeset，不改动已有 changeset。

R3: 小物件管理页展示分页列表，列为图片缩略图、类型名称、状态、更新时间；列表只包含未删除记录。

R4: 列表按物件类型的字典排序升序排列，与字典管理页中小类的顺序一致；物件表自身不存排序字段。

R5: 列表与详情中的类型名称由字典 `dict_label` 翻译，翻译不到时回落显示原始 `dict_value`。

R6: 新增弹窗的类型下拉只列出尚未被未删除物件占用的字典小类；下拉数据取自后端接口而非字典全量列表。

R7: 新增保存服务端校验：类型存在于 `SMALL_OBJECT_TYPE` 字典、该类型在未删除记录中未被占用、图片非空、故事非空且长度不超过 2000。

R8: 物件图片经独立上传场景 `small-object` 上传，允许 png/jpeg/webp、单文件上限 2MB、不允许 svg；图片 URL 以完整 OSS URL 存入表中。

R9: 编辑弹窗回填当前值，当前类型始终可选，其他已被占用的类型不出现在下拉中；类型改为另一个未占用类型可成功。

R10: 列表内可直接启用/停用，切换只提交 id 与目标状态，不回传图片与故事。

R11: 删除为软删，列表与详情均按"已删除 = 不存在"处理；删除后可以为同一类型重新创建物件。

R12: 物件不存在、物件类型不存在、该类型已被占用三种失败各有独立错误码，并在全部七套 `messages_*.properties`（含无语言后缀的基包 `messages.properties`）中提供文案。

R13: `SmallObjectService` 集成测试覆盖保存成功、类型重复拒绝、类型不存在拒绝、故事超长拒绝、更新（含改类型与保留自身类型）、软删后不可见、列表按字典排序、状态切换不影响内容。

R14: 根 `GLOSSARY.md` 收录「小物件 (Small Object)」并明确其与「道具 (Item)」是不同概念。

## User Stories

以下为叙述性背景，不作为可验证的验收项；可验证的约束见上方 Requirements。

运营人员在运营管理菜单下需要一个小物件管理入口，可以从字典中挑选尚未使用的物件类型，为每个类型配一张图（png/jpeg/webp、2MB 内）和一段最长 2000 字的故事，故事输入框显示字数统计，图片与故事都必填，避免出现"有类型没内容"的空物件。

运营人员需要能分页浏览全部物件，在列表里同时看到缩略图、类型名称、启用状态和更新时间，以便核对配置和判断哪些是最近调整过的。

运营人员需要能编辑已有物件（替换图片、改写故事、换到另一个未占用类型），也需要能在列表里直接启用/停用；停用/启用不应要求重新提交图片和故事，避免覆盖他人刚改过的内容。

运营人员需要能删除物件；删除后记录从列表消失且无法恢复，不提供回收站入口，操作语义保持简单。

运营人员需要列表顺序与字典管理页中小类的顺序一致；在字典里调整顺序后，小物件列表顺序自动跟着变，不需要二次维护。

运营人员需要能被阻止保存无效数据：选择不存在的类型、为同一类型建第二条记录、故事超长，都应在保存时被拒绝并给出准确提示。运营人员还需要能在字典管理里自由新增/改名/排序/删除小物件小类，使物件类型可随内容规划扩展而无需发版。

系统管理员需要小物件表与「小物件类型」字典大类随数据库迁移自动创建，无需手工建表或插数据；迁移脚本只新增 changeset，避免已部署环境因校验和变化而启动失败。

开发者需要三种失败场景（物件不存在、类型不存在、类型已占用）各有独立错误码与七套文案（含默认基包），以便前端给出准确提示；上传场景需按目录与限制隔离，避免小物件图片与游戏图标、涂鸦作品混在同一路径；同时领域术语「小物件」需要写进根 GLOSSARY，明确不与已有的「道具 (Item)」混淆。

## Implementation Decisions

### 领域概念区分（重要）

- **小物件 (Small Object)** 与现有的**道具 (Item)** 是两个不同概念。道具指 `modules/item` 下那套消耗品/服装/音量配额/亲密度（表 `ai_item_sku` 等），有库存与消耗语义。小物件是纯内容记录（一张图 + 一段故事），没有归属、没有库存、没有消耗。二者之间不存在任何引用或归属关系。
- 小物件表不保存物件名称。物件名称即字典小类的 `dict_label`，运行时由字典翻译。

### 模块与分层

- 新建后端模块 `xiaozhi.modules.smallobject`，分层照抄 game 模块：entity / dao / service(+impl) / controller / dto / vo
- 前端 manager-web 新增小物件管理页面与 API 模块，页面结构参照游戏管理页面
- 不新增任何前端权限码、不往 `sys_params` 的 `system-web.menu` 里加功能开关（运营管理这一组菜单目前都是硬编码可见性，小物件跟随）

### Schema（Liquibase 新增 changeset，只增不改）

新 changeset id 按 `yyyyMMddHHmm` 递增，在主 changelog 末尾追加注册块。同一 changeset 内完成建表与字典大类初始化。

新表 `ai_small_object`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK | 是 | 主键，走全局 `ASSIGN_ID`（雪花），DDL 不写 AUTO_INCREMENT |
| object_type | VARCHAR(64) | 是 | 字典 `SMALL_OBJECT_TYPE` 的 `dict_value` |
| image_url | VARCHAR(512) | 是 | 物件图片完整 OSS URL |
| story | VARCHAR(2000) | 是 | 物件背后的故事，纯文本 |
| status | INT | 是 | 1 启用 / 0 停用，DEFAULT 1 |
| deleted | TINYINT | 是 | 0 未删除 / 1 已删除，DEFAULT 0 |
| creator / create_date / updater / update_date | | 否 | 跟随 BaseEntity 与 `FieldMetaObjectHandler` 惯例 |

索引：`object_type` 普通索引、`status` 普通索引。

**不建唯一索引**。原因见下方"软删与唯一性"。

字典初始化（同一 changeset 内 INSERT）：

- `sys_dict_type` 一行：`SMALL_OBJECT_TYPE` / 小物件类型 / remark 说明其用途，id 取当前未占用区间
- `sys_dict_data` **不插入任何小类**：小类完全由运营在字典管理里维护，不预置示例内容
- 插入前先 `delete from ... where id = ...` / `where dict_type_id = ...`，与既有 changeset 的幂等写法一致

### 后端接口

管理端挂在 `/admin/small-object`，每个方法 `@RequiresPermissions("sys:role:superAdmin")`（与游戏管理一致）：

- `GET /admin/small-object/page` — 分页列表，只返回 `deleted = 0`
- `GET /admin/small-object/{id}` — 详情
- `POST /admin/small-object/save` — 新增
- `PUT /admin/small-object/update` — 更新
- `PUT /admin/small-object/update-status` — 只更新 `{id, status}`，用于列表内启用/停用
- `DELETE /admin/small-object/delete/{id}` — 软删
- `GET /admin/small-object/object-types` — 返回 `[{key, name, used}]`，供前端类型下拉渲染与过滤

`update-status` 独立于 `update` 是刻意的：故事字段最长 2000 字，若切换状态走完整更新，会把整条记录（含故事全文）回传，且并发下会用陈旧副本覆盖别人刚改的故事。

`object-types` 的 `used` 由后端统计未删除记录算出。前端之所以不自己从列表推算，是因为列表分页，跨页的占用关系在前端不可见。

#### 保存/更新校验

- `objectType` 必须存在于 `SMALL_OBJECT_TYPE` 字典（服务端查字典，不信前端下拉）
- 同一 `objectType` 在未删除记录中只允许一条；`update` 时排除自身 id
- `imageUrl` 非空
- `story` 非空且长度 ≤ 2000
- 唯一性冲突、类型不存在、记录不存在分别返回独立错误码

#### 软删与唯一性

- 软删走 `deleted TINYINT` + Service 内显式 `deleted = 0` 过滤，**不引入 MyBatis-Plus `@TableLogic`**。全仓库没有 `@TableLogic` 用法，且 ADR 0006 已明确否决它（全局自动过滤会波及智控台管理员的全量视图）。
- **不建 DB 唯一索引**。软删保留行后，`(object_type)` 唯一索引会阻止"删掉某类型物件后重建同类型物件"；改成 `(object_type, deleted)` 复合索引则同一类型只允许存在一条已删记录，删第二次仍然撞键（ADR 0006 背景第 2 点记录过同一个坑）。本表是运营维护的内容库，写并发几乎不存在，唯一性由 Service 层 count 校验承担。
- 需要手动过滤的读取点共三处：分页列表、详情、唯一性校验。三处都在同一个 Service 内，不漏。
- `delete` 对已删除或不存在的 id 返回"物件不存在"，不做幂等成功。

#### 列表排序

按物件类型的字典 `sort` 升序。实现：先取字典有序 code 列表，再据此排列物件，使页面顺序与字典管理页看到的顺序永远一致。物件表自身不存排序字段——字典已有排序，再存一份就是两处真相。

#### 类型被删除/停用后

已引用某类型、但该类型后来在字典里被删除或停用时，不做任何处理：列表照常显示该物件，类型名称翻译不到时回落显示原始 `dict_value`。字典删除前不检查引用，是本期接受的已知缺口。

### 图片上传

- 复用现有 `POST /upload/image?scene=xxx`（阿里云 OSS），表内存返回的完整 URL
- `UploadScene` 追加 `SMALL_OBJECT("small-object", {png, jpeg, webp}, 2MB)`
- **不复用 `game-icon` 场景**：一是语义隔离，二是 `ImageUploadService` 的扩展名映射并不支持 svg（`extensionOf` 只映射 png/jpeg/webp），照抄 `game-icon` 的 svg 白名单会产出缺扩展名的 OSS key
- 前端上传走 `el-upload` 的 `http-request` 自定义上传（与游戏管理一致），以便携带 Bearer token
- `scene` 必须拼在 URL query 上，不能放进 FormData

### 前端

- 运营管理下拉菜单新增"小物件管理"子项。该组 label 目前是硬编码中文（不走 i18n），小物件保持一致，避免触发六语言 key 对齐校验
- 菜单高亮的 `$route.path === ...` 判断在该组件内出现两处（下拉容器 class 与图标 filter），两处都要补上新路由，否则菜单不会高亮
- 路由表、菜单 path 映射各加一项
- 页面列：图片缩略图、类型名称、状态、更新时间、操作
- 新增/编辑弹窗：类型下拉（新增时过滤 `used=true` 的项；编辑时把当前类型强制加回）、图片上传与预览、故事 textarea（`maxlength=2000`，显示字数统计）
- 校验为提交前的前端提示，后端仍完整校验（前端校验是体验，不是安全边界）
- 类型下拉的数据来源是 `object-types` 接口，不是字典全量列表——字典里混有 `FIRMWARE_TYPE`、`MOBILE_AREA` 等非物件类型，拉全量会污染下拉

### 错误码

在游戏管理错误码区间之后为小物件分配独立连续区间，覆盖：物件不存在、物件类型不存在、该类型已被占用。错误文案需同步到全部七套 `messages_*.properties`（含无语言后缀的基包 `messages.properties`）；DTO 校验文案同步到七套 `validation_*.properties`。

## Testing Decisions

- 好测试的标准：只测外部可观察行为——Service 方法的输入输出、校验拒绝、状态流转、软删后的可见性。不测实现细节（SQL 字面量、私有方法、框架装配、Mapper XML）。
- 单接缝：`SmallObjectService` 层集成测试，真实 dev 库上下文，`@ActiveProfiles("dev")` + `@AfterEach` 清理本用例产生的行。
- 先例：`GameServiceTest`（`src/test/java/xiaozhi/modules/game/GameServiceTest.java`），同菜单同形态的最近一次实现。
- 覆盖：
  - save 成功路径（含图片与故事落库）
  - 同一 `objectType` 第二次 save 被拒绝
  - `objectType` 不在字典中被拒绝
  - `story` 超长被拒绝
  - update 成功，且能把 `objectType` 从 A 改为 B（B 未被占用时）
  - update 改成已被占用的类型被拒绝
  - update 保留自身类型不报重复
  - delete 后 `get` 返回"物件不存在"
  - delete 后 page 不再返回该记录
  - page 按字典 sort 排序
  - `updateStatus` 只改状态，不动图片与故事
  - `object-types` 的 `used` 正确反映占用情况
- 不测：Controller（薄壳，只有 Shiro 注解与参数透传）、前端页面（仓库现有运营管理页面均无前端测试，为此单独新起前端测试接缝是净增成本）
- 每条测试使用独立数据，`@AfterEach` 物理清理（软删的行也要清，否则 dev 库会持续累积）

## Out of Scope

- 任何 C 端/小程序/设备侧的读取接口（`/app/...`）。本期无消费方
- "某个用户/设备拥有某几个物件"的归属关系与关联表
- 物件在聊天、故事引擎、生图等流程中的实际使用
- 回收站 / 已删除记录的查看与恢复
- 字典删除或停用被引用类型时的反向拦截与提示
- 列表按名称搜索、按状态筛选
- 物件表自身的排序字段
- 前端页面的自动化测试
- 不写 ADR：本决策可逆、不意外、无实质 trade-off，不满足"难以回退 + 缺少上下文会困惑 + 真实权衡"三条件

## Further Notes

- 领域词条需写入根 `GLOSSARY.md`「蛋宝宝」段内，明确"小物件"与"道具 (Item)"是不同概念，并记录"字典删除不做引用检查"这一已知缺口。
- 与 `ai_game` 的关键差异（避免后人照抄错）：`ai_game` 有 `uk_ai_game_game_type` DB 唯一索引且物理删除；`ai_small_object` 既无唯一索引也非物理删除，唯一性靠 Service、删除靠 `deleted` 标记。这个差异是有意为之，理由见"软删与唯一性"。
- 参考实现：`ai_game` 及其 game 模块、`GameManagement.vue`、`apis/module/game.js`。