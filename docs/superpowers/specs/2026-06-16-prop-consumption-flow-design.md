# 产品方案：小程序「重塑命运」道具消费流程

- 日期：2026-06-16
- 子项目：`main/miniprogram/`（完美女友微信小程序）+ `main/manager-api/`（后端）
- 关联方案：
  - 背包浏览/购买：[`2026-06-15-backpack-items-page-design.md`](./2026-06-15-backpack-items-page-design.md)
  - 后端订阅/道具/支付：[`main/manager-api/docs/companion-subscription-items-payment.md`](../../../main/manager-api/docs/companion-subscription-items-payment.md)
- 状态：设计已确认，待评审

---

## 1. 背景与目标

「我的背包」已落地道具的**浏览 + 购买**链路。用户在「重塑命运」分类下可购买三张券：

| `sku_code` | 名称 | 价格 | 用途 |
|---|---|---|---|
| `occupation_change` | 职业变更券 | ¥299 | 变更女友职业 |
| `soul_quirk_change` | 小任性变更券 | ¥99 | 变更女友性格（灵魂特质 + 小任性） |
| `voice_change` | 声音变更 | ¥99 | 变更女友声音 |

但目前**没有任何页面承载「买完之后怎么用」**。新人引导（`destiny` / `soul-resonance`）只在首次创建伴侣时设定这些属性，老用户买完券无处消费。

**本方案的目标**：设计并实现老用户消费这三张券的完整交互流程——从入口、选择新值、二次确认、扣券生效，到无券拦截与成功收尾。

**非目标**：
- 道具购买流程（已在背包页实现）。
- 亲密度礼物 / 服装 / 声音克隆额度 的消费（各自有独立现场：聊天送礼、换装、克隆流程）。
- 克隆音色的**订阅门禁**（见 §9.4，当前 `FeatureCode` 无 `CUSTOM_VOICE`，本期不做）。

---

## 2. 已确认决策

| # | 项 | 决策 |
|---|---|---|
| 1 | 入口心智 | **C 混合**：背包「使用」+ 伴侣资料页「更换」两条入口，共用同一套更换流程 |
| 2 | 换性格范围 | **B**：一张 `soul_quirk_change` 券可在一次编辑里**同时**变更灵魂特质 + 小任性。**`sku_code` 保持 `soul_quirk_change` 不改名**，仅升级功能含义（不改 DB、不改 `ConsumeBizType`） |
| 3 | 换声音计费 | **A 对称**：换声音也扣 1 张 `voice_change` 券；三张「重塑命运」券行为完全对称 |
| 4 | 无券处理 | **C**：让用户先选完，在「确认更换」那一步才校验；无券 → 弹窗提示 +「去背包获取」（不在更换页内嵌购买） |
| 5 | 反悔缓冲 | **C**：职业/性格用二次确认面板；换声音在确认面板内额外保留试听入口 |
| 6 | 资料页范围 | **A**：「我的女友」资料页只承载三张券门禁项（职业/性格/声音），不扩其他免费可改字段 |

---

## 3. 信息架构与入口

```
入口①  我的背包 ── 点持有的券卡片（CTA=「使用」）──┐
入口②  「我」/ 聊天头像 ── 我的女友资料页 ── 点「更换 ›」──┤
                                                        ▼
                                            更换页（职业 / 性格 / 声音 三选一）
                                                        │
                                          选新值 → 点「确认更换」
                                                        ▼
                                            二次确认面板（扣券预览）
                                          ├─ 有券：「确认重塑」→ 扣券生效
                                          └─ 无券：拦截弹窗 → 去背包获取
                                                        ▼
                                            重塑成功态 → 返回入口
```

### 3.1 入口① 背包「使用」

`pages/backpack` 现有 `consumable_change` 卡片：`remainCount===0` 时 CTA 为「购买」（不变）。新增：**`remainCount>0` 时主 CTA 改为「使用」**，点「使用」`navigateTo` 到对应更换页。

- 为保留「囤积」（决策见背包方案 §2.2），`remainCount>0` 时卡片同时提供「加购」次级入口（badge 区小链接或长按），购买仍走背包既有的 purchase-sheet。
- 「使用」与「加购」互不冲突：消费是主路径，购买是次路径。

