---
version: alpha
name: 小智智控台
description: xiaozhi-esp32-server 管理控制台（manager-web，Vue 2 + Element UI）的视觉设计语言。浅蓝底、深蓝墨字、品牌蓝渐变驱动交互的管理后台。
colors:
  primary: "#5778ff"
  primary-hover: "#6b8cff"
  primary-confirm-start: "#4a7cfd"
  primary-confirm-end: "#8154fc"
  nav-active-start: "#2983fe"
  nav-active-end: "#5251fc"
  home-cta: "#3375fd"
  danger: "#f56c6c"
  warning: "#e6a23c"
  success: "#67c23a"
  ink: "#3d4566"
  ink-deep: "#342f45"
  text-body: "#606266"
  text-primary: "#303133"
  text-secondary: "#909399"
  text-muted: "#818cae"
  text-nav: "#7079aa"
  surface-page: "#eff4ff"
  surface-card: "#fafcfe"
  surface-tint: "#e6ebff"
  surface-table-header: "#edf2fc"
  border: "#e4e7ed"
  border-table: "#eef3fd"
typography:
  sans:
    fontFamily: Avenir, Helvetica, Arial, sans-serif
  mono:
    fontFamily: Menlo, Monaco, Consolas, "Courier New", monospace
  display:
    fontFamily: Avenir, Helvetica, Arial, sans-serif
    fontSize: 33px
    fontWeight: 700
    lineHeight: 1.2
  headline:
    fontFamily: Avenir, Helvetica, Arial, sans-serif
    fontSize: 24px
    fontWeight: 600
    lineHeight: 1.4
  title:
    fontFamily: Avenir, Helvetica, Arial, sans-serif
    fontSize: 18px
    fontWeight: 500
    lineHeight: 1.4
  nav:
    fontFamily: Avenir, Helvetica, Arial, sans-serif
    fontSize: 16px
    fontWeight: 500
    lineHeight: 1.2
  body:
    fontFamily: Avenir, Helvetica, Arial, sans-serif
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
  caption:
    fontFamily: Avenir, Helvetica, Arial, sans-serif
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.5
rounded:
  base: 4px
  container: 6px
  card: 15px
  pill: 20px
spacing:
  page-gutter: 20px
  card-padding: 22px
  dialog-padding: 20px
  footer-gap: 16px
components:
  button-confirm:
    backgroundColor: "linear-gradient(to right, {colors.primary-confirm-start}, {colors.primary-confirm-end})"
    textColor: "#ffffff"
    rounded: "{rounded.base}"
  button-default:
    backgroundColor: "#4998ff"
    textColor: "#ffffff"
    rounded: "{rounded.base}"
  button-delete:
    backgroundColor: "#ff6b6b"
    textColor: "#ffffff"
    rounded: "{rounded.base}"
  button-add:
    backgroundColor: "#52c41a"
    textColor: "#ffffff"
    rounded: "{rounded.base}"
  button-default-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "#ffffff"
    rounded: "{rounded.base}"
  home-cta-button:
    backgroundColor: "{colors.home-cta}"
    textColor: "#ffffff"
    rounded: "{rounded.pill}"
  table-container:
    backgroundColor: "#ffffff"
    textColor: "{colors.text-body}"
    rounded: "{rounded.container}"
  page-shell:
    backgroundColor: "{colors.surface-page}"
    textColor: "{colors.text-primary}"
  card:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
  tint-tag:
    backgroundColor: "{colors.surface-tint}"
    textColor: "{colors.primary}"
    rounded: 14px
  input:
    backgroundColor: "#ffffff"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.base}"
  banner:
    backgroundColor: "linear-gradient(269.62deg, #e0e6fd 0%, #cce7ff 49.69%, #d3d3fe 100%)"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
  dialog:
    rounded: 10px
    backgroundColor: "#ffffff"
    padding: "{spacing.dialog-padding}"
  table:
    backgroundColor: "{colors.surface-table-header}"
    textColor: "{colors.ink-deep}"
    rounded: "{rounded.container}"
  nav-active-tab:
    backgroundColor: "linear-gradient(90deg, {colors.nav-active-start} 0%, {colors.nav-active-end} 100%)"
    textColor: "#ffffff"
    rounded: 30px
---

# 小智智控台 DESIGN

## Overview

小智智控台是 xiaozhi-esp32-server 的管理后台（Vue 2 + Element UI 2.15），面向设备/智能体/模型运营人员。视觉基调是"浅蓝工作区"：页面铺浅蓝底（#eff4ff），内容浮在白色或近白卡片上，文字使用深蓝墨色（#3d4566 / #303133），交互动作由品牌蓝（#5778ff）及蓝紫渐变（#4a7cfd → #8154fc）驱动。整体是轻量、明亮、圆润的工具型控制台，不是数据大屏，也不走深色路线。圆角偏大（卡片 15–20px、按钮 4px、导航 30px 胶囊），配合 0.3s 的 hover 上浮动效，营造轻快感。

Element UI 使用默认 theme-chalk 样式，未做主题定制；品牌感全部来自共享封装组件（CustomButton / CustomDialog / CustomTable / CustomPagination）和页面级样式。新界面应复用这些共享原语，而不是直接裸用 el-* 组件再局部改色。

