# 蛋宝宝小程序多宠物上下滑动切换手势调研

> **实施状态（2026-10-05）**：推荐方案 A + D 已实施。A 落地为统一 10px 主轴判定阈值（取代旧 6px 横拖阈值，无回弹逻辑）+ 一次锁死 + 45 度水平优先 + 锁垂直手势豁免终点 45 度校验；D 落地为指示点 88rpx 热区可点选（`onPetDotTap`，复用 `canSwitchPet` 闸门与渐隐切换）。原文两段式/回弹表述按实现收敛，见 `main/egg-miniprogram/miniprogram/pages/home/`。

## 一句话结论

当前多宠物切换是**纯自定义 touch 手势 + 事后判定**：换宠（垂直）与故事空间横向拖拽（水平）两套手势都在 touchend 时才仲裁，touchmove 过程中互不知情，导致垂直滑动时背景被横向误拖（6px 阈值过小 + 1.8 倍增益放大漂移）、且 `_storyDragMoved` 置位后整次垂直手势被吞掉。**推荐方案 A（touchmove 主轴判定 + 误拖回弹）+ 方案 D（指示点可点选）**，不动现有架构；官方 swiper 组件对嵌套场景无任何承诺（文档零字提及），重构成嵌套 swiper / movable-view 风险高收益低。

## 现状代码事实

### 两套手势的实现

| 手势 | 绑定位置 | 实现方式 |
|---|---|---|
| 换宠（垂直） | `.page` 根节点 `bindtouchstart/onPetSwipeStart` + `bindtouchend/onPetSwipeEnd` | home.wxml:1，离散判定 |
| 故事空间横向拖拽 | `pet-view` 与 `story-window-hotspot` 上 `bindtouchstart/move/end` 三连 | home.wxml:97、home.wxml:110，跟手 transform |
| 故事轨道 | `story-track` fixed 定位 200vw，`transform: translateX(storyScrollX)` | home.wxml:106、home.wxss:30 |

关键常量（home.js:29-48）：

- `STORY_DRAG_THRESHOLD_PX = 6`：横向拖拽进入阈值（过小，见下）
- `STORY_DRAG_GAIN = 1.8`：手指位移放大倍数
- `PET_SWIPE_THRESHOLD_PX = 60`：换宠垂直位移阈值
- 换宠判定（home.js:1043）：`|deltaY| >= 60 且 |deltaY| > |deltaX|`，否则忽略

冲突仲裁机制（home.js:1021-1046）：

- `onStoryDragEnd` 中若 `drag.moved` 为 true 则置 `this._storyDragMoved = true`（home.js:786）
- `onPetSwipeEnd` 开头检查 `_storyDragMoved`，置位则直接 return，不换宠（home.js:1038）
- `onPetSwipeStart` 每次手势开始清零该标志（home.js:1026）

切换动画（home.js:1061-1077）：整屏 `opacity:0` → 预取资产（弱网 600ms 超时兜底）→ 置换宠物 → `opacity:1`，`_petSwitching` 锁全程。

指示点（home.wxml:149-151、home.wxss:6）：右侧垂直排布 `pet-switch-dots`，**`pointer-events: none`，纯展示不可点**。

### 不顺畅的根因（按代码逻辑推演）

1. **垂直滑动时背景被横向误拖且不回弹**：用户向上滑想换宠，手指只要横向漂移超过 6px，`onStoryDragMove` 就进入拖拽态（home.js:769），且增益 1.8 倍放大漂移——背景横移约 10-20px。松手后 `storyScrollX` 停在偏移处（`onStoryDragEnd` 无回弹逻辑，home.js:783-793 只处理惯性），用户看到背景歪了。
2. **该手势同时被吞**：上面那次 `moved=true` 置位 `_storyDragMoved`，`onPetSwipeEnd` 直接 return（home.js:1038）——垂直位移再大也换不了宠。用户想换宠却得到"背景歪了一点 + 没反应"，正是"不顺利"的体感。
3. **全程零反馈**：垂直滑动过程中页面纹丝不动（没有跟手位移、没有预览），只有松手达标才一次性触发渐隐切换，判定全靠事后；且 45 度附近的手势被 `|deltaY| <= |deltaX|` 硬性丢弃。
4. **切换本身有网络延迟体感**：淡出后要等预取（600ms 上限），弱网时黑屏感明显（已有超时兜底，属可接受但可优化项）。

### 官方文档核实结果（一手来源）

- **swiper 组件**（https://developers.weixin.qq.com/miniprogram/dev/component/swiper.html）：文档对 `vertical` 属性有定义，但**通篇没有嵌套 swiper（外层 vertical 内层 horizontal）的任何支持声明或限制说明**；也未列 `disable-touch`。即官方对嵌套场景零承诺。
- **事件系统**（https://developers.weixin.qq.com/miniprogram/dev/framework/view/wxml/event.html）：bind 冒泡、catch 阻止冒泡，官方建议"处理函数逻辑排他的场景写 catch 是比较好的选择"。
- **touch 事件族**（https://developers.weixin.qq.com/miniprogram/dev/framework/view/tap.html）：touchstart/move/end/cancel 均为冒泡事件，有捕获阶段。
- **movable-view**（https://developers.weixin.qq.com/miniprogram/dev/component/movable-view.html）：`direction` 支持 `none/all/vertical/horizontal`；有 `htouchmove`/`vtouchmove` 事件（"初次手指触摸后移动为横向/纵向的移动时触发"，catch 该事件会连带 catch touchmove）——即 movable-view 自带方向分流，这是官方组件里唯一显式提供"主轴判定"的原语。

注：社区关于"嵌套 swiper 竖横组合可用/不可用"的帖子本次未能拿到可信原文（搜索工具返回合成内容，不作依据），故本调研只依据官方文档的"零承诺"事实与代码现状做判断。