### 3.2 入口② 我的女友资料页（新建）

新页面 `pages/companion/profile`，从「我」页面（`settings`）的伴侣卡片或聊天页头像进入。页面内容（决策 6，仅 3 项）：

```
导航栏（‹  我的女友）
├─ 头像 / 名字 / 亲密度（展示，不可编辑）
├─ 职业        当前值            更换 ›
├─ 性格        灵魂特质 / 小任性  更换 ›
└─ 声音        当前值            更换 ›
（底部小字：每项更换消耗 1 张对应券）
```

- 「更换 ›」`navigateTo` 到对应更换页。
- 当前值来自 `/companion/detail/{deviceId}`。

---

## 4. 更换页规格（三个）

三个更换页**结构独立**（选择器形态不同），但共享：顶部「当前值」展示、底部固定的「确认更换」条、以及同一套二次确认面板组件。选择器逻辑**复用新人引导**已实现的交互。

### 4.1 换职业 `pages/companion/change-occupation`

- 复用 `destiny.js` 的 `OCCUPATIONS` 九宫格选择器（单选，点选高亮）。
- 顶部展示「当前 · {当前职业}」。
- 底部确认条：「将消耗 1 张换职业券（剩 N 张）」+「确认更换」。

### 4.2 换性格 `pages/companion/change-soul`

- 复用 `soul-resonance.js` 的选择器：
  - **灵魂特质**：六宫格，最多选 2 条（复用 `config/companion-codes` 的 `SOUL_TRAITS`）。
  - **小任性**：胶囊行，单选 1 条（复用 `QUIRKS`）。
- 顶部展示「当前 · {灵魂特质} ／ {小任性}」。
- 底部确认条：「将消耗 1 张换性格券（剩 N 张）」+「确认更换」。
- **一次提交同时写 `soulTraits` 与 `soulQuirk`**，后端只要其中之一变化即扣 1 张 `soul_quirk_change`（见 §8）。

### 4.3 换声音 `pages/companion/change-voice`（新建）

- **不复用**新人引导的 4 音色小弹窗，**新建独立页**，采用**可滚动列表**结构以容纳更多音色。
- 每条音色：▶ 试听按钮 + 名称 + 标签（`默认` / `克隆` / `需订阅`）+ 选中 ✓。
- 试听逻辑复用 `destiny.js` 的 `InnerAudioContext` 试听（播放/停止/单实例）。
- 顶部展示「当前 · {当前声音}」+ 引导「点 ▶ 试听，选择她的新音色」。
- 底部确认条：「将消耗 1 张换声音券（剩 N 张）」+「确认更换」。
- **音色目录来源**：抽取到共享配置 `config/voice-catalog.js`（默认音色 + 扩展位），供新人引导与换声音页共用，便于后续上新。用户克隆音色来自 `/voiceclone` 用户列表。

---

## 5. 二次确认面板（共享组件）

新组件 `components/reshape-confirm`（底部弹层），由各更换页在点「确认更换」时唤起。

```
底部面板
├─ 标题：为她换上新{职业/性格/声音}
├─ 副标题：确认后将消耗一张对应券，立即生效
├─ 变更卡片：由 {当前} → 变为 {新值}
├─ [仅换声音] ▶ 再试听一下「{新声音}」   ← 搬 destiny.js 试听逻辑
├─ 消耗行：将消耗 1 张 {券名}（剩余 N 张）
└─ 「确认重塑」按钮
```

- 面板通过 props 接收 `{title, from, to, voucherName, remainCount, listenable, audioUrl}`，自身不发请求；点「确认重塑」触发父页面提交。
- 动效复用背包 `purchase-sheet`：`translateY` + `cubic-bezier(0.16, 1, 0.3, 1)`，遮罩点击关闭。

---

## 6. 无券拦截态

点「确认重塑」前/时，前端以**本地已加载的 inventory** 校验该券 `remainCount`：

