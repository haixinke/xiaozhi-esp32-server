# 游戏管理 (Game Management)

Status: resolved

## Problem Statement

运营需要在智控台配置蛋宝宝小程序中的游戏玩法。游戏是星星激励体系的一部分：用户玩游戏可以赚取或消耗星星。目前没有游戏配置能力，游戏参数（赚多少星、花多少星、每天能玩几次、出现在哪些入口）无法由运营调整。

## Solution

在智控台"运营管理"菜单下新增"游戏管理"子菜单，运营可对游戏配置做增删改查、启用/停用。游戏类型复用字典管理（`GAME_TYPE` 字典），游戏入口用后端枚举（小纸条、活动）。小程序提供只读查询接口，按入口拉取启用中的游戏列表。本次只交付配置层；玩游戏时实际发放/扣减星星、每日次数计数的执行逻辑由后续业务模块接入。

## User Stories

1. As an 运营人员, I want to 在运营管理菜单看到"游戏管理"子菜单, so that 我可以进入游戏配置页面
2. As an 运营人员, I want to 分页查看游戏列表, so that 我可以浏览全部已配置的游戏
3. As an 运营人员, I want to 新增一条游戏配置（选类型、填赚取/消耗星星数、每日次数上限、上传 icon、填说明、勾选入口）, so that 新游戏可以投放到小程序
4. As an 运营人员, I want to 游戏类型下拉选项来自字典管理中的 GAME_TYPE 字典, so that 类型可扩展且口径统一
5. As an 运营人员, I want to 编辑已有游戏配置, so that 我可以调整星星参数和投放入口而无需重建
6. As an 运营人员, I want to 停用某个游戏, so that 它在小程序下线但配置保留可恢复
7. As an 运营人员, I want to 重新启用已停用的游戏, so that 它重新对小程序可见
8. As an 运营人员, I want to 物理删除一条游戏配置, so that 误建的记录可以清理
9. As an 运营人员, I want to 保存时被阻止选择不存在的游戏类型, so that 配置不会指向无效类型
10. As an 运营人员, I want to 保存时被阻止填入非法入口值, so that 入口数据始终是枚举内的合法值
11. As an 运营人员, I want to 星星数量和每日次数不能填负数, so that 配置语义保持正确
12. As an 运营人员, I want to 每日次数填 0 表示不限次数, so that 免费无限玩的游戏可以表达
13. As an 运营人员, I want to 上传游戏 icon 图片（png/jpeg/webp/svg，2MB 内）, so that 小程序能展示游戏图标
14. As an 运营人员, I want to icon 和说明可以为空, so that 可以先配好核心参数后补素材
15. As an 运营人员, I want to 为一个游戏勾选多个入口（小纸条、活动）, so that 同一游戏可在多处出现
16. As an 运营人员, I want to 至少选择一个入口才能保存, so that 游戏一定有投放位置
17. As a 小程序用户, I want to 小程序按入口拉取启用中的游戏列表, so that 我在小纸条/活动入口看到可玩的游戏
18. As a 小程序用户, I want to 停用的游戏不出现在列表中, so that 我不会点到已下线的游戏
19. As a 系统管理员, I want to GAME_TYPE 字典及初始五个类型随数据库迁移自动初始化, so that 部署后无需手工插数据

## Implementation Decisions

### 模块与分层

- 新建后端模块 `xiaozhi.modules.game`，分层照抄现有字典/反馈模块模式：entity / dao / service(+impl) / controller / dto / vo
- 前端 manager-web 新增游戏管理页面 + API 模块，结构参照反馈管理页面

### Schema（Liquibase 新增 changeset，只增不改）

新表 `ai_game`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | BIGINT PK 自增 | 是 | |
| game_type | VARCHAR(64) | 是 | 字典 `GAME_TYPE` 的 code |
| earn_stars | INT | 是 | 赚取星星数，DEFAULT 0，非负 |
| consume_stars | INT | 是 | 消耗星星数，DEFAULT 0，非负 |
| daily_limit | INT | 是 | 每日每设备可玩次数上限，DEFAULT 0，0=不限 |
| icon | VARCHAR(512) | 否 | OSS URL |
| description | VARCHAR(500) | 否 | 纯文本说明 |
| entrances | VARCHAR(128) | 是 | 逗号分隔枚举 code，如 `NOTE,ACTIVITY` |
| status | TINYINT | 是 | 1 启用 / 0 停用，DEFAULT 1 |
| creator / create_date / updater / update_date | | 否 | 跟随 BaseEntity 惯例，可空 |

