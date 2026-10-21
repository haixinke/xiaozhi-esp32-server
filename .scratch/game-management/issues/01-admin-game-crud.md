# 01: 游戏管理后台 CRUD（建表 + 字典初始化 + 后端接口 + 智控台页面）

**What to build:** 运营在智控台"运营管理"菜单看到并进入"游戏管理"页面，可分页查看游戏列表、新增游戏（类型下拉取自 GAME_TYPE 字典、填赚取/消耗星星数与每日次数上限、上传 icon、填说明、勾选一个或多个入口）、编辑、启用/停用、物理删除。`ai_game` 表与 GAME_TYPE 字典（一起画/听你说/遇见你/旅行/打工）随 Liquibase 迁移自动初始化。保存时服务端校验：类型必须存在于字典、入口必须是 GameEntranceEnum（NOTE 小纸条 / ACTIVITY 活动）内至少一项、三个数值字段非负。icon 经现有 `/upload/image` 上传（UploadScene 新增 GAME_ICON，png/jpeg/webp/svg 2MB），库存完整 OSS URL，icon 与说明可空。每日次数 0 表示不限。管理接口 `/admin/game/*` 用 `sys:role:superAdmin` 权限，页面可见条件跟随运营管理同组菜单。GameService 层集成测试覆盖 CRUD 与全部校验拒绝路径。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [ ] Liquibase changeset：建 `ai_game` 表（字段与必填性按 spec）+ 插入 GAME_TYPE 字典类型与 5 条字典数据
- [ ] `UploadScene` 追加 GAME_ICON
- [ ] `xiaozhi.modules.game` 模块：entity/dao/service/controller/dto/vo + GameEntranceEnum，管理接口 page/get/save/update/delete，权限 `sys:role:superAdmin`
- [ ] 服务端校验：game_type 在字典存在、entrances 非空且全在枚举内、earn_stars/consume_stars/daily_limit 非负
- [ ] GameService 集成测试（参照 StarServiceTest）：CRUD + 各校验拒绝路径
- [ ] 智控台运营管理菜单新增"游戏管理"子项 + 路由 + path 映射
- [ ] 游戏管理页面：分页列表 + 新增/编辑弹窗（类型下拉取字典、icon el-upload 调 /upload/image?scene=game-icon、入口多选、数值输入、说明 textarea）+ 启用/停用 + 删除
