# 蛋宝宝宠物身份模型与 adopt/hatch 接口草案

> 状态：设计草案，未实现。落地前需与产品确认孵化时长规则、动作明细表设计。
> 关联：[../CLAUDE.md](../CLAUDE.md) "设备/宠物身份模型"小节；PRD 5.2 孵化机制、5.4 破壳档案。

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
- `ai_pet.device_id` 允许 NULL（MySQL 唯一索引对多 NULL 不冲突），**无需改 schema**。
- device id 用 `ai_device.id`（ASSIGN_UUID），`macAddress` 存 `egg-{uuid前8位}`。

## 3. 数据模型变更

- `ai_pet`：**无 schema 变更**。`device_id` 由 NOT NULL 改为可空语义（见 `202607101030.sql` 已补孵化字段；`device_id` 列定义本身需确认是否 NOT NULL，若是的需单独 changeset 放宽）。
- 新增错误码（`ErrorCode`，102xx 段）：

| 常量 | 值 | 含义 |
|---|---|---|
| `PET_NOT_HATCHABLE` | 10209 | 蛋未破壳，不能进入聊天/破壳前置未满足 |
| `PET_ALREADY_HATCHED` | 10210 | 该蛋已破壳，重复破壳 |
| `PET_HATCH_TIME_NOT_REACHED` | 10211 | 未到预计破壳时间 |
| `PET_HATCH_NOT_STARTED` | 10212 | 尚未完成任何修炼任务，倒计时未启动 |

## 4. 端点契约

### 4.1 领养蛋 `POST /pet/adopt`

- 鉴权：normal（`SecurityUser.getUserId()`）
- 入参 `PetAdoptDTO`：

```java
@Data
@Schema(description = "领养蛋请求")
public class PetAdoptDTO {
    @Schema(description = "原型(锦鲤/玉兔)；不传则随机或由激活码决定")
    private String prototype;

    @Schema(description = "激活码/邀请码（可选，用于核销与原型来源")
    private String inviteCode;
}
```

- 行为：
  1. 校验激活码（若传）—— 复用 invite 模块的核销逻辑（`InviteService.consume`），核销失败按其错误返回。
  2. 确定原型：`inviteCode` 命中 KOI→锦鲤，否则按 `prototype` 或随机。
  3. 建 `ai_pet`：`userId`、`nickname` 空、`prototype`、`hatchStatus=EGG`、`hatchStartTime=null`、`expectedHatchTime=null`、`acceleratedMinutes=0`、`deviceId=null`、不生成 mbti/personality/avatar。
  4. 返回 `PetVO`（stage 对应前端 `waiting`）。
- 错误：激活码相关沿用 invite 错误码；`PET_ALREADY_EXISTS` 不再适用（多宠）。

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

1. **孵化动作明细表 `ai_pet_hatch_action`**（未建）：许愿池/早教班/摸一摸/涂鸦的"每日一次 + payload + 减少时长"需此表；首个动作写入 `hatchStartTime` 与 `expectedHatchTime = now + 7d`，后续动作累加 `acceleratedMinutes` 并重算 `expectedHatchTime`。adopt/hatch 依赖这套动作端点先落，否则 `hatch()` 会因 `PET_HATCH_NOT_STARTED` 无法破壳。建议先出该表与动作端点草案。
2. **OTA 对虚拟设备的行为验证**：现有 `/ota/` 按 mac 查 `ai_device` 返回 `websocket`。需验证破壳时建的虚拟设备（`agentId` 已绑、`userId` 已写）能直接返回 `websocket.url/token`，无需走 `/device/bind` 验证码流程。若 OTA 对 board 类型或"未绑 agent"有特殊分支，需适配。
3. **agent 默认配置**：`AgentCreateDTO` 需补齐该蛋的默认音色（TTS timbre）、LLM model、记忆开关等；落地时确认 `AgentService.createAgent` 的必填项。
4. **AI 生图（avatarUrl）**：破壳档案头像由提示词生成（PRD 5.4.1）。同步生图会拖长破壳响应；建议破壳接口先返回不含 `avatarUrl` 的 `PetVO`，生图异步完成后回填并推送，或前端破壳动画后再拉取。需产品确认。
5. **激活码与邀请码关系**：PRD 5.1 邀请码（用户 5 个、企业后台建）与 adopt 的 `inviteCode` 入参是否同一套，需与 invite 模块对齐。

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
