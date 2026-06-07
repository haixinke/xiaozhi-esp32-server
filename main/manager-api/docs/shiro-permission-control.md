# Shiro 权限控制机制

> 本文档描述 manager-api 服务中 Apache Shiro 的认证与授权流程。

## 概述

项目使用 Apache Shiro 2.0.2 + 自定义 OAuth2 Token 认证。权限模型为二元分级：**普通用户** vs **超级管理员**，无细粒度 RBAC 体系。

`@RequiresPermissions` 注解中的 `sys:role:normal` 和 `sys:role:superAdmin` 是 Shiro 字符串权限（不是角色），本质上等同于角色判断。

## 三层控制架构

```
请求到达
  |
  +-- 第 1 层: URL 级别 (Shiro Filter Chain)
  |     按 URL 模式匹配过滤器: anon / server / oauth2
  |
  +-- 第 2 层: 认证 (Authentication)
  |     oauth2: 校验 Bearer Token -> 查库 -> 建立 UserDetail
  |     server: 校验预共享密钥
  |
  +-- 第 3 层: 授权 (Authorization)
        @RequiresPermissions 注解 -> 查 superAdmin 字段 -> 允许/拒绝
```

## 第 1 层: URL 级别 (Filter Chain)

**配置文件:** `src/main/java/xiaozhi/modules/security/config/ShiroConfig.java` (74-106 行)

两个自定义过滤器 (59-64 行):

| 过滤器名 | 类 | 作用 |
|----------|-----|------|
| `oauth2` | `Oauth2Filter` | 校验 Bearer Token (用户请求) |
| `server` | `ServerSecretFilter` | 校验预共享服务密钥 (机对机) |

### Filter Chain 映射

| URL 模式 | 过滤器 | 含义 |
|----------|--------|------|
| `/ota/**`, `/otaMag/download/**` | `anon` | 公开 (设备 OTA) |
| `/webjars/**`, `/druid/**`, `/v3/api-docs/**`, `/doc.html`, `/favicon.ico` | `anon` | 静态资源/接口文档 |
| `/user/captcha`, `/user/smsVerification`, `/user/login` | `anon` | 公开认证端点 |
| `/user/pub-config`, `/user/register`, `/user/retrieve-password` | `anon` | 公开注册/配置 |
| `/config/**` | `server` | 预共享密钥 |
| `/agent/chat-history/report` | `server` | 预共享密钥 |
| `/agent/chat-history/download/**` | `anon` | 公开下载 |
| `/agent/chat-summary/**`, `/agent/chat-title/**` | `server` | 预共享密钥 |
| `/agent/play/**` | `anon` | 公开音频播放 |
| `/pet/birth` | `anon` | 公开 |
| `/pet/detail/**`, `/pet/chat-history/list`, `/pet/memory/list`, `/pet/profile` | `server` | 预共享密钥 |
| `/companion/create`, `/companion/update`, `/companion/detail/**` | `oauth2` | Token 认证 |
| `/voiceClone/play/**` | `anon` | 公开 |
| `/wechat/login` | `anon` | 公开 |
| `/**` (兜底) | `oauth2` | 所有其他端点需 Token |

### 关键配置

`AuthorizationAttributeSourceAdvisor` Bean (117-121 行) 是 Spring AOP 桥梁，使 `@RequiresPermissions` 注解生效。没有它，注解会被忽略。

## 第 2 层: 认证 (Authentication)

### OAuth2 路径

**调用链:**

```
请求 -> Oauth2Filter.onAccessDenied()
     -> 提取 Authorization Header 中的 Bearer Token
     -> createToken() 创建 Oauth2Token
     -> Oauth2Realm.doGetAuthenticationInfo()
         -> 查 sys_user_token 表 (校验 Token 有效性/过期)
         -> 查 sys_user 表 (获取用户信息)
         -> 校验账户状态 (null=禁用, 0=锁定)
         -> 返回 SimpleAuthenticationInfo(userDetail, token, realmName)
```

**关键文件:**

| 文件 | 作用 | 关键行 |
|------|------|--------|
| `Oauth2Filter.java` | 提取 Bearer Token, 触发 Shiro 登录 | 56-76 (onAccessDenied), 100-108 (Token 提取) |
| `Oauth2Realm.java` | Token 验证 + 用户信息构建 | 76-106 (doGetAuthenticationInfo) |
| `ShiroServiceImpl.java` | 查询 Token 和用户数据 | 19-26 |
| `Oauth2Token.java` | AuthenticationToken 包装类 | 整个文件 |

### UserDetail 对象

**文件:** `src/main/java/xiaozhi/common/user/UserDetail.java`

```java
public class UserDetail implements Serializable {
    private Long id;
    private String username;
    private Integer superAdmin;  // 0 = 普通用户, 1 = 超级管理员
    private String token;
    private Integer status;
}
```

`superAdmin` 字段来源于 `sys_user.super_admin` 列。

### Server 路径 (机对机)

**文件:** `src/main/java/xiaozhi/modules/security/secret/ServerSecretFilter.java`

