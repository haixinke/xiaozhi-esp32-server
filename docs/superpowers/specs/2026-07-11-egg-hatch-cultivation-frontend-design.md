# 蛋宝宝孵化修炼前端接入设计

- **日期**: 2026-07-11
- **子项目**: `main/egg-miniprogram/`
- **范围**: 前端接入 only，后端不动
- **状态**: 待评审

## 1. 背景与目标

后端 `manager-api` 已 landed 全套孵化修炼接口（`/pet/adopt`、`/pet/{id}/hatch-action`、`/pet/{id}/hatch-actions`、`/pet/{id}/hatch`、`/pet/{id}`、`/pet/list`、`/pet/update`，以及 todayMood 懒生成）。蛋宝宝小程序当前只接了 `/wechat/login`、`/pet/adopt`、`/pet/list` 三条；5 个修炼动作、破壳仪式、破壳后档案、stage 派生仍全部走 `utils/pet-store.js` 的本地 mock（`pet-store.js:91-92` 注释明示"修炼动作本次不接后端"）。

目标：把孵化修炼相关前端流程接入已 landed 的后端，让非演示场景下蛋宝宝的真实孵化/破壳/档案由后端为唯一事实源；同时保留展会演示模式。

## 2. 范围边界（已确认）

| 维度 | 决定 |
|---|---|
| 接入层级 | 纯前端接入，后端不改 |
| 收藏卡数据 | 身份字段（bazi/wuxing/zodiac/mbti/personality/gender/bloodType/avatarUrl）取后端 PetVO；装饰字段（serial / hatchQuality / style）前端生成 |
| 演示模式 | 保留双轨：`demoMode === true` 走本地 mock（保留现有逻辑），非 demo 走后端 |
| stage 模型 | 单轨（Model X），删除 `prepared` 阶段 |

后端 draft / 未验证项（收藏编号 serial 后端未返、昵称后端校验、OTA WebSocket 真机联调）**不在本次范围**。

## 3. 核心架构：pet-store 作为适配门面

新增 `miniprogram/utils/pet-api.js` 封装后端调用；`pet-store.js` 每个对外函数内部按 `demoMode` 分流：

- `demoMode === true` → 保留现有 mock 逻辑不动
- 否则 → 调 `pet-api.*`，用返回的 PetVO / HatchActionResultVO 更新本地缓存

页面层不感知分流（已全部通过 `petStore.*` 调用），demo 与真实共用同一调用面。

### 3.1 新增 `utils/pet-api.js`

```
adoptPet(inviteCode)                       // POST /pet/adopt
submitHatchAction(petId, type, payload)    // POST /pet/{id}/hatch-action
listHatchActions(petId)                    // GET /pet/{id}/hatch-actions
hatchPet(petId)                            // POST /pet/{id}/hatch
getPet(petId)                              // GET /pet/{id}
listPets()                                 // GET /pet/list
updateNickname(petId, nickname)            // PUT /pet/update
```

返回值即后端 VO，不做映射；映射在 `pet-store.js` 里做。底层复用 `utils/request.js`（已自动附 Bearer、401 静默重登重试）。

## 4. `pet-store.js` 改造点

### 4.1 `savePetFromVO(vo)` 扩展映射

当前已映射 `hatchStatus / acceleratedMinutes / deviceId / todayMood`。补全：

- 时间戳：`hatchStartTime`、`expectedHatchTime`、`hatchedAt`（ms）
- 破壳档案：`bazi / wuxing / zodiac / mbti / personality / personalityBrief / gender / bloodType / avatarUrl`
- 每日状态：`todayMoodSentence / todayMoodDate`

### 4.2 `getStage(pet, now)` 重新对齐后端时间戳（非 demo）

当前错误地基于 mock 的 `pet.hatchAt` / `pet.progress`。新逻辑（单轨 Model X，5 态）：

```
if hatchStatus === HATCHED        → 'hatched'
else (EGG):
  if now >= expectedHatchTime     → 'ready'      // 待破壳：倒计时归零
  elif remaining < 24h            → 'soon'       // 即将破壳
  elif acceleratedMinutes > 0     → 'hatching'   // 孵化中：已有动作
  else                            → 'waiting'    // 待激活：无动作
```

`prepared` 阶段删除。原因：Model X 下"进度满"意味着 `acceleratedMinutes >= 10080` → `expectedHatchTime = hatchStartTime`（clamp）→ `now >= expectedHatchTime` → 直接 `ready`。"进度满但时间未到"在单轨下不可能出现。

demo 模式仍走旧 `hatchAt/progress` 派生逻辑，不动。

### 4.3 5 个修炼动作函数分流

`updateNickname / completeCuddle / completeWish / completeLesson / saveDoodle` 各自：

- demo：保留现有 mock（`+progress` / `tasks` 标记）
- 非 demo：调 `pet-api.submitHatchAction(petId, TYPE, payload)`
  - type 映射：`NICKNAME / CUDDLE / WISH / LESSON / DOODLE`
  - 用返回 `HatchActionResultVO { addedMinutes, alreadyDone, readyToHatch, pet }` → `savePetFromVO(result.pet)` 更新本地缓存
  - 返回 `{ alreadyDone, readyToHatch }` 供页面 toast
  - 非 demo 下**不再本地加 progress**，以后端返回为准

