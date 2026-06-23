# 定时任务独立部署方案

## 背景

当前 `manager-api` 同时承载：
1. 控制台 REST API 业务
2. 3 个 `@Scheduled` 定时任务（订阅到期、支付订单维护、知识库文档同步）

水平扩展时多个实例同时执行定时任务会造成重复处理。

## 技术方案：Spring Profile 条件激活

**核心思路**：用 `@ConditionalOnProperty` 或 Profile 控制定时任务 Bean 的注册，使 manager-api（`api` profile）不加载任务 Bean，task-worker（`task` profile）专门运行任务，两者共享同一套代码，只是启动 profile 不同。

---

## Task 1：给定时任务加 `@ConditionalOnProperty` 条件注解

修改 3 个任务类，加上条件注解使其仅在 `task` profile 下才注册：

**文件：**
- `main/manager-api/src/main/java/xiaozhi/modules/subscription/task/SubscriptionExpirationTask.java`
- `main/manager-api/src/main/java/xiaozhi/modules/payment/task/PaymentOrderMaintenanceTask.java`
- `main/manager-api/src/main/java/xiaozhi/modules/knowledge/config/RAGTaskConfig.java`
- `main/manager-api/src/main/java/xiaozhi/modules/knowledge/task/DocumentStatusSyncTask.java`

**改法（每个任务类）：**
```java
// 在 @Component 前添加
@Profile("task")
```

以及 `RAGTaskConfig` 也加 `@Profile("task")`，这样 `@EnableScheduling` 也仅在 task profile 生效。

---

## Task 2：配置文件调整

在 `main/manager-api/src/main/resources/` 下新增 `application-task.yml`，设置 task-worker 专用配置（关闭 Swagger、关闭 HTTP 端口或换用随机端口以节省资源）：

```yaml
# application-task.yml
server:
  port: 0   # 随机端口，task-worker 不对外提供 HTTP 接口

springdoc:
  api-docs:
    enabled: false
knife4j:
  enable: false

# 可选：关闭不需要的 actuator 端点
management:
  endpoints:
    web:
      exposure:
        include: health
```

`application-api.yml`（供 manager-api 正常实例使用，保持 port: 8002）：
```yaml
# application-api.yml（可选，或直接用默认 application.yml）
# 不含 task 相关内容，保持现状
```

---

## Task 3：新增 task-worker Dockerfile

`Dockerfile-task-worker`（放在项目根目录，与现有 Dockerfiles 并列）：

```dockerfile
FROM manager-api:latest
ENV SPRING_PROFILES_ACTIVE=task
CMD ["java", "-jar", "/app/xiaozhi-esp32-api.jar", "--spring.profiles.active=task"]
```

或者在现有 `Dockerfile-server` 基础上复制修改，仅覆盖 `SPRING_PROFILES_ACTIVE`。

---

## Task 4：部署说明

- **manager-api 正常实例**：启动时加 `--spring.profiles.active=api`（或不指定，默认不激活 `task` profile 即可）
- **task-worker 实例**：启动时加 `--spring.profiles.active=task`，只部署 1 个实例（单实例运行，无重复执行风险）
- 两者共用同一镜像，通过环境变量 `SPRING_PROFILES_ACTIVE` 区分

**SAE / K8s 部署**：task-worker 部署为单独的应用，实例数固定为 1，manager-api 可按需水平扩展。

---

## Task 5：验证

1. 启动 manager-api（不带 `task` profile）：确认 3 个任务类 Bean 不在 Spring 上下文中（查看启动日志无 `SubscriptionExpirationTask` 等类名）
2. 启动 task-worker（`--spring.profiles.active=task`）：确认定时任务正常触发，日志输出扫描信息
3. 同时运行两者：确认 API 实例不执行任务，task-worker 正常执行任务

---

## 文件清单

| 操作 | 文件 |
|------|------|
| 修改 | `modules/subscription/task/SubscriptionExpirationTask.java` |
| 修改 | `modules/payment/task/PaymentOrderMaintenanceTask.java` |
| 修改 | `modules/knowledge/config/RAGTaskConfig.java` |
| 修改 | `modules/knowledge/task/DocumentStatusSyncTask.java` |
| 新增 | `src/main/resources/application-task.yml` |
| 新增 | `Dockerfile-task-worker`（项目根目录） |