- `remainCount ≥ 1` → 正常调 `/companion/update`。
- `remainCount === 0` → **拦截**，弹窗：
  ```
  🎟️  还没有{券名}
  {券用途一句话}
  需要 · 1 张{券名} · ¥{价}
  [ 去背包获取 ]   [ 再想想 ]
  ```
  - 「去背包获取」→ `navigateTo` 背包页，可带 `?focus={skuCode}` 带出对应卡片购买面板。
  - 「再想想」→ 关闭弹窗，留在更换页（已选值保留）。

> 说明：库存以本地缓存为准做前置拦截仅为体验；**真实扣减以服务端为准**（后端 `consume` 在事务内 `FOR UPDATE` 校验，不足抛 `ITEM_INSUFFICIENT` 10321）。若本地与服务端不一致（如多端消费），update 仍会返回 10321，前端按 §9 错误处理降级到拦截弹窗。

---

## 7. 重塑成功态

`/companion/update` 成功返回后：

```
✨  命运已重塑
她的{职业/性格/声音}已更新，下一次对话将以全新姿态陪你
┌─ {属性}    {当前} → {新值}
└─ 消耗      1 张{券名}
            [ 完成 ]
```

- 「完成」→ 返回来源入口（背包或资料页），并触发库存刷新（`/item/inventory`）。
- 若返回入口是背包，`onShow` 已有 `refreshInventory()`，券徽标自动 -1。

---

## 8. 数据契约

### 8.1 接口（**前端无新接口**，全部复用现有）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/companion/detail/{deviceId}` | 资料页 / 更换页取当前值 |
| GET | `/item/inventory` | 前置校验券余量、成功后刷新 |
| POST | `/companion/update` | **消费触发点**：提交新值，服务端扣券 |

均经 `utils/request.js`，自动附加 `Bearer ${token}`，上下文 `/xiaozhi`，信封 `{code, msg, data}`，`code===0` 成功。

### 8.2 消费提交（POST /companion/update）

复用现有 `CompanionUpdateDTO`，按更换类型只传变化字段 + 必填 `deviceId`：

```
换职业： { deviceId, occupation: "music" }
换性格： { deviceId, soulTraits: "zhi,xing", soulQuirk: "dushe" }   // 任一或两者
换声音： { deviceId, voice: "TTS_HSDSTTS_V2_0022" }
```

服务端在事务内检测字段变化并扣券（见 §9）。

### 8.3 错误码

| code | 含义 | 前端处理 |
|---|---|---|
| `10321` | `ITEM_INSUFFICIENT`（无券） | 转为无券拦截弹窗（§6） |
| 其他非 0 | 业务异常 | toast「更换失败，请重试」，保留选择 |

---

## 9. 后端改动（manager-api）

> 关键：**无 DB 迁移、无 SKU 改名、无 `ConsumeBizType` 变更**。所有改动集中在 `CompanionServiceImpl.update`。

### 9.1 现状 vs 提议

`CompanionServiceImpl.update`（`main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`，约 line 84–170）现状：

| 字段变化 | 现状 | 提议 |
|---|---|---|
| `occupation` 变 | ✅ 已扣 `occupation_change` | 不变 |
| `soulTraits` 变 | ❌ 直接写，不扣券 | **新增：扣 `soul_quirk_change`** |
| `soulQuirk` 变 | ✅ 已扣 `soul_quirk_change` | 不变 |
| 二者其一变 | — | 统一为「**soulTraits 或 soulQuirk 任一变化 → 扣 1 张** `soul_quirk_change`」（决策 2） |
| `voice` 变 | ❌ 直接 `setVoice`，不扣券 | **新增：扣 `voice_change`**（决策 3） |

### 9.2 改动点（update 方法内，扣券区）

现有扣券区（写库之前）改造为：

