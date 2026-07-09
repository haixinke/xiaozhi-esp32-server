# 蛋宝宝小程序 邀请码后端设计

- 日期：2026-07-09
- 范围：`main/manager-api`（Java Spring Boot 后端），不含小程序端
- 依据：`main/egg-miniprogram/蛋宝宝小程序PRD.md` 第 5.1 节

## 1. 背景与目标

蛋宝宝小程序展会版采用邀请制控制初期 token 成本、过滤无效访客、追踪传播链路。本设计为后端邀请码功能：建模、生成、消耗、管理。

PRD 第 5.1 节定义两类邀请码。经与产品确认，关键规则如下：

- **每个主体 1 个邀请码**：每个用户 1 个个人邀请码；每次企业码创建也是 1 个码（不再生成多个码）。
- **个人邀请码**：跟随用户创建自动生成 1 个，总配额 = 5 次（每被消耗一次 remaining -1）。
- **企业邀请码**：由管理员手动创建，配额在创建时人工指定。
- **需记录字段**：总配额数量、已使用数量、剩余数量、有效性字段。
- **消耗时机**：领取蛋时消耗。
- **传播链路**：需记录"谁邀请了谁"，并防同一被邀请人对同一码重复消耗。

PRD 原文存在一处矛盾（第 43 行"一个用户可以有三个邀请码" vs 第 92 行"一个账号只有5个"）。产品确认：**一个用户 1 个个人码，配额 5 次**，即第 92 行的"5"指使用次数而非码数量。本设计按此落地。

## 2. 方案选型

三个候选方案：

- **方案 A（采用）**：统一码表 `ai_invite_code` + 使用记录表 `ai_invite_usage`，用 `type` 区分个人/企业；个人码在微信注册流程自动生成；消耗走独立接口。
- 方案 B：企业码 / 个人码分两张表。1 码/主体模型下字段大量重复，查询需 UNION，无收益。否决。
- 方案 C：统一码表，但个人码按需生成。违背 PRD"跟随用户创建"，且产生"用户暂时无码"中间态。否决。

采用方案 A：模型最简、贴合 PRD、传播链路完整、模块自包含。

## 3. 数据模型

遵循仓库现有 `ai_` 表约定（InnoDB / utf8mb4 / COMMENT / `creator`-`create_date` / `updater`-`update_date`，与 `pet` 模块风格一致）。

### 3.1 表 1：`ai_invite_code`（邀请码主表，一行一码）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT AUTO_INCREMENT PK | 主键 |
| `code` | VARCHAR(32) NOT NULL, UNIQUE | 邀请码字符串（8 位 base32 去歧义字符，全局唯一索引） |
| `type` | TINYINT NOT NULL | 1=个人邀请码 2=企业邀请码 |
| `owner_user_id` | BIGINT NULL | 个人码=归属用户 id；企业码=NULL |
| `quota` | INT NOT NULL | 总配额数量（个人码=5，企业码=人工指定） |
| `used_count` | INT NOT NULL DEFAULT 0 | 已使用数量 |
| `remaining` | INT NOT NULL | 剩余数量（不变式：remaining = quota - used_count） |
| `status` | TINYINT NOT NULL DEFAULT 1 | 0=失效 1=有效 |
| `expire_time` | DATETIME NULL | 过期时间，NULL=不过期 |
| `remark` | VARCHAR(255) NULL | 备注（企业码用，如"展会A渠道"） |
| `creator` / `create_date` / `updater` / `update_date` | | 审计字段 |

索引：`UNIQUE uk_code(code)`、`KEY idx_owner(owner_user_id, type)`、`KEY idx_status(status)`。

### 3.2 表 2：`ai_invite_usage`（使用记录表）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT AUTO_INCREMENT PK | 主键 |
| `code_id` | BIGINT NOT NULL | 关联 `ai_invite_code.id` |
| `invitee_user_id` | BIGINT NOT NULL | 被邀请人 user_id |
| `create_date` | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 消耗时间 |

索引：`UNIQUE uk_code_invitee(code_id, invitee_user_id)`（防同一被邀请人对同一码重复消耗）、`KEY idx_invitee(invitee_user_id)`（反查"我是被谁邀请的"）。

### 3.3 关键决策

