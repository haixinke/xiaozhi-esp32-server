# 百宝箱与活动列表

## 产品范围

- 首页右下角使用静态 UI 项目的箱子图标，点击展开浮窗，包含「活动」「AI 写真」和「设置」。点击箱子或浮窗外部可关闭，离开首页后收起。
- AI 写真沿用原图标，移入箱子后仅在当前宠物已破壳时展示，点击进入该宠物的写真集；未破壳时浮窗显示活动和设置。
- 设置使用现有齿轮图标，继续进入完整的「我的」页面。
- 活动入口跳转独立活动列表页，保留参考页顶部星星余额和「可玩」卡片样式。不迁移纪念册、日记和「即将上线」区域。
- 展示智控台中已启用且游戏入口包含「活动」的全部游戏，使用配置的图标、类型名称、说明与赚取/消耗星星数。列表为空时提示，加载失败时可重试。
- 「听你说」进入答题页，「一起画」在当前宠物已破壳时进入绘画页；其他尚未实现的游戏仍展示，点击提示「暂未开放」。列表只负责导航，扣费确认与奖励结算由实际游戏处理。

## 数据来源

- `GET /app/game/list?entrance=ACTIVITY`：后端负责启用状态与入口过滤，并按游戏 ID 升序返回。
- `GET /star/balance`：当前用户星星余额。余额读取失败独立展示重试，不影响游戏列表。
- 每次显示活动页时刷新；临时切后台不结束已进入游戏的会话，遵循现有游戏页面的生命周期规则。

参考实现位于同仓库独立静态工程 `main/eggbabe-miniprogram/miniprogram/pages/life-scene/` 和 `pages/iaa-activities/`。

## 一起画

2026-11-04 确认的迁移范围：

- 完整迁移静态工程当前的单人道具临摹玩法，包括画板工具、参考图查看、完成判定与结果弹层。
- 接入正式后端完成记录、星星奖励／扣费与次数限制；奖励和费用来自智控台游戏配置，替换静态工程本地固定奖励。
- 活动列表和小纸条两个入口共用同一个绘画页。
- 以静态页面的布局、素材与动效为视觉基准，适配正式小程序导航栏与屏幕安全区。

### 已确认的业务规则

- 参与者当前选中的宠物必须已领取且已破壳；即使另有已破壳宠物，选中未破壳宠物时也需先切换。游戏记录归用户，切换宠物不增加奖励机会。
- 只保存开局及完成记录，不上传用户画作，不新增作品集；完成后的彩色素材不进入道具库存。
- 保留当前纸张画板与随机 41 种素材；首次自动查看线稿 5 秒，不占手动查看额度，另可手动查看 3 次，每次 5 秒。查看期间锁定绘画。
- 完成门槛为至少 5 笔、5 秒有效作画、覆盖 10×10 网格中的 5 格，不进行相似度判断；绘画、参考图查看、计时与门槛校验均在小程序本地完成。点击「画好了」只向完成接口提交局次 ID，不上传画作、坐标、绘画时间或其他过程数据；后端完成记录与结算成功后展示反馈，客户端不能指定奖励金额。
- 一起画开局不扣费、不发奖、不承诺星数；扣费与奖励均在完成时按当时游戏配置结算。奖励仅在用户北京时间当日首次成功完成时发放，重复请求不重复奖扣。结果弹层的「好的」只关闭并返回，不触发首次结算。
- 一起画和听你说统一采用游戏管理配置的用户级每日局次上限，0 表示不限。一起画按用户、北京时间自然日、成功开局次数限制，活动与小纸条共享额度；退出仍占一次，开局失败不占次数。听你说一次整组提交计一局，重试不计次。
- 暂时切后台保留画布与本局状态，并暂停参考图倒计时及有效作画计时，返回继续；真正返回上一页丢弃本局，下次重新随机素材。有绘画内容时退出须提示丢失进度。

- 跨日局次的次数计入开局日，奖励资格计入成功完成日；完成请求跨日重试不再次结算，余额以当前账户为准。
- 会话不保存定价与余额快照；运营调整配置后，未完成局次按完成时配置结算。开局响应未知时，优先使用原请求标识恢复该局，不被新局额度或停用状态阻断。

业务归属与完成结算的理由见 [ADR 0011](../../../docs/adr/0011-drawing-user-session.md)。

### 页面与接口

- 两个入口共用 `/drawing/pages/draw/draw`，绘画页及 41 套临摹 PNG 位于 `drawing` 分包，约 1.45 MB。
- `GET /app/drawing/config?petId=…` 查询当前宠物资格、费用、奖励与剩余次数。
- `POST /app/drawing/start` 只提交 `petId` 和稳定的 `requestId`，后端校验资格与限额，只返回 `sessionId` 与 `referenceId`。
- `POST /app/drawing/{sessionId}/complete` 无请求体，仅按局次归属完成并按当前配置结算，不接收任何绘画过程数据；同一局重试不重复奖扣。
- 新表通过 Liquibase `202611041000.sql` 创建，并由 `202611051000.sql` 删除宠物、定价及钱包快照六列；正常启动更新后的后端时应用迁移，不需要手工修改星星流水表。

### 验收边界

- 后端绘画接口：宠物归属及破壳门槛、同一开局请求幂等、开局计次事务、用户级并发限次、每日首次奖励、跨日重试及完成时配置结算；完成接口仅接收局次身份。
- 绘画页面：真实触摸画板、工具与撤销清空、参考图次数和暂停恢复、错误后保留画布重试、结算成功反馈、退出确认。
- 两个入口：只让当前已破壳宠物进入同一绘画页，保留其他游戏分发；实际编译验证导航、素材和页面绑定。

## 验证

从仓库根目录运行：

```sh
node main/egg-miniprogram/miniprogram/custom-tab-bar/index.test.js
node main/egg-miniprogram/miniprogram/pages/iaa-activities/iaa-activities.test.js
node main/egg-miniprogram/miniprogram/pages/home/home.test.js
node main/egg-miniprogram/miniprogram/utils/drawing-entry.test.js
node main/egg-miniprogram/miniprogram/drawing/utils/drawing-api.test.js
node main/egg-miniprogram/miniprogram/drawing/utils/drawing-model.test.js
node main/egg-miniprogram/miniprogram/drawing/utils/drawing-session.test.js
node main/egg-miniprogram/miniprogram/drawing/pages/draw/draw.test.js
node main/egg-miniprogram/scripts/verify-project.js
```

页面与组件测试覆盖约定的用户可见边界：箱子开关与入口跳转、活动列表加载/重试及游戏分发。微信开发者工具用于检查素材、布局和实际点击绑定。

`scripts/drawing-e2e.js` 使用官方 `miniprogram-automator` 验证真实画板触摸、工具、参考图、完成反馈及开局无金额确认，并断言完成请求无绘画数据；接口和缓存均使用独立测试数据，不执行真实扣费或持久化写入。依赖安装方式见 [小程序自动化测试](./miniprogram-e2e-testing.md)。默认脚本覆盖活动入口与配置费用后仍无金额开局的流程，小纸条分发由首页测试覆盖。

后端从 `main/manager-api` 运行：

```sh
mvn test -DskipTests=false '-Dtest=Drawing*Test,StarDailyRewardSettlementTimeTest,StarDailyRewardLiquibaseContractTest' jacoco:report
```

事务测试使用独立 MySQL 容器，需本机 Docker 可用；不连接开发库。