- 从 `Authorization` Header 提取 Bearer Token
- 与 `sys_params` 表中 `SERVER_SECRET` 参数值直接比对
- **不创建 Shiro Subject**，没有 `UserDetail` 可用
- `@RequiresPermissions` 注解在此路径下无效（但 server 路径的端点也不使用该注解）

### 便捷访问工具

**文件:** `src/main/java/xiaozhi/modules/security/user/SecurityUser.java`

Controller 中通过 `SecurityUser.getUser()` 和 `SecurityUser.getUserId()` 获取当前用户。

## 第 3 层: 授权 (Authorization)

### 核心逻辑

**文件:** `src/main/java/xiaozhi/modules/security/oauth2/Oauth2Realm.java` (54-70 行)

```java
protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    UserDetail user = (UserDetail) principals.getPrimaryPrincipal();
    Set<String> permsSet = new HashSet<>();

    if (user.getSuperAdmin() == SuperAdminEnum.YES.value()) {  // superAdmin == 1
        permsSet.add("sys:role:superAdmin");
        permsSet.add("sys:role:normal");
    } else {
        permsSet.add("sys:role:normal");
    }

    SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
    info.setStringPermissions(permsSet);
    return info;
}
```

### 权限矩阵

| 用户类型 | `sys_user.super_admin` | 拥有的权限 |
|----------|----------------------|------------|
| 普通用户 | `0` | `sys:role:normal` |
| 超级管理员 | `1` | `sys:role:normal` + `sys:role:superAdmin` |

### 三类接口的权限效果

| 注解 | 普通用户 | 超级管理员 | 说明 |
|------|----------|------------|------|
| `@RequiresPermissions("sys:role:normal")` | 允许 | 允许 | 登录即可访问 |
| `@RequiresPermissions("sys:role:superAdmin")` | **403** | 允许 | 仅管理员 |
| 无注解 | 允许 | 允许 | 通过认证即可，无额外权限检查 |

### 注解在各 Controller 中的使用

| Controller | 权限注解 | 说明 |
|------------|----------|------|
| `AdminController` | 全部 `sys:role:superAdmin` | 系统管理操作 |
| `SysParamsController` | 全部 `sys:role:superAdmin` | 系统参数管理 |
| `AgentController` | 混合使用 | 用户级操作 `normal`, 管理操作 `superAdmin` |
| `DeviceController` | 大部分 `sys:role:normal` | 设备管理 |
| `LoginController` | 无注解 | `/user/info`, `/user/change-password` 等认证即可 |

### 异常处理

**文件:** `src/main/java/xiaozhi/common/exception/RenExceptionHandler.java` (48-54 行)

```java
@ExceptionHandler(UnauthorizedException.class)
public Result<Void> handleUnauthorizedException(UnauthorizedException ex) {
    Result<Void> result = new Result<>();
    result.error(ErrorCode.FORBIDDEN);  // 返回 HTTP 403
    return result;
}
```

Shiro 抛出 `UnauthorizedException` 时，统一返回 403 错误码。

## 核心代码逐行对照

以下将流程图中的每一步对应到具体代码位置。所有路径相对于 `src/main/java/`。

### 步骤 1: URL 匹配 → 过滤器分发

**文件:** `xiaozhi/modules/security/config/ShiroConfig.java` (74-106 行)

`filterChainDefinitionMap` 配置决定了请求走哪条路径 (`anon` / `server` / `oauth2`)。

### 步骤 2: OAuth2 路径 — 校验 Bearer Token

`Oauth2Filter.java` 的 `onAccessDenied()` 提取 Token 后，Shiro 框架自动调用 Realm 做认证:

**文件:** `xiaozhi/modules/security/oauth2/Oauth2Realm.java` (76-106 行)

```java
// doGetAuthenticationInfo — 认证逻辑
protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
    String accessToken = (String) token.getPrincipal();

    // 80 行: 查 sys_user_token 表，验证 Token 是否存在
    SysUserTokenEntity tokenEntity = shiroService.getByToken(accessToken);

    // 82 行: Token 过期检查 → 过期抛 IncorrectCredentialsException (→ 401)
    if (tokenEntity == null || tokenEntity.getExpireDate().getTime() < System.currentTimeMillis()) {
        throw new IncorrectCredentialsException(...);
    }

    // 87 行: 查 sys_user 表，获取用户信息
    SysUserEntity userEntity = shiroService.getUser(tokenEntity.getUserId());

    // 90 行: 构建 UserDetail (含 superAdmin 字段)
    UserDetail userDetail = ConvertUtils.sourceToTarget(userEntity, UserDetail.class);
    userDetail.setToken(accessToken);

    // 95-98 行: 账号状态为 null → 禁用
    if (userDetail.getStatus() == null) {
        throw new DisabledAccountException(...);
    }
    // 100-102 行: 账号状态为 0 → 锁定
    if (userDetail.getStatus() == 0) {
        throw new LockedAccountException(...);
    }

    // 104 行: 认证成功，返回 SimpleAuthenticationInfo，Shiro Subject 建立
    return new SimpleAuthenticationInfo(userDetail, accessToken, getName());
}
```

