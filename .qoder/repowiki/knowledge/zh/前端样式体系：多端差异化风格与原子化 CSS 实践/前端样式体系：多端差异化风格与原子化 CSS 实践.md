---
kind: frontend_style
name: 前端样式体系：多端差异化风格与原子化 CSS 实践
category: frontend_style
scope:
    - '**'
source_files:
    - main/manager-web/vue.config.js
    - main/manager-web/src/styles/global.scss
    - main/manager-mobile/uno.config.ts
    - main/manager-mobile/package.json
    - main/manager-mobile/src/style/index.scss
    - main/miniprogram/app.wxss
    - main/miniprogram/pages/index/index.wxss
    - main/miniprogram/UI_REDESIGN_SUMMARY.md
    - main/egg-miniprogram/miniprogram/app.wxss
    - main/egg-miniprogram/miniprogram/components/button/button.wxss
---

本仓库包含多个独立的前端子项目，每个项目采用不同的样式方案，整体呈现「按端选型、各自为政」的样式架构。

## 1. 管理后台 Web（Vue2 + Element UI）
- **框架与组件库**：Vue2 + Element UI 2.15.14，通过 CDN 或本地打包引入。
- **样式语言**：SCSS，全局样式集中在 `src/styles/global.scss`，主要覆盖 Element UI 默认样式（如 `.el-footer`、滚动条样式）。
- **构建优化**：`vue.config.js` 中启用 Terser 压缩、Gzip 压缩、Webpack 缓存、Service Worker 注入（Workbox），支持生产环境通过环境变量切换 CDN 模式。
- **设计约定**：使用 `!important` 覆盖 Element UI 默认样式，自定义滚动条样式 mixin（`scrollbar-style`）。

## 2. 移动端管理后台（UniApp + Vue3）
- **框架与组件库**：UniApp 3.x + wot-design-uni 1.9.1，基于 Vue3 + Pinia。
- **样式方案**：**UnoCSS**（原子化 CSS），配置文件在 `uno.config.ts`，集成 `@uni-helper/unocss-preset-uni` 预设，支持属性化（attributify）、图标（presetIcons）、指令（@apply/@screen/theme）。
- **主题系统**：通过 CSS 变量 `--wot-color-theme` 和 `--wot-button-primary-bg-color` 控制主题色，可在 `src/style/index.scss` 中覆盖。
- **响应式策略**：使用 `rpx` 单位适配不同屏幕，内置 `p-safe`/`pt-safe`/`pb-safe` 规则处理安全区域。
- **开发工具**：ESLint + TypeScript + Husky 提交钩子，支持多端构建（H5、微信小程序、iOS/Android App）。

## 3. 语音助手小程序（微信原生）
- **样式语言**：原生 WXSS，采用 BEM 命名规范（如 `.page`、`.top-bar`、`.input-wrapper`）。
- **设计系统**：**Ethereal Companion（完美女友）** 主题，以樱花粉（#864e5a, #ffb7c5）为主色调，瓷白色（#fbf9f8）为背景。
- **视觉风格**：大量使用玻璃态效果（`backdrop-filter: blur(20rpx)`）、大圆角（32rpx/100rpx）、柔和阴影、渐变背景。
- **暗色模式**：通过 `.dark` 类名切换完整暗色主题，覆盖所有组件样式。
- **动画系统**：自定义 keyframes（aurora-drift、bounce、recording-wave 等）实现微交互。
- **安全区域**：使用 `env(safe-area-inset-bottom)` 适配刘海屏。

## 4. 蛋宝宝小程序（微信原生）
- **样式语言**：原生 WXSS，遵循严格的设计系统文档。
- **设计系统**：**eggbabe Design System**，颜色 token 对照表在 `app.wxss` 顶部注释中明确定义（--ink, --egg-green, --canvas 等）。
- **单位换算**：设计稿按 375px 绘制，固定换算比例 `px × 2 = rpx`，所有数值已写死。
- **字体规范**：中文统一使用 'PingFang SC'/'Microsoft YaHei'，最高字重 600。
- **组件样式**：自定义组件（button、card、nav-bar 等）通过 `:host` 选择器确保宿主节点样式正确。

## 5. 数字人测试页面（HTML/CSS/JS）
- **技术栈**：纯 HTML + CSS + JavaScript，无构建工具。
- **样式组织**：内联 CSS 和外部 CSS 文件混合，结构相对简单。

## 核心约束与约定
- **多端隔离**：各前端项目独立维护样式，无跨项目共享的样式库或设计令牌。
- **rpx 优先**：小程序端统一使用 rpx 单位，Web 端使用 px/rem。
- **主题定制**：通过 CSS 变量或 UnoCSS theme 配置实现主题切换，而非硬编码颜色。
- **组件样式封装**：小程序组件使用 WXML/WXSS 分离，Web 组件使用 Vue SFC 的 `<style>` 标签。
- **构建时优化**：生产环境启用代码压缩、Gzip、CDN 加速、Service Worker 缓存。
- **无障碍与安全区域**：统一处理 safe-area-inset 适配，隐藏滚动条提升视觉体验。