字典初始化（同一 changeset 内 INSERT）：

- `sys_dict_type` 一行：`GAME_TYPE` / 游戏类型 / remark 游戏管理中游戏的类型 / sort 0
- `sys_dict_data` 五行：

| dict_label | dict_value | sort |
|---|---|---|
| 一起画 | DRAW_TOGETHER | 1 |
| 听你说 | LISTEN_TO_YOU | 2 |
| 遇见你 | MEET_YOU | 3 |
| 旅行 | TRAVEL | 4 |
| 打工 | WORK | 5 |

注：`TRAVEL` 与现有 `StarConsumeBizType.TRAVEL` code 撞名但属不同枚举/字典，互不冲突，不做改名。

### 后端接口

管理端 `/admin/game/...`（权限 `@RequiresPermissions("sys:role:superAdmin")`，与运营管理其他菜单一致）：

- `GET /admin/game/page` — 分页列表
- `GET /admin/game/{id}` — 详情
- `POST /admin/game/save` — 新增
- `PUT /admin/game/update` — 更新（含启用/停用，经 status 字段）
- `DELETE /admin/game/delete/{id}` — 物理删除

save/update 服务端校验：

- `game_type` 必须存在于 `GAME_TYPE` 字典（只挡保存/更新；事后在字典管理中删除字典项不反向拦截，靠运营自觉）
- `entrances` 至少一项，每项必须在 `GameEntranceEnum` 内
- `earn_stars` / `consume_stars` / `daily_limit` 非负整数

小程序端：

- `GET /app/game/list?entrance=NOTE` — 需登录鉴权（走现有 token 机制），只返回 status=1 的记录；`entrance` 可选，传入时按包含该入口过滤

### 枚举

`GameEntranceEnum`（写死代码，变更需发版）：

- `NOTE("小纸条")`
- `ACTIVITY("活动")`

`entrances` 字段在库中存逗号分隔 code 串，如 `NOTE,ACTIVITY`。

### icon 上传

- 复用现有 `POST /upload/image?scene=xxx` 链路（阿里云 OSS）
- `UploadScene` 枚举追加 `GAME_ICON("game-icon", {png,jpeg,webp,svg}, 2MB)`
- 接受现有 OSS key 带 userId 的路径模型，不动现有上传代码
- 游戏表存返回的完整 OSS URL

### 前端

- 运营管理下拉菜单新增"游戏管理"子项，可见条件跟随同组菜单（`userInfo.superAdmin`）；该组 label 原本为硬编码中文，保持一致不走 i18n
- 路由表与菜单 path 映射各加一项
- 页面形态参照反馈管理：列表（分页）+ 新增/编辑弹窗（类型下拉取 GAME_TYPE 字典、icon 用 el-upload 调 `/upload/image?scene=game-icon`、入口多选框、数值输入、说明 textarea）+ 启用/停用操作 + 删除

## Testing Decisions

- 好测试的标准：只测外部可观察行为（Service 方法的输入输出、校验拒绝、状态流转），不测实现细节（SQL、私有方法、框架装配）
- 单接缝：`GameService` 层集成测试，真实 DB 上下文，参照现有 `StarServiceTest`（`src/test/java/xiaozhi/modules/star/StarServiceTest.java`）为先例
- 覆盖：save 成功路径、game_type 不存在拒绝、非法 entrance 拒绝、空 entrances 拒绝、负数拒绝、update 含 status 切换、page 查询、delete、小程序列表只返回启用记录 + 按入口过滤
- Controller 不单独测：薄壳，仅 Shiro 注解与参数透传，无业务逻辑

## Out of Scope

- 玩游戏的实际玩法逻辑
- 星星的实际发放/扣减执行（与 star 模块的对接）
- 每日可玩次数的计数与拦截执行
- 字典管理中删除被引用 GAME_TYPE 字典项的反向拦截
- 小程序端游戏入口的 UI 展示与交互

## Further Notes

- 领域词条已写入根 `GLOSSARY.md`：「游戏」「游戏类型」「游戏入口」「每日可玩次数上限」
- 星星罐概念见 GLOSSARY「星星罐」「赚取渠道」「消费场景」；游戏后续接入星星执行时，赚取渠道枚举可能需要追加 GAME 取值（届时另立 issue）
