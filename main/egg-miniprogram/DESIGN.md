---
version: alpha
name: 蛋宝宝微信小程序
description: 从领养、孵化到破壳后陪伴的微信小程序设计规范。
colors:
  ink: "#002900"
  egg-green: "#9DB65B"
  egg-yellow: "#EDE78E"
  egg-pink: "#F4B9AE"
  canvas: "#FFFFFF"
  text: "#1A1A1A"
  text-muted: "#5C5C5C"
  text-faint: "#8C8C88"
  hairline: "#E5E3DF"
  hairline-strong: "#CFCBC4"
  ink-surface: "#E5EBE5"
  error: "#D9463C"
  error-surface: "#FDECEA"
  semantic-error-text: "#C33F36"
  green-surface: "#F5F8ED"
  green-on-tint: "#54632C"
  yellow-surface: "#FAFAEF"
  yellow-on-tint: "#6E6B35"
typography:
  body-md-cn:
    fontFamily: PingFang SC, Helvetica Neue, Microsoft YaHei, sans-serif
    fontSize: 15px
  caption-cn:
    fontFamily: PingFang SC, Helvetica Neue, Microsoft YaHei, sans-serif
    fontSize: 12px
  button-cn:
    fontFamily: PingFang SC, Helvetica Neue, Microsoft YaHei, sans-serif
    fontSize: 15px
    fontWeight: 600
rounded:
  lg: 16px
  full: 499.5px
spacing:
  lg: 24px
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.canvas}"
    typography: "{typography.button-cn}"
    rounded: "{rounded.full}"
    height: 50px
  button-secondary:
    textColor: "{colors.ink}"
    typography: "{typography.button-cn}"
    rounded: "{rounded.full}"
    height: 50px
  button-danger:
    backgroundColor: "{colors.error}"
    typography: "{typography.button-cn}"
    rounded: "{rounded.full}"
    height: 50px
  nav-bar:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
  list-row:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.text}"
    typography: "{typography.body-md-cn}"
  list-row-pressed:
    backgroundColor: "{colors.ink-surface}"
  card-base:
    backgroundColor: "{colors.canvas}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  card-tint-green:
    backgroundColor: "{colors.green-surface}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  card-tint-yellow:
    backgroundColor: "{colors.yellow-surface}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
---

## Overview

蛋宝宝是一款孵化型 AI 宠物微信小程序。用户从领养一颗蛋开始，通过日常互动陪它孵化，破壳后继续陪伴与对话。孵化体验以蛋为画面焦点，周围的孵蛋房随季节、天气和时段变化。

## Colors

- 常规页面以暖白为底，导航、卡片和列表使用白色表面。正文用 `text`，次要信息和辅助说明分别用 `text-muted` 和 `text-faint`。
- 用深绿 `ink` 突出主操作。蛋壳占位图沿用黄粉渐变，浅绿或浅黄的提示面搭配同色系深色文字。
- 危险操作的填充使用 `error`，错误说明使用 `semantic-error-text`。需要标出异常区域时使用浅红提示底。

## Typography

中文界面沿用全局 PingFang SC 字体栈和系统回退。列表标签采用正文层级，尾部值和说明采用辅助文字层级。按钮标签与导航标题用半粗字重。

## Layout

- 将设计稿的 px 尺寸加倍后写入 WXSS 的 rpx。固定在屏幕底部的操作为设备安全区留出空间。
- 常规页面复用 `nav-bar`，由组件按设备状态栏高度留位。蛋宝宝首页空态显示标准导航栏，孵化态以全屏房间承载互动入口。
- 孵蛋房将背景、窝垫和蛋分层。自定义涂鸦叠在蛋壳底图与立体高光之间，随场景保留蛋壳的体积与光影。

## Elevation & Depth

普通白底卡片用细边框和轻微绿调阴影区分于页面底色。浅色提示卡片以底色区分信息，设置列表以细分隔线分行。

## Shapes

操作按钮采用胶囊形。用户头像缺图时复用 `egg-avatar` 的非对称蛋形轮廓。

## Components

- 主操作使用实底按钮，次操作在浅色表面使用透明底和深绿描边。共用按钮按下时轻微收缩，禁用时降低透明度并取消点击反馈。
- 设置和资料项复用 `list-row`。将现值放在右侧，可打开页面、编辑项或选择器的行显示箭头，组末按需收掉分隔线。
- 首页右下角的百宝箱浮层承载活动、AI 写真和设置。AI 写真仅在当前宠物破壳后显示，设置进入「我的」。点箱子或浮层外侧可收起。
- 系统要求减少动态效果时，沿用共用组件的降级处理，停用相应的装饰动画并保留交互入口。

## Do's and Don'ts

- PingFang SC 最高使用半粗字重，避免请求更高字重而产生合成粗体。
- 孵化期不要添加独立的场景选择页或恢复手动调试入口。
- 窗景缺少匹配素材时保持窗口入口关闭。不要用其他季节、天气的窗景或整幅房间图替代。
