# 蛋宝宝孵化闭环后端实现参考

> 范围：`manager-api` 中蛋宝宝（egg）从领养到破壳的后端实现。供后期开发参考。
> 关联：[`egg-miniprogram/docs/egg-pet-identity-and-hatch-api.md`](../../egg-miniprogram/docs/egg-pet-identity-and-hatch-api.md)（接口契约/草案）、[`egg-miniprogram/CLAUDE.md`](../../egg-miniprogram/CLAUDE.md)（小程序侧交互）。
> 状态：adopt / hatch-action / hatch / `GET /pet/{id}` / 每日心情 todayMood 已落地并通过单测；OTA→xiaozhi-server WS 真机联调、AI 生图、旧端点 `@Deprecated` 迁移待做。

## 1. 全景

蛋宝宝是「一人多宠」的孵化型 AI 宠物。后端把生命周期拆成三段，对应三个端点族：

```
wx.login → /wechat/login(token,userId)
   │
   ├─ POST /pet/adopt            领养：建 ai_pet(EGG, device_id=null) + 核销邀请码 + 设 Model X 基线
   │
   ├─ POST /pet/{id}/hatch-action  修炼(5动作)：累加 accelerated_minutes，下推 expectedHatchTime
   ├─ GET  /pet/{id}/hatch-actions  修炼动作明细
   │
   ├─ POST /pet/{id}/hatch         破壳：建虚拟 ai_device + ai_agent(个性=system_prompt) + 生成档案 → HATCHED
   ├─ GET  /pet/{id}               单宠查询(归属校验)
   │
   └─ 破壳后：小程序用 activeDeviceId 走 OTA(/ota/) 拿 websocket → 直连 xiaozhi-server:8000 语音对话
```

身份模型（多宠关键）：**一只蛋 = 一个虚拟 `ai_device` = 一个 `ai_agent`**。`openid` 只换 `token/userId`，不进 device；否则所有蛋共用一条 device/agent，性格音色无法区分，且 `uk_ai_pet_device_id` 唯一索引阻止第二只蛋。破壳时才建 device+agent（懒创建），领养阶段 `device_id=null`。

## 2. 数据模型

### 2.1 `ai_pet`（复用，不改 schema）

孵化相关字段由 changeset `202607101030.sql` 补齐，`device_id` 由 `202607101500.sql` 放宽为可空（原 NOT NULL）：

| 字段 | 说明 | 谁写 |
|---|---|---|
| `id` | ASSIGN_UUID | adopt |
| `user_id` | 微信用户 | adopt |
| `device_id` | 虚拟设备 id（领养为 NULL，破壳回填） | hatch |
| `nickname` | 昵称（≤10字符+敏感词） | hatch-action(NICKNAME) / PUT /pet/update |
| `hatch_status` | EGG / HATCHED | adopt=EGG, hatch=HATCHED |
| `hatch_start_time` | **Model X 基线=adopt 时刻** | adopt |
| `expected_hatch_time` | 预计破壳时间（adopt+7d 起算，动作下推） | adopt + hatch-action |
| `hatched_at` | 实际破壳时间（=生日） | hatch |
| `accelerated_minutes` | 累计已加速分钟 | hatch-action |
| `bazi`/`wuxing`/`zodiac` | 命理（破壳时刻算） | hatch |
| `mbti`/`personality`/`personality_brief` | MBTI、系统提示词、20字卡片语 | hatch |
| `gender`/`blood_type`/`avatar_url`/`prototype` | 性别、血型、头像、原型(锦鲤/玉兔) | adopt(prototype), hatch(其余) |
| `today_mood`/`today_mood_date`/`today_mood_sentence` | 今日心情(开心/平静/想念/兴奋/低落)、对应日期(Asia/Shanghai)、一句话 | `refreshTodayMood` 懒生成（见 §5.8） |

### 2.2 `ai_pet_hatch_action`（新增，changeset `202607101600.sql`）

修炼动作明细表：

