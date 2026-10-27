# 01: 小物件管理骨架打通（建表 + 字典大类 + 只读列表）

**What to build:** 运营在智控台"运营管理"菜单里看到"小物件管理"子项，点击能进入页面，页面顶部有"新增小物件"按钮（本片不接功能），下方是分页列表，列为图片缩略图、类型名称、状态、更新时间。空库时正常显示空态、不报错。`ai_small_object` 表与 `SMALL_OBJECT_TYPE` 字典大类随 Liquibase 迁移自动创建，部署后在字典管理页能看到这个大类并能在其下自由新增小类；给小类补上数据后，小物件列表能正确把 `dict_value` 翻译成 `dict_label` 显示，翻译不到时回落显示原始 code。列表只展示未删除记录，顺序与字典管理页中小类的排序一致。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] 新增 Liquibase changeset（只新增文件，不改动任何已有 changeset），在主 changelog 末尾追加注册块；同一 changeset 内建立 `ai_small_object` 表并插入 `SMALL_OBJECT_TYPE` 字典大类，不预置任何小类示例数据；插入语句带 `delete ... where` 前缀保证可重复执行
- [ ] `ai_small_object` 表含主键、物件类型（存字典 `dict_value`）、图片 URL、故事、状态、删除标记、创建/更新审计字段；主键走 `application.yml` 已配置的全局 `ASSIGN_ID`（雪花），DDL 因此不写 `AUTO_INCREMENT`——这是与 `ai_game` 表 DDL 的刻意差异，照功能分层照抄 game 模块，但主键策略跟随全局配置；建物件类型与状态的普通索引；不建任何唯一索引
- [ ] 新增错误码"物件不存在"，并同步到全部七套 `messages_*.properties`（含无语言后缀的 `messages.properties` 基包，`application.yml` 的 `basename: i18n/messages` 使它是默认兜底包；只补六套会漏掉默认 locale，且后端没有像前端 `check:i18n` 那样的自动对齐校验）
- [ ] 新建后端模块（entity/dao/service+impl/controller/dto/vo），分页接口只返回未删除记录，详情接口对不存在或已删除的记录返回"物件不存在"
- [ ] 列表排序按物件类型的字典 sort 升序，而非按 id 或更新时间
- [ ] 详情/列表中的类型名称由字典 `dict_label` 翻译，翻译不到时回落显示原始 `dict_value`
- [ ] 管理端接口挂 superAdmin 权限，与运营管理其他子菜单一致
- [ ] 前端运营管理下拉菜单新增"小物件管理"子项（硬编码中文，不走 i18n），菜单高亮的两处路径判断都要补上新路由
- [ ] 前端路由表与菜单路径映射各加一项
- [ ] 小物件管理页面：分页列表，列含图片缩略图、类型名称、状态标签、更新时间；显示"新增小物件"按钮但本片不实现其功能
- [ ] 根 GLOSSARY 新增「小物件 (Small Object)」词条，明确与已有的「道具 (Item)」是两个不同概念（道具是消耗品/服装/音量配额那套，有库存与消耗语义；小物件是纯内容记录），并记录"字典删除被引用类型时不做引用检查"是已知缺口
- [ ] Service 集成测试：空库分页正常、只返回未删除记录、按字典 sort 排序、详情查不存在时报"物件不存在"、类型名称能按字典翻译、字典中不存在的 code 回落显示原始值

## Comments