## 方案

### 方案 A：touchmove 主轴判定 + 误拖回弹（推荐主案）

**原理**：在手势早期（累计位移首次超过约 10px 时）判定主轴并锁死。判定为垂直（换宠意图）时：立即把 `storyScrollX` 回弹到 `drag.baseX`（消除漂移痕迹）、置 `_storyDragMoved = true`（阻止背景继续跟手）、可选给一点跟手预览（如 `pet-switch-stage` 向位移方向 translateY 少许，或露出目标宠物头像边缘）。判定为水平时维持现状。45 度附近手势按垂直优先（换宠是低频动作，宁可换宠误触率低一点也要保背景拖拽的纯粹性——或反之，可调）。

**落点改动**：

- `home.js`：`onStoryDragStart/Move` 增加主轴判定逻辑；`onPetSwipeEnd` 语义基本不变（`_storyDragMoved` 此时含义变为"手势已被水平轴消费"，与现注释一致，home.js:1037）
- 可选：`onPetSwipeStart`/`onPetSwipeEnd` 增加跟手预览的位移状态
- `home.wxml` 无结构改动（或加一个预览元素）

**优点**：

- 直击根因 1/2/3：背景不再被垂直手势误拖、垂直手势不再被吞、且可在 move 阶段给反馈
- 不动现有架构（fixed 轨道 + transform + 自管惯性全保留），改动面小、可单测（现有 home.test.js 已有手势回归用例骨架，home.test.js:1337-1397 可直接扩展）

**缺点/风险**：

- 主轴判定阈值与"判定后是否可反悔"（一次锁死 vs 接近 45 度时允许二次仲裁）需要真机调参
- `onEggTap` 等孵化期手势与 tap 抑制逻辑（`_storyDragMoved` 复用于同手势 tap 抑制，home.js:786）语义要仔细对齐，防止误伤点按

### 方案 B：外层 vertical swiper + 内层 horizontal swiper（官方组件嵌套）

**原理**：宠物层用 `<swiper vertical>` 承载 `wx:for pets`，故事层用横向 swiper 或保留现有拖拽，方向仲裁交给原生手势识别。

**落点改动**：home.wxml 大改（`pet-switch-stage` 整层换 swiper 结构）、home.js 切换逻辑重写、渐隐过渡废弃（改用 swiper 滑动过渡）。

**优点**：原生跟手 + 惯性 + 方向仲裁，理论上最"顺"。

**缺点/风险（基于本仓库代码事实）**：

- **官方文档对嵌套 swiper 零承诺**（见上），跨版本/双端行为无保障，真机回归成本高
- 现架构两处硬冲突：故事轨道是 `position: fixed` 全屏层（home.wxss:30，注释明说移出 scroll-view 避免裁剪偏移，home.wxml:104），放进 swiper-item 后 fixed 定位基准被破坏；渐隐过渡注释明说"不能用 transform，fixed 元素会改用它作定位基准"（home.js:1056-1058），而 swiper 滑动恰是 transform
- 预取防闪屏机制（home.js:1060、switchCurrentPet）与 swiper 的预渲染窗口模型需要重新设计

结论：重构面大、风险不可控，收益主要在"原生顺滑"，而方案 A 能以 1/10 成本拿到 80% 体验提升。不推荐。

### 方案 C：movable-area / movable-view 方向分流

**原理**：movable-view 的 `htouchmove`/`vtouchmove` 是官方唯一显式提供主轴判定的组件原语；可将故事轨道包进 `direction="horizontal"` 的 movable-view，换宠层包进 vertical 的 movable-view。

**落点改动**：story-track 从 fixed+transform 改为 movable-area 内布局（fixed 问题同方案 B），惯性逻辑可删（inertia 属性替代，home.js:802-821）。

**优点**：方向分流是官方行为、惯性/阻尼/回弹原生。

**缺点/风险**：`vtouchmove`/`htouchmove` 文档仅一句话描述，分流阈值不可调（无角度/距离参数）；fixed 布局冲突同方案 B；垂直 movable 层承载"整只宠物页面"与现有淡隐切换语义冲突。不推荐为主案。

### 方案 D：切换入口重设计（推荐与 A 组合的副案）

**原理**：给非手势路径。现有 `pet-switch-dots`（home.wxml:149-151）本就是为多宠设计的指示 UI，去掉 `pointer-events: none`（home.wxss:6）、每个 dot 绑 `bindtap` 调 `switchCurrentPet(index)` 即可点选切换。

**落点改动**：home.wxss 一行 + home.wxml 加 `bindtap` + home.js 复用现成 `switchCurrentPet`（home.js:1061），dot 加按下态与可点 aria-label。

**优点**：改动极小、立刻提供 100% 可靠的切换路径（手势作为增强而非唯一路径）；对 2 只宠物的场景（NFC 限两只原型，实际常见就是 2 只）点选比滑动更快。

**缺点/风险**：无实质风险；只是"绕开"而非"修复"手势冲突，故与 A 组合而非单用。

## 推荐组合与验证路径

**A + D**：主轴判定治本（背景误拖 + 手势被吞），可点指示点治标（保底路径）。

验证路径（按 egg-miniprogram/CLAUDE.md 的测试选型）：

1. 纯逻辑（主轴判定、回弹、`_storyDragMoved` 新语义）：扩展 `home.test.js` 现有手势用例（home.test.js:1337-1397 已覆盖同域回归）
2. WXML 绑定与 dot 点选：`home.test.js` 的模板断言（现已有 `bindtouchmove` 模板断言，home.test.js:482）
3. 真机手感（阈值、45 度边界、跟手预览）：miniprogram-automator 复现手势序列，最终手感调参靠真机