```sql
CREATE TABLE `ai_pet_hatch_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `pet_id` VARCHAR(32) NOT NULL,
    `action_type` VARCHAR(20) NOT NULL,          -- NICKNAME/CUDDLE/WISH/LESSON/DOODLE
    `payload` TEXT NULL,                          -- JSON：nickname/wish-value/lesson-value/doodle-color等
    `action_date` DATE NOT NULL,                  -- Asia/Shanghai 日界，幂等用
    `accelerated_minutes` INT NOT NULL DEFAULT 0,
    `creator` BIGINT NULL, `create_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_pet_action_date` (`pet_id`, `action_type`, `action_date`),
    INDEX `idx_pet_id` (`pet_id`)
);
```

唯一索引兜底「每日一次」；「一次性」动作跨天重复由业务层 `selectCount` 拦截。

### 2.3 `ai_device` / `ai_agent`（复用，破壳时手动插/建）

- `ai_device`：破壳手动插一行（`id=ASSIGN_UUID`、`mac_address=ai_device.id`、`board=wechat-egg-miniprogram`、`auto_update=0`、`alias=nickname`、`agent_id`）。
- `ai_agent`：`AgentService.createAgent` 走默认模板（ASR/VAD/LLM/TTS/voice/memory/intent/systemPrompt），再 `update` 该 agent 的 `system_prompt = personality`。

### 2.4 错误码（`ErrorCode`，102xx 段）

| 常量 | 值 | 含义 | 状态 |
|---|---|---|---|
| `PET_DEVICE_NOT_FOUND` | 10205 | 宠物关联设备不存在 | 旧 birth 用 |
| `PET_ALREADY_EXISTS` | 10206 | 该设备已创建过宠物 | 旧（多宠下不再适用） |
| `PET_NOT_FOUND` | 10207 | 宠物不存在 | ✅ |
| `PET_NO_PERMISSION` | 10208 | 没有权限操作该宠物 | ✅ |
| `PET_ALREADY_HATCHED` | 10209 | 已破壳（hatch-action / hatch 守卫） | ✅ |
| `PET_HATCH_TIME_NOT_REACHED` | 10214 | 未到预计破壳时间 | ✅ |
| `PET_NOT_HATCHABLE` | 10213 | 蛋未破壳不能进聊天 | 草案，未落地 |
| ~~`PET_HATCH_NOT_STARTED`~~ | ~~10215~~ | 已取消（Model X：`hatchStartTime` 由 adopt 设，永非 null） | 取消 |

> 编号注：`COMPANION_NOT_FOUND=10210` 已占用，故 hatch 相关码用 10209/10214，10215 取消，10213 留给后续「未破壳禁入聊天」。

## 3. Model X 时间模型（重要，偏离 PRD §5.3）

PRD §5.3 是「双轨孵化（进度不减时）」；本实现按产品确认的**草案模型（任务减时）**落地：

- **adopt 设基线**：`hatchStartTime = now`、`expectedHatchTime = now + 7d`（7d = 10080 分）。
- **hatch-action 只减时**：`acceleratedMinutes += 动作分钟`；重算 `expectedHatchTime = hatchStartTime + 7d − acceleratedMinutes`，clamp `≥ hatchStartTime`（不会早于倒计时起点）。**动作永远让破壳更早，绝不推迟。**
- **hatch 前置**：`hatchStatus == EGG` 且 `now >= expectedHatchTime`。无动作蛋到 `adopt + 7d` 即可破；有动作蛋更早破。
- **日界**：`action_date = LocalDate.now(ZoneId.of("Asia/Shanghai"))`，与 `PetBirthCalculator` 时区一致。
- **进度派生**：后端不存 `progress%`，前端由 `acceleratedMinutes / 10080` 派生进度条。

> 历史教训：曾一度用「首个动作起算」（`hatchStartTime` 在首个 hatch-action 才写）。这会导致「用户晾 6 天才做首个动作 → 破壳日从 day7 推到 day13」，做动作反而更晚破壳。Model X 把基线移到 adopt 时刻修复了该问题。**改这块前务必保持 adopt 设基线、hatch-action 只减时。**

## 4. 端点契约（均已落地）

### 4.1 `POST /pet/adopt`（鉴权 normal）
- 入参 `PetAdoptDTO { inviteCode @NotBlank }`
- 行为：prototype 后端随机（锦鲤/玉兔，与 inviteCode 解耦）→ 建 `ai_pet`（EGG、`device_id=null`、设 Model X 基线、不生成档案）→ `InviteService.consume(inviteCode, userId)`（幂等，`@Transactional(REQUIRES_NEW)`；无效码抛异常 → 外层事务回滚 insert，不产生孤儿蛋）
- 出参 `PetVO`（前端 stage=waiting）

### 4.2 `POST /pet/{id}/hatch-action`（鉴权 normal）
- 入参 `HatchActionDTO { type @NotBlank, payload: Map<String,Object> }`
- 五动作：

| type | 加速 | 幂等 | payload |
|---|---|---|---|
| `NICKNAME` | 720 分（12h） | 一次性 | `{ nickname }`（≤10字符+敏感词） |
| `CUDDLE` | 60 分 | 每日 | `{ }` |
| `WISH` | 60 分 | 每日 | `{ value }` |
| `LESSON` | 60 分 | 每日 | `{ value }` |
| `DOODLE` | 720 分（12h） | 一次性 | `{ color, colorName, pattern }`（不做 AI 生图） |

- 出参 `HatchActionResultVO { addedMinutes, alreadyDone, readyToHatch, pet: PetVO }`。当日已完成/一次性已完成 → `alreadyDone=true, addedMinutes=0`，不重复减时。
- 已破壳 → `PET_ALREADY_HATCHED`(10209)。

### 4.3 `GET /pet/{id}/hatch-actions`（鉴权 normal）
- 出参 `List<HatchActionVO>`（按 `create_date` desc），供前端渲染当前 shell/wish/lesson（后端为唯一事实源）。

### 4.4 `POST /pet/{id}/hatch`（鉴权 normal，单事务）
- 前置：归属 → `hatchStatus==EGG`（否 `PET_ALREADY_HATCHED`）→ `now >= expectedHatchTime`（否 `PET_HATCH_TIME_NOT_REACHED`）
- 行为：
  1. `PetBirthCalculator.calculate(now)` → bazi/wuxing/zodiac
  2. `deriveMbti(calc)`（LLM 推，不可用兜底 INFP）→ `derivePersonality(mbti)`（LLM 生成系统提示词，不可用兜底默认）
  3. `personalityBrief` = 内置卡片语池随机（不调 LLM）
  4. `gender`/`bloodType` 随机；`avatarUrl` = 按 `prototype` 从 `pet.avatar.koi/rabbit` 配置池随机（池空走内置默认，**非 AI 生图**）
  5. `agentService.createAgent(name=nickname或prototype)` 拿默认模板 → `agentService.update(UpdateWrapper set system_prompt=personality)`
  6. 手动插 `ai_device`：`id=IdUtil.simpleUUID()`、`mac_address=id`、`board=wechat-egg-miniprogram`、`auto_update=0`、`alias=nickname`、`agent_id`、`app_version=1.0.0`
  7. 回填 `ai_pet`：`deviceId/hatchStatus=HATCHED/hatchedAt/birthDate/bazi/wuxing/zodiac/mbti/personality/personalityBrief/gender/bloodType/avatarUrl`
  8. 返回 `PetVO`（含 `deviceId`，前端据此走 OTA）
- 整个方法 `@Transactional(rollbackFor=Exception.class)`；pet+agent+device 全 DB 操作，一致回滚。

### 4.5 `GET /pet/{id}`（鉴权 normal）
- 按 petId 查 + 归属校验 → 调 `refreshTodayMood`（见 §5.8，按需生成今日心情并写回）→ `PetVO`（含 `todayMood/todayMoodDate/todayMoodSentence`）。替代旧 `GET /pet/detail/{deviceId}`（旧端点暂保留兼容）。

### 4.6 既有端点（保留）
- `GET /pet/list`：当前用户所有蛋（按 create_date desc）。
- `PUT /pet/update`：改昵称（无奖励，奖励只在 hatch-action NICKNAME 首次）。
- `POST /pet/birth` / `GET /pet/detail/{deviceId}`：旧「创建即出生」演示逻辑，待标 `@Deprecated` 迁移。

## 5. 关键实现细节

### 5.1 设备创建：手动插 vs `deviceActivation`
`CompanionServiceImpl`（笨笨女友）走 `checkDeviceActive` → `deviceActivation`（OTA + Redis 激活码）建设备。蛋是虚拟设备，破壳时**直接 `deviceDao.insert`**，等价且更简单，无需 OTA/Redis/激活码。`deviceActivation` 本质也是从 Redis 缓存读 mac/board 后 insert `ai_device`，手动插与之一致。

### 5.2 `macAddress` 必须等于 `ai_device.id`
OTA `getDeviceByMacAddress(mac)` 用 `eq("mac_address", mac)` 精确查。小程序发的 `mac = activeDeviceId = ai_device.id`（完整 UUID）。故 `mac_address` 必须存完整 `ai_device.id`，**不能用 `egg-{uuid前8位}`**（会查不到）。draft 原草案的 `egg-{8}` 已修正。

### 5.3 agent 个性注入
`createAgent(AgentCreateDTO)` 只收 `agentName`，默认模板填模型/音色/systemPrompt，且 `userId=SecurityUser.getUserId()`（当前微信用户）。要让蛋的个性生效，**必须 create 后再 `update` agent `system_prompt = personality`**（companion `syncPromptToAgent` 同款手法）。

### 5.4 avatar 分类池
`application-dev.yml`：
```yaml
pet:
  avatar:
    koi: https://.../koi1.png;https://.../koi2.png
    rabbit: https://.../rabbit1.png;https://.../rabbit2.png
