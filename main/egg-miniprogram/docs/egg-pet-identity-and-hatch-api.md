# 蛋宝宝宠物身份模型与 adopt/hatch 接口草案

> 状态：设计草案，未实现。落地前需与产品确认孵化时长规则、动作明细表设计。
> 关联：[../CLAUDE.md](../CLAUDE.md) "设备/宠物身份模型"小节；PRD 5.2 孵化机制、5.4 破壳档案。

> ⚠ **偏离说明（重要）**：本实现按**草案模型（任务减时）**落地——完成修炼任务会**减少孵化时长**（累加 `accelerated_minutes`，下推 `expectedHatchTime`）。这与 PRD §5.3 的"双轨孵化（进度不减时）"模型**不一致**，系产品明确确认选择按草案模型走。详见第 10 节。adopt/hatch 破壳接口仍为草案未实现。

> ✅ **已落地**：孵化修炼任务（hatch-action）端点 + `ai_pet_hatch_action` 表已落地，详见第 10 节。

## 1. 背景与目标

笨笨女友小程序"一人一伴侣"，用 `openid` 当 `ai_device` 的 mac 成立。蛋宝宝是"一人多宠"，`openid` 不能当 device id：

- 所有蛋共用一条 `ai_device` → 一个 `agentId` → 性格/音色无法区分；
- `ai_pet.device_id` 唯一索引（`uk_ai_pet_device_id`）会阻止第二只蛋。

xiaozhi 模型里 `ai_device` 是聊天通道单位、`agentId` 1:1 挂在 device 上。因此"聊天身份"下沉到宠物级：**一只蛋 = 一个虚拟设备 = 一个 agent**。同时把现有 `PetServiceImpl.birth()` 的"创建即出生"演示逻辑拆成 **领养（adopt）** 与 **破壳（hatch）** 两段，匹配 PRD"档案在破壳时生成"。

## 2. 身份模型

```
微信用户(openid) ──1:N── 蛋宝宝(ai_pet) ──1:1── 虚拟设备(ai_device) ──1:1── agent(ai_agent)
```

- `openid` 仅换 `token/userId`，不进 device。
- 领养只建 `ai_pet`：`deviceId=null`、`agentId=null`、`hatchStatus=EGG`、不生成档案。
- 破壳才建 `ai_device` + `ai_agent`，回填 `ai_pet.deviceId`，生成档案。
- `ai_pet.device_id` 原为 NOT NULL（DDL 见 `202605071500.sql`），领养阶段需写 NULL，已由 changeset `202607101500` 放宽为可空。MySQL 唯一索引对多 NULL 不冲突，`uk_ai_pet_device_id` 保留，破壳后仍保证"一设备一宠物"。
- device id 用 `ai_device.id`（ASSIGN_UUID），`macAddress` 存 `egg-{uuid前8位}`。

## 3. 数据模型变更

- `ai_pet`：新增 changeset `202607101500.sql` 将 `device_id` 由 NOT NULL 放宽为可空（原 DDL 在 `202605071500.sql`，确为 NOT NULL）。孵化字段由 `202607101030.sql` 补齐，无需再加。
- 新增错误码（`ErrorCode`，102xx 段）：

| 常量 | 值 | 含义 | 状态 |
|---|---|---|---|
| `PET_ALREADY_HATCHED` | 10209 | 该蛋已破壳，不能再做修炼动作（hatch-action 守卫） | ✅ 已落地（见 §10） |
| `PET_NOT_HATCHABLE` | 10213 | 蛋未破壳，不能进入聊天（破壳前置未满足） | 草案，hatch 落地时实现 |
| `PET_HATCH_TIME_NOT_REACHED` | 10214 | 未到预计破壳时间 | 草案，hatch 落地时实现 |
| `PET_HATCH_NOT_STARTED` | 10215 | 尚未完成任何修炼任务，倒计时未启动 | 草案，hatch 落地时实现 |

> 注：原草案把 `PET_ALREADY_HATCHED` 编为 10210、`PET_NOT_HATCHABLE` 编为 10209，与 `COMPANION_NOT_FOUND=10210` 撞码。实际落地已用 10209=PET_ALREADY_HATCHED，后续 hatch 相关错误码改用 10213–10215。

## 4. 端点契约

### 4.1 领养蛋 `POST /pet/adopt`

- 鉴权：normal（`SecurityUser.getUserId()`）
- 入参 `PetAdoptDTO`：