- `remaining` 作为存储列而非计算列：满足"记录剩余数量"的明确要求；与 `used_count` 在同一事务、行锁下同步更新，保持不变式 `remaining = quota - used_count`。
- `type` 区分而非分表：1 码/主体模型下字段完全共用，分表无收益。
- 使用记录唯一键：保证"一个被邀请人对同一个码只能消耗一次"；多码场景下同一人仍可被不同码邀请。
- 不冗余 `invitee_openid`：需要时 join `ai_wechat_user` 即可。
- Liquibase：新增一个 changeSet，文件名按"当前时分"命名（如 `202607091500.sql`，具体时间戳在实现时确定），注册到 `db.changelog-master.yaml`。遵循"只新建 changeSet、不改历史"约定。

## 4. 模块与接口

新建 `main/manager-api` 下 `invite` 模块，遵循现有 `controller/service/dao/entity/dto/vo` 分层，返回 `Result<T>`。

```
modules/invite/
├── entity/   InviteCodeEntity.java, InviteUsageEntity.java
├── dao/      InviteCodeDao.java, InviteUsageDao.java   (继承 BaseMapper)
├── dto/      InviteCodeCreateDTO.java, InviteConsumeDTO.java, InviteCodeUpdateDTO.java
├── vo/       InviteCodeVO.java, InviteUsageVO.java
├── service/  InviteService.java + impl/InviteServiceImpl.java
└── controller/ InviteController.java   (管理员侧)  +  InviteMpController.java  (小程序侧)
```

### 4.1 管理员侧（`/invite`，走 Shiro 管理员认证，供 manager-web）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/invite` | 创建企业邀请码（人工指定 quota、expire_time、remark） |
| PUT | `/invite` | 编辑企业码（quota 仅可调增、status 启停、expire_time、remark） |
| DELETE | `/invite/{id}` | 删除企业码（仅 used_count=0 可删） |
| GET | `/invite/page` | 分页查询码列表（按 type/owner/状态筛选） |
| GET | `/invite/{id}/usage` | 查某码的使用记录（传播链路） |
| GET | `/invite/stats` | 概览统计（总码数、总消耗、按类型） |

### 4.2 小程序侧（`/mp/invite`，走微信登录态 token）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/mp/invite/mine` | 查"我的"个人邀请码（含 quota/used/remaining/状态） |
| POST | `/mp/invite/consume` | 消耗邀请码领蛋（body: `{code}`，当前登录用户作为被邀请人） |

### 4.3 关键决策

- 个人码不暴露创建接口：自动生成，用户只能查和分享。
- 企业码 quota 只允许调增：防止运营误操作把已发放码额度砍到低于 used_count 造成数据矛盾。
- `/mp/invite/consume` 是消耗的唯一入口：内部完成"校验→扣减→写使用记录"原子事务；未来/现有的领取蛋流程（`/pet/birth` 等）调用此接口或直接调用 `InviteService.consume`。先做独立接口，等领蛋流程落地再决定是接口调用还是服务层直调。
- 个人码自动生成：`WechatServiceImpl.createSysUserForOpenid` 末尾注入 `InviteService.createPersonalCode(userId)`（quota 从系统参数读，默认 5）。与现有 `AgentService` 注入方式一致，轻量耦合。

## 5. 消耗流程与并发控制

### 5.1 `POST /mp/invite/consume` 流程（`InviteService.consume(code, inviteeUserId)`）

1. 取当前登录用户 userId（被邀请人）—— 由 controller 从 `SecurityUser` 注入。
2. 根据 code 查 `ai_invite_code`（`SELECT ... FOR UPDATE` 行锁）。
3. 校验（任一失败即抛 `ErrorCode`，事务回滚）：
   a. 码存在
   b. `status == 1`（有效）
   c. `expire_time` 为 NULL 或 > now
   d. `remaining > 0`
   e. `owner_user_id != inviteeUserId`（防自邀；企业码 owner 为 NULL 不受限）
4. 校验唯一键：查 `ai_invite_usage` 是否已存在 `(code_id, inviteeUserId)` → 存在则幂等返回"已使用过该码"，不再扣减。
5. 原子扣减：`UPDATE ai_invite_code SET used_count=used_count+1, remaining=remaining-1 WHERE id=? AND remaining>0` → `affectedRows=0` 视为并发抢空，回退到"已无剩余"错误。
6. `INSERT ai_invite_usage(code_id, invitee_user_id, create_date=now)` → 唯一键 `uk_code_invitee` 兜底防并发重复。
7. 提交事务，返回 `InviteConsumeVO`（剩余、是否仍在有效期内等）。

