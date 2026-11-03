# 百宝箱与活动列表

## 产品范围

- 首页右下角使用静态 UI 项目的箱子图标，点击展开浮窗，包含「活动」「AI 写真」和「设置」。点击箱子或浮窗外部可关闭，离开首页后收起。
- AI 写真沿用原图标，移入箱子后仅在当前宠物已破壳时展示，点击进入该宠物的写真集；未破壳时浮窗显示活动和设置。
- 设置使用现有齿轮图标，继续进入完整的「我的」页面。
- 活动入口跳转独立活动列表页，保留参考页顶部星星余额和「可玩」卡片样式。不迁移纪念册、日记和「即将上线」区域。
- 展示智控台中已启用且游戏入口包含「活动」的全部游戏，使用配置的图标、类型名称、说明与赚取/消耗星星数。列表为空时提示，加载失败时可重试。
- 「听你说」进入现有答题页，其他尚未实现的游戏仍展示，点击提示「暂未开放」。列表点击不执行扣费或奖励结算。

## 数据来源

- `GET /app/game/list?entrance=ACTIVITY`：后端负责启用状态与入口过滤，并按游戏 ID 升序返回。
- `GET /star/balance`：当前用户星星余额。余额读取失败独立展示重试，不影响游戏列表。
- 每次显示活动页时刷新；临时切后台不结束已进入游戏的会话，遵循现有游戏页面的生命周期规则。

参考实现位于同仓库独立静态工程 `main/eggbabe-miniprogram/miniprogram/pages/life-scene/` 和 `pages/iaa-activities/`。

## 验证

从仓库根目录运行：

```sh
node main/egg-miniprogram/miniprogram/custom-tab-bar/index.test.js
node main/egg-miniprogram/miniprogram/pages/iaa-activities/iaa-activities.test.js
node main/egg-miniprogram/scripts/verify-project.js
```

页面与组件测试覆盖约定的用户可见边界：箱子开关与入口跳转、活动列表加载/重试及游戏分发。微信开发者工具用于检查素材、布局和实际点击绑定。