```java
@Data
@Schema(description = "领养蛋请求")
public class PetAdoptDTO {

    @NotBlank(message = "邀请码不能为空")
    @Schema(description = "邀请码(必填,核销裂变邀请码;无效码将拒绝领养)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inviteCode;
}
```

- 行为：
  1. prototype 后端随机（锦鲤/玉兔），与 inviteCode 解耦——invite 码不编码原型。
  2. 先建 `ai_pet`：`userId`、`nickname` 空、`prototype`(随机)、`hatchStatus=EGG`、`hatchStartTime=null`、`expectedHatchTime=null`、`acceleratedMinutes=0`、`deviceId=null`、不生成 mbti/personality/avatar。
  3. 再核销 `inviteCode`（`InviteService.consume(code, userId)`，幂等）。核销失败（无效/过期/无剩余）抛异常 → 外层 `@Transactional(rollbackFor=Exception.class)` 回滚第 2 步的 insert，不产生孤儿蛋。
  4. 返回 `PetVO`（stage 对应前端 `waiting`）。
- 错误：邀请码相关沿用 invite 的 `RenException`；`PET_ALREADY_EXISTS` 不再适用（多宠）。

> 倒计时起点：PRD 规定从完成首个修炼任务起算。故 adopt 阶段 `hatchStartTime/expectedHatchTime` 均为空，由首个 hatch action 写入（见第 6 节依赖）。

### 4.2 破壳 `POST /pet/{id}/hatch`

- 鉴权：normal
- 路径参数：`petId`
- 前置校验：
  1. 宠物存在且 `userId` 归属当前用户（否则 `PET_NOT_FOUND` / `PET_NO_PERMISSION`）
  2. `hatchStatus == EGG`（否则 `PET_ALREADY_HATCHED`）
  3. `hatchStartTime != null`（否则 `PET_HATCH_NOT_STARTED`）
  4. `now >= expectedHatchTime`（否则 `PET_HATCH_TIME_NOT_REACHED`）
- 行为（事务内）：
  1. `now = LocalDateTime.now()`；`hatchedAt = now`；`birthDate = hatchedAt`（生日=破壳日）。
  2. 用 `PetBirthCalculator.calculate(hatchedAt)` 算 `bazi/wuxing/zodiac`。
  3. LLM 推 `mbti`、生成 `personality`（系统提示词，500 字）+ `personalityBrief`（20 字卡片语）。
  4. 随机 `gender`（MALE/FEMALE）、`bloodType`（A/B/O/AB）；`avatarUrl` 由 AI 生图（异步？见第 7 节）。
  5. 建 `ai_agent`：`AgentService.createAgent(AgentCreateDTO)`，注入 `personality` 作系统提示词、配置音色；得 `agentId`。
  6. 建 `ai_device`：`id=ASSIGN_UUID`、`userId`、`macAddress=egg-{id前8位}`、`board="wechat-egg-miniprogram"`、`alias=nickname`、`agentId`、`appVersion`。
  7. 回填 `ai_pet`：`deviceId`、`hatchStatus=HATCHED`、`hatchedAt`、`birthDate`、`mbti/personality/personalityBrief/zodiac/bazi/wuxing/gender/bloodType/avatarUrl`、`updater`。
  8. 返回 `PetVO`（含 `deviceId`，前端据此走 OTA 进 chat）。
- 错误：上表 10209–10212。

### 4.3 查询

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/pet/{id}` | normal | 按 petId 查，带归属校验，替换旧 `/pet/detail/{deviceId}` |
| GET | `/pet/list` | normal | 当前用户所有蛋（已有，保留） |
| PUT | `/pet/update` | normal | 改昵称（已有，保留） |

> 旧 `GET /pet/detail/{deviceId}` 与 `POST /pet/birth` 标记 `@Deprecated`，下线前保留以兼容存量演示数据。

## 5. Service 接口签名

```java
public interface PetService extends BaseService<PetEntity> {

    /** 领养蛋：建 ai_pet(EGG)，不建 device/agent/档案 */
    PetVO adopt(Long userId, PetAdoptDTO dto);

    /** 破壳：建 device+agent，回填档案，EGG→HATCHED */
    PetVO hatch(Long userId, String petId);

    /** 按 petId 查（带归属校验） */
    PetVO getById(Long userId, String petId);

    List<PetVO> listByUserId(Long userId);

    void updatePet(Long userId, String petId, String nickname);

    // 聊天历史/记忆/画像相关方法保留
    PageData<ChatHistoryVO> getChatHistoryByMacAddress(String macAddress, Map<String, Object> params);
    PageData<MemoryVO> getMemoryByDeviceId(String deviceId, Map<String, Object> params);
    UserProfileVO getUserProfileByDeviceId(String deviceId, Map<String, Object> params);

