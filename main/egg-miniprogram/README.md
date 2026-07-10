# 小程序源码（全部页面）

一个完整的微信小程序源码工程，覆盖蛋宝宝的 **13 个页面**（蛋宝宝 tab 6 个 + 我的 tab 7 个），共享一套自定义组件。

## 结构

```
├── app.json                 ← 全局配置：13 个页面 + tabBar（蛋宝宝 / 我的）
├── app.wxss                 ← 全局样式 + 颜色 token 对照表 + 单位换算说明
├── sitemap.json
├── components/              ← 共享自定义组件
│   ├── nav-bar/             （自定义导航栏，navigationStyle=custom 时必需）
│   ├── egg-avatar/          （蛋形头像占位，唯一允许的品牌双色渐变）
│   ├── button/              （primary / secondary / danger）
│   ├── card/  list-row/  switch-row/  collapse-item/
│   ├── mood-badge/          ★ 新增：心情标签 pill
│   └── signal-bars/         ★ 新增：3 格信号强度（仅在线展示）
└── pages/
    ├── home/                ★ 蛋宝宝主页（列表 / 单只 / 空态）
    ├── chat/                ★ 对话页（含模拟回复 + typing 态）
    ├── egg-state/           ★ 蛋形态详情（孵化中，进度 + 神秘感占位）
    ├── pet-detail/          ★ 宠物详情（灵魂底色 7 维 + 成就 + 技能）
    ├── add-device/          ★ 添加（扫码 / 凭证码 → 校验 → 惊喜 → 进孵化）
    ├── hatch/               ★ 破壳仪式（多阶段动画）
    ├── my/  profile/  settings/  account/  deregister/  help/  privacy/
```

★ = 本次新补的蛋宝宝 tab 页面骨架（干净文件名）。「我的」7 个页面为此前已有的真实源码（含哈希文件名，为导出态）。

## 关于两类文件名

- **哈希文件名**（如 `my-9297361c.js`）：来自小程序导出包的「我的」模块原始文件，逐一对应 `pages/xxx/xxx.js` 的运行时命名。工程落地时按项目规范整理即可。
- **干净文件名**（如 `home.wxml`）：本次新写的蛋宝宝 tab 骨架，`app.json` 的 `pages` 路径直接指向它们，导入微信开发者工具即可识别。

## 骨架的定位

蛋宝宝 tab 的 6 个页面是**结构骨架**：WXML 结构、组件用法、关键样式（token 值已按「设计 px × 2 = rpx」写死）、页面 `data` 与事件处理都已就位，`js` 内以 `// TODO` 标出需要接后端 / 微信能力（`wx.login`、手机号、`wx.scanCode`、订阅消息、破壳事件）的位置。数据均为示例静态数据，接口对接由工程实现。

规格细节以交付包根目录 `README.md`（逐屏规格）+ `设计原型(全部页面)/` 交互原型为准。

## 待补资源

- `assets/tab/`：tabBar 图标（egg / me 各含常态 + 选中态）。当前 `app.json` 的 tabBar 为**纯文字**（不引用图标文件，可直接启动）；正式图标系统到位后，把图标放入 `assets/tab/`，再给两个 tab 各加 `iconPath` / `selectedIconPath` 即可。
- 字体文件（PingFang SC / Google Sans）、蛋宝宝形象素材、正式图标库：见根目录 README「待办」章节。
