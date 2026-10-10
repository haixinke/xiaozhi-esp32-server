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

## 用户角色与权限

**角色模型（super_admin + role 字段、后端权限、提权 SQL）见 `../manager-api/CLAUDE.md`「用户角色与权限」**，本节只记前端细节。

### 前端菜单控制（HeaderBar.vue）

- 管理员专属菜单（智能体管理、模型配置、参数字典）：`v-if="userInfo.superAdmin && (userInfo.role || 'admin') === 'admin'"`
- 运营相关菜单（内容运营）：`v-if="userInfo.superAdmin"`（admin + operator 均可见）
- `(userInfo.role || 'admin')` 兼容旧会话中无 role 字段的情况
- 用户信息通过 `GET /user/info` 返回，包含 superAdmin 和 role 字段
- 新增角色：前端 HeaderBar 添加对应 `v-if` 条件