### 步骤 3: 授权逻辑 — @RequiresPermissions 检查

Shiro 在遇到 `@RequiresPermissions` 注解时，自动调用 `doGetAuthorizationInfo()` 获取用户权限集合，然后与注解值做比对。

**文件:** `xiaozhi/modules/security/oauth2/Oauth2Realm.java` (54-70 行)

```java
// doGetAuthorizationInfo — 授权逻辑
protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    // 55 行: 从 Shiro Subject 中取出已认证的 UserDetail
    UserDetail user = (UserDetail) principals.getPrimaryPrincipal();

    // 58 行: 构建权限集合
    Set<String> permsSet = new HashSet<>();

    // 60-65 行: 根据 superAdmin 字段决定授予哪些权限
    if (user.getSuperAdmin() == SuperAdminEnum.YES.value()) {  // superAdmin == 1
        permsSet.add("sys:role:superAdmin");   // 61 行: 管理员专属权限
        permsSet.add("sys:role:normal");       // 62 行: 通用权限
    } else {
        permsSet.add("sys:role:normal");       // 64 行: 普通用户只有这一个权限
    }

    // 67-69 行: 将权限集合返回给 Shiro 框架
    SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
    info.setStringPermissions(permsSet);
    return info;
}
```

Shiro 框架拿到权限集合后的比对逻辑:

- 用户权限集合 **包含** 注解值 → **通过**
- 用户权限集合 **不包含** 注解值 → 抛 `UnauthorizedException`

因此:

| 注解 | 普通用户 (superAdmin=0) | 超级管理员 (superAdmin=1) |
|------|------------------------|--------------------------|
| `@RequiresPermissions("sys:role:normal")` | 权限集合中有 `sys:role:normal` → **通过** | 权限集合中有 `sys:role:normal` → **通过** |
| `@RequiresPermissions("sys:role:superAdmin")` | 权限集合中无 `sys:role:superAdmin` → **403** | 权限集合中有 `sys:role:superAdmin` → **通过** |

### 步骤 4: 403 异常处理

**文件:** `xiaozhi/common/exception/RenExceptionHandler.java` (48-54 行)

Shiro 比对失败抛出 `UnauthorizedException`，由全局异常处理器捕获并返回 403。

```java
@ExceptionHandler(UnauthorizedException.class)
public Result<Void> handleUnauthorizedException(UnauthorizedException ex) {
    Result<Void> result = new Result<>();
    result.error(ErrorCode.FORBIDDEN);  // 返回 HTTP 403
    return result;
}
```

## 完整请求流程

```
请求到达
  |
  +-- 匹配 anon URL? -> 直接放行 (无认证)
  |
  +-- 匹配 server URL? -> ServerSecretFilter 校验预共享密钥
  |     |
  |     +-- 密钥匹配 -> 放行 (无 Subject)
  |     +-- 密钥不匹配 -> 401
  |
  +-- 匹配 oauth2 (/** 兜底) -> Oauth2Filter 校验 Bearer Token
        |
        +-- OPTIONS 请求 -> 放行 (CORS)
        +-- 无 Token -> 401
        +-- Token 有效 -> Shiro Subject 建立 (UserDetail)
              |
              +-- 无 @RequiresPermissions -> 直接执行
              |
              +-- @RequiresPermissions("sys:role:normal")
              |     -> 普通用户: 有此权限 -> 通过
              |     -> 管理员:   有此权限 -> 通过
              |
              +-- @RequiresPermissions("sys:role:superAdmin")
                    -> 普通用户: 无此权限 -> 403
                    -> 管理员:   有此权限 -> 通过
```

## 关键文件索引

| 文件 (相对 `src/main/java/` 路径) | 作用 |
|-------------------------------------|------|
| `xiaozhi/modules/security/config/ShiroConfig.java` | Filter Chain 配置, Realm 注册, AOP |
| `xiaozhi/modules/security/oauth2/Oauth2Filter.java` | Bearer Token 提取, 登录触发 |
| `xiaozhi/modules/security/oauth2/Oauth2Realm.java` | 认证 + 授权逻辑 |
| `xiaozhi/modules/security/oauth2/Oauth2Token.java` | AuthenticationToken 包装 |
| `xiaozhi/modules/security/secret/ServerSecretFilter.java` | 预共享密钥校验 |
| `xiaozhi/modules/security/user/SecurityUser.java` | 当前用户便捷访问工具 |
| `xiaozhi/common/user/UserDetail.java` | 用户主体对象 |
| `xiaozhi/common/exception/RenExceptionHandler.java` | 异常处理 (403) |
| `xiaozhi/modules/sys/entity/SysUserEntity.java` | 用户表实体 (含 superAdmin 字段) |
| `xiaozhi/modules/sys/enums/SuperAdminEnum.java` | YES(1) / NO(0) 枚举 |