    /** @Deprecated 旧"创建即出生"演示逻辑，迁移后移除 */
    @Deprecated
    PetVO birth(String deviceId);

    /** @Deprecated 旧按 deviceId 查，迁移后用 getById */
    @Deprecated
    PetVO getByDeviceId(String deviceId);
}
```

`PetController` 对应新增 `adopt/hatch/{id}` 端点，鉴权用 `@RequiresPermissions("sys:role:normal")`，`userId` 取 `SecurityUser.getUserId()`。

## 6. 破壳流程伪码

```java
@Transactional
public PetVO hatch(Long userId, String petId) {
    PetEntity pet = petDao.selectById(petId);
    if (pet == null) throw new RenException(ErrorCode.PET_NOT_FOUND);
    if (!pet.getUserId().equals(userId)) throw new RenException(ErrorCode.PET_NO_PERMISSION);
    if (HATCHED.equals(pet.getHatchStatus())) throw new RenException(ErrorCode.PET_ALREADY_HATCHED);
    if (pet.getHatchStartTime() == null) throw new RenException(ErrorCode.PET_HATCH_NOT_STARTED);
    if (LocalDateTime.now().isBefore(toLdt(pet.getExpectedHatchTime())))
        throw new RenException(ErrorCode.PET_HATCH_TIME_NOT_REACHED);

    LocalDateTime hatchTime = LocalDateTime.now();
    var calc = PetBirthCalculator.calculate(hatchTime);
    String mbti = deriveMbti(calc);
    String personality = derivePersonality(mbti);
    String brief = derivePersonalityBrief(mbti);          // 20 字卡片语
    String gender = randomGender();
    String bloodType = randomBloodType();
    String avatarUrl = generateAvatar(pet.getPrototype()); // AI 生图，可能异步

    // 1. 建 agent（性格=系统提示词，音色配置）
    String agentId = agentService.createAgent(buildAgentCreateDTO(pet, personality, mbti));

    // 2. 建 device 并绑 agent
    DeviceEntity device = new DeviceEntity();
    device.setUserId(userId);
    device.setMacAddress("egg-" + left(device.getId(), 8)); // id 由 ASSIGN_UUID 生成
    device.setBoard("wechat-egg-miniprogram");
    device.setAlias(pet.getNickname());
    device.setAgentId(agentId);
    deviceDao.insert(device);

    // 3. 回填 ai_pet
    pet.setDeviceId(device.getId());
    pet.setHatchStatus("HATCHED");
    pet.setHatchedAt(toDate(hatchTime));
    pet.setBirthDate(toDate(hatchTime));
    pet.setBazi(calc.bazi()); pet.setWuxing(calc.wuxing()); pet.setZodiac(calc.zodiac());
    pet.setMbti(mbti); pet.setPersonality(personality); pet.setPersonalityBrief(brief);
    pet.setGender(gender); pet.setBloodType(bloodType); pet.setAvatarUrl(avatarUrl);
    pet.setUpdater(userId);
    petDao.updateById(pet);

    log.info("蛋破壳 userId={}, petId={}, deviceId={}, agentId={}", userId, petId, device.getId(), agentId);
    return toVO(pet);
}
```

## 7. 依赖与前置项

1. **孵化动作明细表 `ai_pet_hatch_action`**（✅ 已落地，changeset `202607101600` + 端点 `POST /pet/{id}/hatch-action`、`GET /pet/{id}/hatch-actions`）：许愿池/早教班/摸一摸/涂鸦/起昵称的"每日一次 / 一次性 + payload + 减少时长"已由此表承载；首个动作写入 `hatchStartTime` 与 `expectedHatchTime = now + 7d`，后续动作累加 `acceleratedMinutes` 并重算 `expectedHatchTime`。完整表结构、端点契约、5 动作奖励、减时公式、幂等规则、日界、错误码、payload 示例见第 10 节。adopt/hatch 破壳端点仍依赖此机制：未完成任何修炼任务时 `hatchStartTime=null`，`hatch()` 会因 `PET_HATCH_NOT_STARTED` 无法破壳。
2. **OTA 对虚拟设备的行为验证**：现有 `/ota/` 按 mac 查 `ai_device` 返回 `websocket`。需验证破壳时建的虚拟设备（`agentId` 已绑、`userId` 已写）能直接返回 `websocket.url/token`，无需走 `/device/bind` 验证码流程。若 OTA 对 board 类型或"未绑 agent"有特殊分支，需适配。
3. **agent 默认配置**：`AgentCreateDTO` 需补齐该蛋的默认音色（TTS timbre）、LLM model、记忆开关等；落地时确认 `AgentService.createAgent` 的必填项。
4. **AI 生图（avatarUrl）**：破壳档案头像由提示词生成（PRD 5.4.1）。同步生图会拖长破壳响应；建议破壳接口先返回不含 `avatarUrl` 的 `PetVO`，生图异步完成后回填并推送，或前端破壳动画后再拉取。需产品确认。
5. **激活码与邀请码关系**：已确认 adopt 的 `inviteCode` 即 invite 模块的裂变邀请码（`InviteService.consume`），**不携带原型信息**——prototype 由后端随机。PRD 5.1 邀请码（用户 5 个、企业后台建）与此同一套核销。

## 8. 迁移与兼容

- 旧 `POST /pet/birth` 保留并标 `@Deprecated`，内部改为调用 `adopt`（忽略 deviceId，按当前用户建蛋），避免存量调用方立即报错；下线前给前端切换期。
- 旧 `GET /pet/detail/{deviceId}` 标 `@Deprecated`，前端改用 `GET /pet/{id}`。
- 存量演示数据（已有 `ai_pet` 行 `birthDate=now`、`hatchStatus` 默认 `HATCHED`）：视为已破壳，前端按 `hatched` 渲染，不影响新流程。
- `ai_pet.device_id` 若当前 DDL 为 NOT NULL，需新增 changeset 放宽为可空（落地时检查 `202605071500.sql` 的列定义）。

## 9. 小程序侧改动要点

- `app.js` globalData：`virtualMAC=openid` → `activePetId/activeDeviceId`。
- `home.js`：从"单只蛋"改"蛋列表 + 当前蛋"（可分阶段：MVP 固定第一只）。
- `chat` 前置：校验 `activeDeviceId != null`（未破壳禁入）；OTA 用 `activeDeviceId` 当 `mac`/`Device-Id`。
- 切换活跃宠物：主动断开 WS 重连到新 device channel。
- 登录后流程：`/wechat/login` → `/pet/list` → 选 `activePetId`。

## 10. 孵化修炼任务（hatch-action）已落地实现

> 状态：✅ 已落地（changeset `202607101600` + `PetController` 新增端点）。本节为**据实记录**，其余 §1–§9 仍为草案。

> ⚠ **偏离 PRD §5.3**：本实现采用"任务减时"模型——每个修炼动作累加 `accelerated_minutes` 并下推 `expectedHatchTime`。与 PRD §5.3"双轨孵化（进度不减时）"不一致，系产品明确确认选择按草案模型走。

### 10.1 表结构 `ai_pet_hatch_action`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键 |
| `pet_id` | varchar | 关联 `ai_pet.id` |
| `action_type` | varchar | 动作类型枚举（见 10.3） |
| `payload` | varchar/json | 动作载荷（昵称/许愿值/课程值/涂鸦颜色等） |
| `action_date` | date | 动作所在"日"（Asia/Shanghai 日界，用于每日幂等） |
| `accelerated_minutes` | int | 该动作减少的孵化分钟数 |
| `creator` | bigint | 创建人（用户 id） |
| `create_date` | datetime | 创建时间 |

- 唯一索引 `uk_pet_action_date(pet_id, action_type, action_date)`：保证"每日一次"动作在同一天对同一只蛋、同一动作类型只能有一条；"一次性"动作则跨天也只允许一条（业务层校验，见 10.5 幂等规则）。
- **不改 `ai_pet` schema**：复用 `ai_pet` 现有的 `hatch_start_time`、`expected_hatch_time`、`accelerated_minutes` 字段承载减时状态。

### 10.2 端点契约

#### POST `/pet/{id}/hatch-action`

- 鉴权：`sys:role:normal`（`SecurityUser.getUserId()`），并校验宠物归属当前用户。
- 路径参数：`petId`
- 请求体 `HatchActionDTO`（`{ type, payload }`）：

```json
{ "type": "WISH", "payload": { "value": "希望长出彩色花纹" } }
```

- 响应 `HatchActionResultVO`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `addedMinutes` | int | 本次新增的加速分钟数（0 表示当日已完成/已完成，未加速） |
| `alreadyDone` | boolean | true 表示该动作本周期已完成（命中幂等，未重复减时） |
| `readyToHatch` | boolean | true 表示 `now >= expectedHatchTime`，已可破壳 |
| `pet` | PetVO | 更新后的宠物视图（含最新 `expectedHatchTime`/`acceleratedMinutes`） |

#### GET `/pet/{id}/hatch-actions`

- 鉴权：`sys:role:normal`，校验归属。
- 返回 `List<HatchActionVO>`：该蛋的动作明细列表（按 `create_date` 排序），供前端展示已完成动作与进度。

### 10.3 五个修炼动作与奖励

| `type` | 含义 | 减少时长 | 幂等类型 | payload | 前端页面 |
|---|---|---|---|---|---|
| `NICKNAME` | 起昵称 | 720 分（12h） | 一次性 | `{ "nickname": "小金" }` | `pages/nickname` |
| `CUDDLE` | 摸一摸 | 60 分（1h） | 每日 | `{ }` | `pages/home` 长按蛋壳 |
| `WISH` | 许愿池 | 60 分（1h） | 每日 | `{ "value": "许愿内容" }` | `pages/wish` |
| `LESSON` | 蛋蛋早教班 | 60 分（1h） | 每日 | `{ "value": "课程/选择值" }` | `pages/lesson` |
| `DOODLE` | 彩蛋涂鸦 | 720 分（12h） | 一次性 | `{ "color": "#FFD700", "colorName": "金色", "pattern": "波点" }` | `pages/doodle` |

> **doodle 不做 AI 生图**：涂鸦仅记录用户选择的颜色/图样 payload，不调用 AI 生图能力。

### 10.4 减时公式

- **首个动作**（该蛋首次提交 hatch-action）：写入 `hatchStartTime = now`、`expectedHatchTime = now + 7d`（7 天 = 10080 分钟），`acceleratedMinutes` 置为本动作的 `accelerated_minutes`。
- **后续动作**：`acceleratedMinutes += 本动作 accelerated_minutes`；重算 `expectedHatchTime = hatchStartTime + 7d - acceleratedMinutes`，并 clamp 至 `>= hatchStartTime`（不会减到早于倒计时起点）。
- **日界**：`action_date` 按 `Asia/Shanghai` 时区计算，跨日即视为新的一天，"每日一次"动作可再次提交。
- **进度派生**：后端不返 `progress%`，前端进度条由 `acceleratedMinutes / 10080` 派生（1 分钟 = 1/10080 进度）。

### 10.5 幂等规则

- **每日一次**（CUDDLE/WISH/LESSON）：同一 `(pet_id, action_type, action_date)` 命中唯一索引即视为当日已完成。重复提交返回 `alreadyDone=true, addedMinutes=0`，不重复减时。
- **一次性**（NICKNAME/DOODLE）：业务层校验该动作对该蛋是否已有记录；已有则返回 `alreadyDone=true, addedMinutes=0`。跨天也不允许第二次。

### 10.6 错误码

| 常量 | 值 | 含义 |
|---|---|---|
| `PET_ALREADY_HATCHED` | 10209 | 该蛋已破壳，不能再提交修炼任务 |

> 注：§3 草案表里的 `PET_NOT_HATCHABLE=10209 / PET_ALREADY_HATCHED=10210` 为草案规划值；落地实现中 `PET_ALREADY_HATCHED` 实际取 **10209**，与草案编号不同。其余 `PET_HATCH_TIME_NOT_REACHED`、`PET_HATCH_NOT_STARTED` 等仍为草案待落地（破壳接口未实现）。

### 10.7 payload 各类型示例

```json
// NICKNAME
{ "type": "NICKNAME", "payload": { "nickname": "小金" } }

// CUDDLE
{ "type": "CUDDLE", "payload": { } }

// WISH
{ "type": "WISH", "payload": { "value": "希望长出彩色花纹" } }

// LESSON
{ "type": "LESSON", "payload": { "value": "音乐启蒙" } }

// DOODLE
{ "type": "DOODLE", "payload": { "color": "#FFD700", "colorName": "金色", "pattern": "波点" } }
```

### 10.8 尚未实现（不要当作已落地）

- `POST /pet/{id}/hatch` 破壳端点：未实现，仍为草案（§4.2、§6）。
- `GET /pet/{id}` 单宠查询：未实现，仍为草案（§4.3）。
- AI 生图（avatarUrl）、每日心情（todayMood）：未实现，不在本批次落地范围。
- `PET_HATCH_TIME_NOT_REACHED`、`PET_HATCH_NOT_STARTED`、`PET_NOT_HATCHABLE` 等其余错误码：随破壳端点待落地。