```java
boolean occupationChanged  = dto.getOccupation() != null && !dto.getOccupation().equals(entity.getOccupation());
boolean soulTraitsChanged  = dto.getSoulTraits() != null && !dto.getSoulTraits().equals(entity.getSoulTraits());
boolean soulQuirkChanged   = dto.getSoulQuirk()  != null && !dto.getSoulQuirk().equals(entity.getSoulQuirk());
boolean soulChanged        = soulTraitsChanged || soulQuirkChanged;   // 决策2：任一变化扣1张
boolean voiceChanged       = dto.getVoice() != null && !dto.getVoice().equals(entity.getVoice());

if (occupationChanged) {
    itemService.consume(entity.getUserId(), "occupation_change", 1,
            ConsumeBizType.OCCUPATION_CHANGE, entity.getDeviceId());
}
if (soulChanged) {
    itemService.consume(entity.getUserId(), "soul_quirk_change", 1,
            ConsumeBizType.SOUL_QUIRK_CHANGE, entity.getDeviceId());
}
if (voiceChanged) {
    itemService.consume(entity.getUserId(), "voice_change", 1,
            ConsumeBizType.VOICE_CHANGE, entity.getDeviceId());   // VOICE_CHANGE 为新增常量，见 §9.3 / §13
}
```

> 扣券仍在写库之前集中完成（保持现状的事务语义：后续任何异常让扣减一起回滚）。`itemService.consume` 内部已 `FOR UPDATE` 校验并抛 `ITEM_INSUFFICIENT`。

写库区补一行：`soulTraits` 变化时 `entity.setSoulTraits(dto.getSoulTraits())`（现状对 `soulTraits` 是无条件覆盖，需与 `soulChanged` 判定保持一致；若保持「传了就写」也成立，但建议改为有变化才写，避免空提交误触发）。

### 9.3 `voice_change` 的 bizType

`ConsumeBizType` 现有常量：`OCCUPATION_CHANGE` / `SOUL_QUIRK_CHANGE` / `OUTFIT_EQUIP` / `VOICE_CLONE` / `INTIMACY_GIFT`。**没有「换声音」专用 bizType**。两种处理：

- **方案 a（推荐）**：新增 `ConsumeBizType.VOICE_CHANGE = "voice_change"`（§9.2 代码按此写），语义清晰，`item_consume_log.biz_type` 可区分「换声音」与「声音克隆额度消耗（VOICE_CLONE）」。
- 方案 b：复用 `VOICE_CLONE`，但会与「克隆额度消耗」混淆流水，不利于对账。

**建议方案 a**：新增一个常量字符串，无 DB 变更（`biz_type` 是 `VARCHAR`）。

### 9.4 克隆音色订阅门禁（**本期不做**）

决策 3 提到「克隆音色另需订阅门禁」。核查现状：

- `FeatureCode`（`subscription/enums/FeatureCode.java`）**没有** `CUSTOM_VOICE` 常量（仅有 `LONG_TERM_MEMORY` / `VOICE_INPUT` / `SUPERPOWER` / `SOCIAL_MOMENTS`）。
- `CompanionServiceImpl` **未注入** `SubscriptionService`。
- 克隆音色当前由「克隆动作消耗 `voice_clone_quota` 额度」（`VoiceCloneController` line 191）间接控制，并非订阅门禁。

因此本期**不实现**克隆音色的订阅门禁；换到任何声音（默认/克隆）统一只扣 `voice_change`。订阅门禁作为**后续项**：待产品确认要用订阅卡克隆音色时，再新增 `FeatureCode.CUSTOM_VOICE` + 在 update 注入 `SubscriptionService` 并在「新 voice 为克隆音色」时 `requireFeature`。

### 9.5 重塑生效需同步 agent（**建议补，待评审**）

`update` 当前**不**重新同步 agent 系统提示词与 TTS 音色。重塑职业/性格/声音后，agent 的人设 prompt 与发音应随之更新。建议在 `update` 写库成功后调用既有的 `syncPromptToAgent(agentId, companionId)` 并更新 `agent.ttsVoiceId`（参照 `setup` 中的写法，约 line 261）。

> 评审时确认：是 update 内自动同步（推荐，原子），还是由前端再调 `/companion/sync-prompt`。

---

## 10. 前端改动（miniprogram）

### 10.1 新增

```
pages/companion/
├── profile/                 # 我的女友资料页（入口②，仅3项）
├── change-occupation/       # 换职业（复用九宫格）
├── change-soul/             # 换性格（复用灵魂特质+小任性）
└── change-voice/            # 换声音（新建列表页）
components/
└── reshape-confirm/         # 二次确认底部面板（共享）
config/
└── voice-catalog.js         # 音色目录（默认+扩展位，换声音页与新人引导共用）
```