## Colors

- **Primary（#5778ff）：** 品牌蓝，普通主按钮、强调文字、链接色、登录按钮。hover 用 #6b8cff。
- **Confirm 渐变（#4a7cfd → #8154fc）：** 蓝紫渐变，只用于"确认/保存"类主行动按钮（CustomButton type="confirm"、弹窗主按钮），与普通 primary 区分操作分量。
- **Nav 渐变（#2983fe → #5251fc）：** 仅用于顶栏导航的激活态胶囊，不挪作他用。
- **Ink（#3d4566）：** 标题与关键文字色，比纯黑柔和；表头用更深的 #342f45。
- **Text 梯队：** 正文 #606266 / #303133，次要 #909399，提示 #818cae，导航未激活 #7079aa。
- **Surface 梯队：** 页面底 #eff4ff → 卡片 #fafcfe/#ffffff → 浅蓝强调底 #e6ebff（标签、设置按钮）→ 表头 #edf2fc。
- **语义色：** 危险 #f56c6c、警告 #e6a23c、成功 #67c23a，沿用 Element 默认值。

## Typography

全局字族 Avenir, Helvetica, Arial, sans-serif（App.vue）；等宽场景用 Menlo/Monaco/Consolas。字号梯队集中在小字号：14px 正文、12px 辅助与表格次级信息是绝对主力，16px 仅用于顶栏导航，18px 用于卡片/弹窗标题，24px 用于页面级标题，33px 仅出现在首页欢迎横幅。字重以 500 为强调常态（比 bold 更常见），标题用 600/700。新界面优先沿用 12/14/16/18/24 梯队，不要引入 15px、17px 之类的中间值。

## Layout

顶栏通栏布局：HeaderBar 高 63px，浅蓝渐变底（#dfeafe → #eff4ff），中间导航是白色胶囊容器（圆角 30px）承载各模块入口。内容区在浅蓝页面底上以卡片堆叠，页面最小宽度 900px（登录/注册页 1200px），属桌面优先设计，无移动端适配，也不做响应式栅格。卡片内边距约 22px，弹窗体 20px，表格局部用 16px 间隔。

## Elevation & Depth

阴影克制且都带蓝色倾向：表格容器 0 2px 12px rgba(74, 124, 253, 0.12)，弹窗 0 2px 8px rgba(0,0,0,0.1)，导航白胶囊 0 0 6px #cfe1fb。不用重投影；层级靠"浅蓝底 + 白卡"的色彩对比而非阴影堆叠。hover 态按钮上浮 translateY(-2px) 并叠加同色浅影（如 rgba(74,124,253,0.2)）。

## Shapes

圆角分四档：控件 4px（按钮、输入框、表格 6px）、弹窗 10px、卡片 15–20px（设备卡 20px、登录框 20px）、胶囊 20–30px（导航 30px、设置按钮 14px、添加设备按钮 20px、搜索框 20px）。规则：容器越大圆角越大；可点的胶囊元素（导航、标签、搜索）用全圆角，表单控件保持 4px 小圆角。

## Components

共享原语在 src/components/，是设计语言的载体：

- **CustomButton：** 四个变体——default（#5778ff 实心）、confirm（蓝紫渐变）、delete（#ff6b6b）、add（#52c41a）。统一 4px 圆角、hover 上浮 + 同色浅影。新增操作按钮必须走这四个变体，不要发明第五种颜色。
- **CustomDialog：** 10px 圆角，头部浅蓝紫渐变（#e2eeff → #edeafe）+ 图标标题，确认按钮用 confirm 渐变。新弹窗用它，不要裸用 el-dialog。
- **CustomTable：** 表头 #edf2fc 底 + #342f45 字，6px 圆角容器带蓝色倾向阴影，滚动条定制为蓝色系（#a1c9fd 拇指 / #f0f3fe 轨道）。列表页统一用它 + CustomPagination。
- **CustomPagination：** 激活页码为蓝色渐变（#2a6dfe → #6b8cff）胶囊，6px 圆角。
- **导航激活态：** 蓝紫渐变胶囊 + 白色文字 + 顶部高光，图标通过 filter: brightness(0) invert(1) 反白。

## Do's and Don'ts

**Do:**

- 新列表页复用 CustomTable + CustomPagination，新弹窗复用 CustomDialog，新按钮复用 CustomButton 四变体（default `#4998ff` / confirm 蓝紫渐变 / delete `#ff6b6b` / add `#52c41a`）。
- 页面底用 #eff4ff，卡片用白或 #fafcfe，保持"浅蓝底托白卡"的层次。
- 强调文字/链接用 #5778ff，浅蓝强调底用 #e6ebff。

**Don't:**

- 不要把确认渐变（#4a7cfd → #8154fc）用在非主行动上，也不要用导航渐变（#2983fe → #5251fc）给按钮或背景。
- 不要引入深色模式或深色卡片；全台为单一浅色主题。
- 不要在共享原语之外再定制第二套表格/弹窗/按钮样式——Element 默认样式只在共享组件内部覆盖。