```
`@Value` 注入两串（默认空），分号 split 去 blank + 内置默认池合并，按 `prototype` 随机取。池空走内置默认。**不做 AI 生图**（代码库无生图能力）。

### 5.5 LLM 依赖与兜底
破壳调 2 次 LLM（MBTI + personality），可能 5–15s。`deriveMbti`/`derivePersonality` 已有 `llmService.isAvailable()` 兜底（INFP / DEFAULT_PERSONALITY），LLM 不可用不影响破壳。MVP 接受同步；后续可改异步生成档案。

### 5.6 单事务
`hatch()` 用 `@Transactional(rollbackFor=Exception.class)`。`createAgent`（含默认插件批量插）全 DB 操作、不涉 Redis，与 pet/device 同事务一致回滚。与 companion 两阶段（设备绑定涉 Redis 在事务外）不同——蛋手动插设备无需 Redis。

### 5.7 PetServiceImpl 构造器
从 `@AllArgsConstructor` 改为 `@RequiredArgsConstructor`：8 个 final 依赖字段（petDao/deviceDao/llmService/chatHistoryDao/memoryDao/userProfileDao/inviteService/agentService）进构造器，avatar `@Value` 字段非 final 不入构造器。**新增依赖时所有手工构造 PetServiceImpl 的测试都要更新参数列表**（PetServiceImplAdoptTest 已是 8 参）。

### 5.8 每日心情 todayMood（懒生成，PRD §8）

PRD §8 要求「已绑定蛋每天最多一句状态文案，按需生成、当天不变次日重算」。落地方式：

- **不新增端点**：心情随 `GET /pet/{id}` / `GET /pet/list` 的 `PetVO` 返回（`todayMood`/`todayMoodDate`/`todayMoodSentence` 字段早就在 VO）。`getById`/`listByUserId` 加载 pet 后、`toVO` 前调 `refreshTodayMood(pet)`。
- **懒生成（lazy on read）**：`refreshTodayMood` 若 `today_mood_date != 今日(Asia/Shanghai)` 则重新生成，幂等 `UPDATE ai_pet` 写回（`WHERE today_mood_date IS NULL OR != 今日`），本地反射字段保证本次 VO 一致。已今日则直接返回，不写库不调 LLM。
- **5 类心情**（`TodayMood` 枚举，PRD §8.6）：开心/平静/想念/兴奋/低落。判定（`MoodDecider.decide`，复刻前端 `pet-store.getDailyStatus`）：
  - `inactiveDays ≥ 4` → 低落；`≥ 2` → 想念
  - 孵化期 `expectedHatchTime` 临近/已过（≤1天 或 <0）→ 兴奋
  - 12h 内有活跃（baseline 距今 <12h）→ 开心
  - 否则 mbti 软分桶：`E*` → 兴奋，`I*` → 平静；无 mbti → 按 baseline 奇偶取开心/平静
- **活跃度基线**（`MoodDecider.baseline`）：孵化期 = `hatchStartTime`（无则 `createDate`）；破壳后 = `hatchedAt`（无则 `createDate`）。MVP 不接真实逐次互动时间（后端无 `lastInteractionAt` 字段），用阶段基线兜底；后续可接 chat-history 最近消息时间。
- **文案生成（两者，LLM 失败兜底静态）**：`llmService.isAvailable()` 为 true 时 `generateSummary("", MOOD_SENTENCE_PROMPT)` 生成 ≤20字（超 30 截断、去引号）；不可用或抛异常则用 `MoodLinePool.pick(hatched, mood, date)` 静态池兜底（egg/pet 两套、每类 3 句、按日期 hashCode 确定性取，同一天同句）。与 derivePersonality 同款 try/catch 兜底手法。
- **阶段文案池**：孵化期写「壳里的动静/等待/被照顾/即将破壳」，破壳后写「心情/行为/想念/今天在做什么」（PRD §8.7），不可混用。
- **不调 LLM 的场景**：`today_mood_date == 今日` 直接返回；故二次拉取零成本。首次拉取若 LLM 不可用走静态池，下次跨天重试 LLM。
- **遗留 `PetMood`（util）**：旧的 8 类纯随机枚举，仅 `birth()`（@Deprecated）用。新流程走 `TodayMood`（5 类、互动驱动），二者并存，迁移 birth 时再统一。

## 6. 文件地图

| 文件 | 内容 |
|---|---|
| `db/changelog/202607101030.sql` | ai_pet 孵化字段（hatch_status/start/expected/hatched_at/accelerated_minutes/avatar/prototype/gender/blood_type/personality_brief/today_mood_*） |
| `db/changelog/202607101500.sql` | ai_pet.device_id 放宽为可空 |
| `db/changelog/202607101600.sql` | ai_pet_hatch_action 建表 |
| `pet/dto/PetAdoptDTO.java` | adopt 入参（inviteCode） |
| `pet/dto/HatchActionDTO.java` | hatch-action 入参（type, payload） |
| `pet/constant/HatchActionType.java` | 5 动作枚举（minutes + oneTime + from()） |
| `pet/constant/TodayMood.java` | 今日心情 5 类枚举（开心/平静/想念/兴奋/低落，中文 label） |
| `pet/constant/MoodLinePool.java` | egg/pet 两套静态文案池（每类 3 句，按日期确定性取） |
| `pet/util/MoodDecider.java` | 心情判定器（复刻前端 getDailyStatus）+ baseline 选取 |
| `pet/entity/HatchActionEntity.java` + `pet/dao/HatchActionDao.java` | 动作明细实体/Mapper |
| `pet/vo/HatchActionResultVO.java` / `HatchActionVO.java` | 动作响应/列表 VO |
| `pet/service/PetService.java` | adopt / hatch / getById / list / update / refreshTodayMood / 旧 birth/getByDeviceId |
| `pet/service/impl/PetServiceImpl.java` | adopt（设基线）/ hatch（破壳全流程）/ getById / refreshTodayMood / generateMoodSentence / deriveMbti / derivePersonality / toVO / avatar 池 / brief 池 |
| `pet/service/HatchActionService.java` + `impl/HatchActionServiceImpl.java` | 5 动作记录 + 减时重算 + 幂等 + 昵称校验 + listByPetId |
| `pet/controller/PetController.java` | adopt / hatch-action / hatch-actions / hatch / {id} / list / update / birth / detail |
| `common/exception/ErrorCode.java` | 10205–10209, 10214 |
| `application-dev.yml` | pet.avatar.koi / pet.avatar.rabbit |
| `test/.../PetServiceImplAdoptTest.java` | adopt 5 用例（含 Model X 基线断言） |
| `test/.../HatchActionServiceImplTest.java` | hatch-action 9 用例（基于 adopt 基线重算） |
| `test/.../PetServiceImplHatchTest.java` | hatch 6 用例 |
| `test/.../MoodDeciderTest.java` | 心情判定 12 用例（5 类分支 + EGG/HATCHED 分流） |
| `test/.../PetServiceImplTodayMoodTest.java` | 今日心情 8 用例（懒生成/幂等/LLM 失败兜底/EGG 池/list+getById 接入） |

## 7. 测试约定

- 框架：JUnit5 + Mockito + AssertJ，纯单测（不撞 DB）。
- `@BeforeAll initMessageSource()`：`RenException(int)` 构造走 `MessageUtils` i18n 查 `SpringContextUtils.applicationContext`，单测无 Spring 上下文需 mock 注入（照抄 `PetServiceImplAdoptTest`）。
- 构造被测 service 用 mock 依赖；`toVO` 是 PetServiceImpl 自身方法，构造真实实例即可直接用。
- `deriveMbti`/`derivePersonality`：mock `llmService.isAvailable()` 返回 false → 走兜底，避免单测依赖 LLM。
- `refreshTodayMood` 同样 mock `isAvailable()`，覆盖 LLM 文案 / 静态兜底 / 异常兜底 / EGG 池 / 幂等不重生。
- 现状：adopt 5 + hatch-action 9 + hatch 6 + mood-decider 12 + todayMood 8 = **40/40 绿**。

## 8. 待办与风险

| 项 | 说明 |
|---|---|
| **OTA→xiaozhi-server WS 真机联调** | manager-api `checkDeviceActive` 会返回 websocket+token（token 由 mac+clientId 生成）。但 xiaozhi-server(Python) 侧解析 token→mac→device→agent 的真实链路未真机验证。需小程序 + 聊天服务联调；若 Python 侧对未走 `deviceActivation` 的设备有特殊分支，需适配。 |
| 旧端点迁移 | `POST /pet/birth` / `GET /pet/detail/{deviceId}` 待标 `@Deprecated`，存量演示数据视为已破壳。 |
| AI 生图 | 头像用预置配置池，未做 AI 生图（代码库无生图能力）。后续集成 provider 后可异步回填 `avatar_url`。 |
| 每日心情 | ✅ 已落地（懒生成于 `GET /pet/{id}`/`list`，LLM 失败兜底静态池，见 §5.8）。后续可接 chat-history 最近消息时间作破壳后真实活跃度基线（现为 `hatchedAt` 兜底）。 |
| 多宠 UI | `pages/home` 现按单只蛋渲染；多宠后改列表+当前蛋。MVP 先单宠（`activePetId` 固定第一只）。 |
| LLM 延迟 | hatch 同步 2 次 LLM 调用，可能 5–15s；后续可改异步生成档案。首次拉取心情也可能触发 1 次 LLM（二次拉取因幂等不触发）。 |

## 9. 后期开发指引

- **加新修炼动作**：在 `HatchActionType` 加枚举（minutes + oneTime），`HatchActionServiceImpl` 自动适配（幂等/减时统一逻辑）；若 payload 需特殊校验，在 `recordHatchAction` 加分支。
- **加新破壳档案属性**：`ai_pet` 加字段（changeset）→ `PetEntity`/`PetVO`/`toVO` 同步 → `hatch()` 生成并回填。
- **改时间模型**：务必保持「adopt 设基线、动作只减时、动作不推迟」不变量；改公式时同步改 adopt + hatch-action + hatch 三处与对应测试。
- **接 AI 生图**：破壳接口先返回不含 `avatarUrl` 的 VO，生图异步完成回填 + 推送；或前端破壳动画后再拉取 `GET /pet/{id}`。
- **真机验证破壳→对话**：adopt → 修炼到点 → hatch → 用返回的 `deviceId` 调 `/ota/` 拿 `websocket.url/token` → WS 连 `xiaozhi-server:8000` 验证 ASR/LLM/TTS。
- **改 PetServiceImpl 依赖**：加/删 final 字段后，所有手工构造该类的测试（AdoptTest/HatchTest/TodayMoodTest）都要更新参数列表。
- **改心情判定/文案**：判定逻辑在 `MoodDecider`（纯函数，单测覆盖全），文案池在 `MoodLinePool`（egg/pet 两套）。加心情类型：`TodayMood` 加枚举 + 两池各加 list + `MoodDecider` 分支。改活跃度基线：`MoodDecider.baseline`，破壳后接 chat-history 时改这里。