### 5.2 并发与幂等要点

- 行锁 + `remaining>0` 条件更新：双重保险防超扣。先 `FOR UPDATE` 锁行做前置校验，再用条件 UPDATE 扣减；即便有锁间隙也能靠 `remaining>0` 拦住。
- 幂等：同一被邀请人对同一码重复调用不重复扣减、不报错，返回"已使用"。配合唯一键，并发下至多一条 usage 成功。
- 事务隔离：`@Transactional(rollbackFor = Exception.class)`，扣减与 usage 写入同事务。
- 自邀拦截：个人码 `owner_user_id` = 邀请人本人，禁止消耗自己的码；企业码 owner 为 NULL，不受此限制。

## 6. 错误处理与边界

| 场景 | 行为 | 错误码/提示 |
|---|---|---|
| 码不存在 | 抛异常 | 邀请码无效 |
| status=0 | 抛异常 | 邀请码已失效 |
| 已过期 | 抛异常 | 邀请码已过期 |
| remaining=0 | 抛异常 | 邀请码已无剩余 |
| 自邀（消耗自己的个人码） | 抛异常 | 不能使用自己的邀请码 |
| 同一被邀请人重复消耗同码 | 幂等成功，不扣减 | 返回"已使用过该邀请码"状态 |
| 并发抢空（条件 UPDATE 0 行） | 抛异常 | 邀请码已无剩余 |
| 企业码删除时有 used_count>0 | 拒绝 | 已被使用，无法删除 |
| 个人码配额参数缺失 | 创建时取默认值 5 | 日志 warn |
| 输入 code 为空/格式非法 | 参数校验 | NOT_NULL / 格式错误 |

错误走仓库现有 `RenException(ErrorCode)` + `Result` 信封，不泄漏内部细节。

## 7. 测试策略

仓库测试用 `mvn test -DskipTests=false`，按现有 `pet`/`wechat` 模块的 Spring Boot 测试风格。针对邀请码核心逻辑覆盖单元 + 集成两层，目标核心路径覆盖 ≥80%。

### 7.1 单元测试（`InviteServiceImplTest`，mock dao）

- 正常消耗：有效码 + remaining>0 → used+1、remaining-1、usage 写入成功。
- 码不存在 / status=0 / 已过期 / remaining=0 → 抛对应异常，无扣减。
- 自邀拦截：owner_user_id == inviteeUserId → 抛异常。
- 幂等：同 (code, invitee) 重复调用 → 不扣减、不重复写 usage、返回"已使用"。
- 并发抢空：条件 UPDATE 返回 0 行 → 抛"无剩余"。
- 企业码创建：quota/expire_time/remark 正确落库，code 全局唯一。
- 企业码编辑：quota 调增允许、调减拒绝、status 启停。
- 个人码自动生成：`createPersonalCode(userId)` 生成 type=1、quota=5、remaining=5、owner_user_id 正确。

### 7.2 集成测试（`InviteConsumeIntegrationTest`，真实内存库 + 事务回滚）

- 完整消耗 HTTP 链路：登录 → `/mp/invite/consume` → 主表扣减 + usage 表落库。
- 唯一键并发：多线程同 (code, invitee) 并发消耗，断言 usage 仅 1 行、used_count 仅 +1。
- 多被邀请人并发抢同一码（remaining=2，3 人抢）：断言成功 2 人、1 人得"无剩余"。
- 管理员侧 CRUD + 权限：非管理员调用 `/invite` 被拦截。

### 7.3 关键点

- 并发测试用 `CountDownLatch` 同步起跑，覆盖行锁 + 条件 UPDATE 双保险。
- 幂等测试断言"第二次调用不抛异常且 used_count 不变"，而非简单成功。
- 时间相关（过期）用可注入 `Clock`/时间参数，避免依赖系统时间导致 flaky。

## 8. 落地范围（本次）

本次仅后端：
- 两张表 + Liquibase changeSet。
- `invite` 模块完整代码（entity/dao/dto/vo/service/controller）。
- `WechatServiceImpl` 注入个人码自动生成一行。
- 单元 + 集成测试。
- 不含小程序端 UI 与调用改造，不含 manager-web 管理页面（仅提供后端 API 供后续接入）。