每个新页面遵循暗色模式规范（`miniprogram/CLAUDE.md`）：`data.darkMode = getTheme()`、`onShow` `applyTheme`、根容器 `{{darkMode?'dark':''}}`。

### 10.2 修改

```
app.json                                  # pages 注册 4 个新页
pages/backpack/backpack.{js,wxml} + logic.js
                                          # consumable_change 且 remainCount>0：主 CTA「使用」navigateTo 更换页；
                                          # 保留「加购」次级入口走既有 purchase-sheet
pages/settings/settings.js                # 伴侣卡片/头像 → navigateTo profile（入口②）
```

### 10.3 复用（不改动）

- `destiny.js` 的 `OCCUPATIONS`、试听逻辑 → 搬到 change-occupation / change-voice。
- `soul-resonance.js` + `config/companion-codes` 的 `SOUL_TRAITS` / `QUIRKS` → 搬到 change-soul。
- 背包 `purchase-sheet` 动效样式。

---

## 11. 边界与错误处理

| 场景 | 处理 |
|---|---|
| 未选新值点「确认更换」 | 确认按钮置灰（`disabled`），或 toast「请先选择新{项}」 |
| 新值与当前值相同 | 后端不视为变化、不扣券；前端可拦截「未发生变化」直接关闭 |
| 本地余量≥1 但服务端 10321（多端并发消费） | update 返回 10321 → 转为无券拦截弹窗，刷新 inventory |
| update 网络失败 | toast「更换失败，请重试」，保留选择，不扣券（事务回滚） |
| 换声音试听失败 | 仅停止播放动画，不阻塞选择/确认 |
| 克隆音色作为新值 | 本期照常扣 `voice_change`；订阅门禁见 §9.4（本期不卡） |

---

## 12. 验收标准

- [ ] 入口①：背包「重塑命运」三张券在 `remainCount>0` 时显示「使用」，点击直达对应更换页。
- [ ] 入口②：从「我」进入「我的女友」资料页，正确展示当前职业/性格/声音，点「更换」进对应更换页。
- [ ] 换职业：九宫格选新值 → 确认面板「由 X → 变为 Y」+ 扣券提示 → 确认重塑 → 成功态 → 返回，职业券 -1。
- [ ] 换性格：改灵魂特质和/或小任性 → 确认 → 扣 1 张 `soul_quirk_change` → 成功态 → 返回。
- [ ] 换声音：列表试听 → 选新音色 → 确认面板含「再试听」→ 确认重塑 → 扣 `voice_change` → 成功态。
- [ ] 无券：`remainCount===0` 时点确认 → 弹窗「还没有{券}」+ 价签 +「去背包获取」/「再想想」；「去获取」跳背包对应卡片。
- [ ] 服务端 10321 → 前端降级为无券拦截弹窗（不卡死、不重复提交）。
- [ ] 后端：改 occupation/soulTraits/soulQuirk/voice 任一 → 对应券扣减、`item_consume_log` 落库；无券抛 10321；扣减在事务内、与写库同进退。
- [ ] 重塑生效：agent 系统提示词与 TTS 音色随之更新（§9.5，按评审结论）。
- [ ] 暗色模式与现有页面一致；不引入新主色（复用 Ethereal Companion token）。

---

## 13. 待评审确认项

1. **背包卡片双 CTA**：`remainCount>0` 时主「使用」+ 次「加购」的呈现形式（badge 区小链接 or 长按），评审时定。
2. **`voice_change` bizType**：新增 `ConsumeBizType.VOICE_CHANGE`（推荐 a）还是复用 `VOICE_CLONE`（b）。
3. **agent 同步时机**：update 内自动同步（推荐）还是前端再调 `/companion/sync-prompt`。
4. **音色目录扩展来源**：`config/voice-catalog.js` 静态扩展 vs 后端 timbre 接口下发，评审时定。
5. **克隆音色订阅门禁**：确认本期不做（§9.4），后续单独排期。