### 4.4 `createCollectionCard()` 分流为真实破壳

- demo：保留本地 mock 生成
- 非 demo：调 `pet-api.hatchPet(petId)` → 返回完整 PetVO（含身份字段）→ `buildCollectionCard(vo)` 拼装：
  - 身份字段：取自 vo（prototype / nickname / mbti / gender / bloodType / bazi / wuxing / zodiac / personality / personalityBrief / avatarUrl / hatchedAt）
  - 装饰字段（前端生成）：
    - `serial`：`EGG-{PROTO}-{YYYYMMDD}-{id 后 6 位}`（PROTO = `KOI`/`RABBIT`，日期取 `hatchedAt`）
    - `hatchQuality`：`acceleratedMinutes / 10080 >= 0.8 ? '完整孵化' : '轻量孵化'`
    - `style`：保留现有派生
  - 写入 `pet.collectionCard` → 跳 `collection-card` 页

### 4.5 `getHatchActionState(pet)` 新增（hatch-guide 用）

- demo：从 `pet.tasks.*` 读
- 非 demo：从 `pet._hatchActions`（由 `listHatchActions` 缓存）派生当日完成态

### 4.6 `getDailyStatus()` 不动

已优先用后端 `todayMood`；demo fallback 走本地启发式。保留现状。

## 5. 页面改动（最小）

| 页面 | 改动 |
|---|---|
| `pages/hatch/hatch.js` | `onReveal()` 调 `petStore.createCollectionCard()`（已分流）|
| `pages/hatch-guide/hatch-guide.js` | `onShow` 非 demo 时先 `listHatchActions` 缓存到 `pet._hatchActions`；任务完成态改读 `petStore.getHatchActionState(pet)` |
| `pages/nickname / wish / lesson / doodle` | 提交后处理 `{alreadyDone, readyToHatch}`：`alreadyDone` → toast "今天已经做过了"；非 demo 下不再本地加 progress |
| `pages/home/home.js` | cuddle 长按分流；`getStage` 已对齐时间戳；`onShow` 非 demo 可用 `getPet(petId)` 单宠刷新（替代仅 `/pet/list`）|
| `pages/pet-detail/pet-detail.js` | 读 `pet.collectionCard`（现含后端身份字段），无需改 |

## 6. 数据流（非 demo 真实破壳）

```
adopt(code) → POST /pet/adopt → PetVO(EGG) → savePetFromVO → home
  ↓ 每日做动作
submitHatchAction(type) → POST /pet/{id}/hatch-action → {pet, readyToHatch}
  ↓ readyToHatch === true
home actionLabel → "破壳" → /pages/hatch → onReveal
  → petStore.createCollectionCard() → POST /pet/{id}/hatch → PetVO(HATCHED, 含身份)
  → buildCollectionCard(vo) → collection-card 页
  → 之后 GET /pet/{id} 取 todayMood → pet-detail 档案
```

## 7. 错误处理

- 后端 `10209 PET_ALREADY_HATCHED` → 静默刷新为 hatched 态
- `10214 PET_HATCH_TIME_NOT_REACHED` → toast "还没到破壳时间"（hatch 页 guard 已有）
- `alreadyDone === true` → toast "今天已经做过了"，不重复减时
- 网络/401 → `request.js` 已有 silent re-login 重试，沿用
- LLM 性格/mood 生成失败 → 后端已有 `MoodLinePool` fallback，前端不感知

## 8. 测试策略

`pet-store.test.js` 已有单测。新增：

- `getStage`（非 demo）5 个分支单测：waiting / hatching / soon / ready / hatched
- `buildCollectionCard(vo)` 装饰字段生成单测（serial 格式、hatchQuality 阈值）
- demo 分流单测：demo 走 mock、非 demo 走 pet-api（mock request）
- `pet-api.js` 端点路径 + payload 单测（mock `request.js`）

## 9. 文档同步更新（本次一并完成）

单轨化已确认，PRD §5.3 的"双轨孵化"描述与实现不符，本次一并修订：

- `docs/蛋宝宝小程序MVP_PRD.md` §5.3：从"双轨孵化机制"改写为单轨任务减时（Model X）
- §6.3 蛋状态表：删除"孵化中 · 已准备"行及其示例文案、"进度 100% 但未到破壳日"规则块
- §6.7 主界面按钮表：删除"孵化进度 100% 但未到破壳日 | 等待破壳日"行
- `main/egg-miniprogram/CLAUDE.md` stage 映射表：5 态（删 `prepared`）
- `docs/egg-pet-identity-and-hatch-api.md`：原"偏离 PRD §5.3"说明改为"与 PRD §5.3 一致（单轨任务减时）"

## 10. 风险

- **低**：`pet-store.js` 分流逻辑增加复杂度，但有 demo/非 demo 单测兜底
- **低**：serial 前端生成可能与未来后端 serial 字段冲突；后端将来若返 serial，`buildCollectionCard` 优先用后端值即可
- **中**：`getStage` 改动影响 home 分发逻辑，需回归 6 态→5 态后所有路由正确
- **不在范围**：OTA WebSocket 真机语音对话链路（API 文档标记未验证），本次不碰
