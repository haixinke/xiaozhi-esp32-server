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

## 用户角色与权限

### 角色体系

sys_user 表通过 `super_admin` + `role` 两个字段控制权限：

| super_admin | role | 身份 | 可见菜单 |
|-------------|------|------|---------|
| 0 | - | 普通用户 | 首页、设备管理 |
| 1 | admin | 管理员 | 全部菜单 |
| 1 | operator | 运营者 | 仅"内容运营"菜单 |

### 前端菜单控制（HeaderBar.vue）

- 管理员专属菜单（智能体管理、模型配置、参数字典）：`v-if="userInfo.superAdmin && (userInfo.role || 'admin') === 'admin'"`
- 运营相关菜单（内容运营）：`v-if="userInfo.superAdmin"`（admin + operator 均可见）
- `(userInfo.role || 'admin')` 兼容旧会话中无 role 字段的情况

### 后端权限

- 接口级权限仍统一使用 `@RequiresPermissions("sys:role:superAdmin")`，不区分 admin/operator
- role 字段仅用于前端菜单可见性控制
- 用户信息通过 `GET /user/info` 返回，包含 superAdmin 和 role 字段

### 创建超管/运营者

- 系统第一个注册用户自动成为超管（super_admin=1, role=admin）
- 后续用户需手动 SQL 提权：

  ```sql
  UPDATE sys_user SET super_admin = 1, role = 'admin' WHERE username = 'xxx';
  UPDATE sys_user SET super_admin = 1, role = 'operator' WHERE username = 'xxx';
  ```

### 扩展指引

- 新增角色：在 role 字段增加新值（如 `viewer`），前端 HeaderBar 添加对应 v-if 条件
- 后端角色鉴权：如需服务端区分 admin/operator，可在 Oauth2Realm 中按 role 值注入不同权限标识
- 用户管理 UI 提权：如需在智控台直接设置角色，在用户管理页增加角色选择器，后端增加 PUT /admin/user/role 接口
