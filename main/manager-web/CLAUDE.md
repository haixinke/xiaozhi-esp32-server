# CLAUDE.md

本文档为 Claude Code (claude.ai/code) 提供在 `manager-web` 中工作时的参考指引。

## 项目概述

`manager-web` 是 `xiaozhi-esp32-server` 的智控台前端（Vue 2 + Element UI + Vuex + vue-router），开发端口 **8001**，后端接口来自 `manager-api`（端口 8002，上下文路径 `/xiaozhi`）。

## 常用命令

```bash
npm install          # 安装依赖
npm run serve        # 开发模式（热更新）
npm run build        # 生产构建
npm run test:unit    # 单元测试
npm run check:i18n   # 国际化文案校验
```

编辑 `.vue` 后 PostToolUse hook 自动跑 `scripts/check-vue-template.js` 编译校验 `<template>` 语法；手动全量校验：`npm run check:template -- src/views/foo.vue`。

## Playwright 视觉验证（免登录）

智控台页面需登录。跳过登录 UI：手动登录一次取 token，注入后直接访问目标页。

1. 浏览器正常登录 → DevTools → Application → Local Storage → 复制 `token` 值。存到 gitignored 文件（如 `.env.test`），**禁止提交**。
2. `add_init_script` 注入（须在 app JS 运行前生效）。路由守卫同时查 `token` 和 `userInfo`，两个都要预置，缺 `userInfo` 会跳回 /login：

```js
await page.addInitScript(token => {
  localStorage.setItem('token', token);
  localStorage.setItem('userInfo', JSON.stringify({ superAdmin: 1, role: 'admin' }));
}, process.env.MANAGER_WEB_TOKEN);
await page.goto('http://localhost:8001/#/game-management');
await page.waitForLoadState('networkidle');
```

注意：token 会过期。注入突然失效时先重新拷贝新 token，再排查其他原因。本机 Playwright 用 **node**（Python 环境无 playwright 模块）。

## 用户角色与权限

**角色模型（super_admin + role 字段、后端权限、提权 SQL）见 `../manager-api/CLAUDE.md`「用户角色与权限」**，本节只记前端细节。

### 前端菜单控制（HeaderBar.vue）

- 管理员专属菜单（智能体管理、模型配置、参数字典）：`v-if="userInfo.superAdmin && (userInfo.role || 'admin') === 'admin'"`
- 运营相关菜单（内容运营）：`v-if="userInfo.superAdmin"`（admin + operator 均可见）
- `(userInfo.role || 'admin')` 兼容旧会话中无 role 字段的情况
- 用户信息通过 `GET /user/info` 返回，包含 superAdmin 和 role 字段
- 新增角色：前端 HeaderBar 添加对应 `v-if` 条件
