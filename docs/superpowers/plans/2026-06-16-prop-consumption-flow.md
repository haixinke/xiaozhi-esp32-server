# 重塑命运道具消费流程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让老用户在买完「重塑命运」三张券（职业/性格/声音）后，能消费它们变更女友的职业/性格/声音，并即时同步到在线 agent。

**Architecture:** 混合入口（背包「使用」+ 新建「我的女友」资料页「更换」）共用三套更换页 + 一个共享二次确认面板。后端把扣券决策抽成纯函数 `ReshapeVoucherRule`（单测），在 `CompanionServiceImpl.update`（已 `@Transactional`）里调用它扣券、写库、同步 agent 系统提示词与 TTS 音色；前端在重塑成功后置位 `needReconnectAfterReshape`，聊天页 `onShow` 断开 WS，下次召唤加载新属性。

**Tech Stack:** Java 21 / Spring Boot 3.4.3 / MyBatis-Plus / JUnit 5 + AssertJ（后端）；微信小程序 WXML/WXSS/JS + Node `assert`（前端纯逻辑）。

**Spec:** [`docs/superpowers/specs/2026-06-16-prop-consumption-flow-design.md`](../specs/2026-06-16-prop-consumption-flow-design.md)

---

## File Structure

### 后端（`main/manager-api`）

| 文件 | 动作 | 职责 |
|---|---|---|
| `.../item/enums/ConsumeBizType.java` | 修改 | 新增 `VOICE_CHANGE` 常量 |
| `.../device/service/DeviceService.java` | 修改 | 新增 `getAgentIdByDeviceId(deviceId)` |
| `.../device/service/impl/DeviceServiceImpl.java` | 修改 | 实现上述方法 |
| `.../companion/util/ReshapeVoucherRule.java` | 新增 | **纯函数**：根据 before/after 决定扣哪些券 |
| `.../companion/util/ReshapeVoucherRuleTest.java` | 新增 | 纯函数单测（JUnit+AssertJ，无 Spring） |
| `.../companion/service/impl/CompanionServiceImpl.java` | 修改 | `update` 改用 `ReshapeVoucherRule`，补 voice 扣券，写库后同步 agent |

### 前端（`main/miniprogram`）

| 文件 | 动作 | 职责 |
|---|---|---|
| `config/voice-catalog.js` | 新增 | 音色目录（默认 + 扩展位），换声音页与新人引导共用 |
| `pages/backpack/logic.js` | 修改 | `cardView`：consumable_change 且 remainCount>0 → CTA `use` |
| `pages/backpack/logic.test.js` | 新增 | Node `assert` 单测 |
| `pages/backpack/backpack.wxml` | 修改 | 卡片渲染 `use` CTA + 次「加购」 |
| `pages/backpack/backpack.js` | 修改 | `onCardTap` 处理 `use` → navigateTo 更换页 |
| `components/reshape-confirm/*` | 新增 | 共享二次确认底部面板（含可选试听） |
| `pages/companion/change-occupation/*` | 新增 | 换职业页（复用九宫格） |
| `pages/companion/change-soul/*` | 新增 | 换性格页（灵魂特质 + 小任性） |
| `pages/companion/change-voice/*` | 新增 | 换声音页（列表 + 试听） |
| `pages/companion/profile/*` | 新增 | 我的女友资料页（仅 3 项） |
| `pages/settings/settings.js` | 修改 | 伴侣卡片/头像 → navigateTo profile |
| `pages/index/index.js` | 修改 | `onShow` 处理 `needReconnectAfterReshape` |
| `app.json` | 修改 | 注册 4 个新页 |

---

## 后端

### Task 1: 新增 `VOICE_CHANGE` 业务类型 + 设备→agent 查询

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/item/enums/ConsumeBizType.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/device/service/DeviceService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/device/service/impl/DeviceServiceImpl.java`

- [ ] **Step 1: `ConsumeBizType` 加常量**

在 `main/manager-api/src/main/java/xiaozhi/modules/item/enums/ConsumeBizType.java` 的 `VOICE_CLONE` 行后追加：

```java
    /** 换声音（重塑命运：变更女友音色） */
    public static final String VOICE_CHANGE = "voice_change";
```

- [ ] **Step 2: `DeviceService` 接口加方法**

在 `main/manager-api/src/main/java/xiaozhi/modules/device/service/DeviceService.java` 接口末尾（最后一个方法签名后、`}` 前）追加：

```java
    /**
     * 根据设备ID查询其绑定的智能体ID
     *
     * @param deviceId 设备ID
     * @return agentId；设备不存在或未绑定则返回 null
     */
    String getAgentIdByDeviceId(String deviceId);
```

- [ ] **Step 3: `DeviceServiceImpl` 实现**

在 `main/manager-api/src/main/java/xiaozhi/modules/device/service/impl/DeviceServiceImpl.java` 类内末尾（最后一个 `}` 前）追加。该类继承 `ServiceImpl<DeviceDao, DeviceEntity>`，已有 `baseDao`：

```java
    @Override
    public String getAgentIdByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        DeviceEntity device = baseDao.selectById(deviceId);
        return device == null ? null : device.getAgentId();
    }
```

- [ ] **Step 4: 编译验证**

Run: `cd main/manager-api && mvn -q compile`
Expected: BUILD SUCCESS（无编译错误）。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/item/enums/ConsumeBizType.java \
        main/manager-api/src/main/java/xiaozhi/modules/device/service/DeviceService.java \
        main/manager-api/src/main/java/xiaozhi/modules/device/service/impl/DeviceServiceImpl.java
git commit -m "feat(item): 新增 VOICE_CHANGE 业务类型与按设备查 agent 方法"
```

---

### Task 2: 纯函数 `ReshapeVoucherRule` + 单测（TDD）

把「扣哪些券」的决策抽成纯函数，脱离 Spring/安全上下文/DB，便于单测。

**Files:**
- Test: `main/manager-api/src/test/java/xiaozhi/modules/companion/util/ReshapeVoucherRuleTest.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/companion/util/ReshapeVoucherRule.java`

- [ ] **Step 1: 写失败测试**

创建 `main/manager-api/src/test/java/xiaozhi/modules/companion/util/ReshapeVoucherRuleTest.java`：

```java
package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xiaozhi.modules.companion.entity.CompanionEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReshapeVoucherRule 扣券决策")
class ReshapeVoucherRuleTest {

    private CompanionEntity entity(String occupation, String soulTraits, String soulQuirk, String voice) {
        CompanionEntity e = new CompanionEntity();
        e.setOccupation(occupation);
        e.setSoulTraits(soulTraits);
        e.setSoulQuirk(soulQuirk);
        e.setVoice(voice);
        return e;
    }

    @Test
    @DisplayName("什么都不改 -> 不扣券")
    void noChange_noVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(entity("camera", "clingy", "jealous", "v1"), null);
        assertThat(skus).isEmpty();
    }

    @Test
    @DisplayName("改职业 -> 扣 occupation_change")
    void occupationChange_consumesOccupation() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after("music", null, null, null));
        assertThat(skus).containsExactly("occupation_change");
    }

    @Test
    @DisplayName("改灵魂特质（小任性不变）-> 扣 soul_quirk_change")
    void soulTraitsChange_consumesSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, "clingy,flirty", null, null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("改小任性 -> 扣 soul_quirk_change")
    void soulQuirkChange_consumesSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, "grumpyMorning", null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("同时改灵魂特质和小任性 -> 只扣 1 张 soul_quirk_change")
    void bothSoulFieldsChange_consumesOneSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, "clingy,flirty", "grumpyMorning", null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("改声音 -> 扣 voice_change")
    void voiceChange_consumesVoiceVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, null, "v2"));
        assertThat(skus).containsExactly("voice_change");
    }

    @Test
    @DisplayName("三项全改 -> 扣三张，顺序 职业性格声音")
    void allChange_consumesThreeInOrder() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after("music", "clingy,flirty", "grumpyMorning", "v2"));
        assertThat(skus).containsExactly("occupation_change", "soul_quirk_change", "voice_change");
    }

    @Test
    @DisplayName("新值与旧值相同（传了但未变）-> 不扣券")
    void sameValue_noVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after("camera", null, null, null));
        assertThat(skus).isEmpty();
    }

    @Test
    @DisplayName("有任意券要扣 -> 需要同步 agent")
    void anyConsume_needsAgentSync() {
        assertThat(ReshapeVoucherRule.needsAgentSync(
                ReshapeVoucherRule.decide(entity("camera", null, null, null),
                        ReshapeVoucherRule.after("music", null, null, null)))).isTrue();
        assertThat(ReshapeVoucherRule.needsAgentSync(
                ReshapeVoucherRule.decide(entity("camera", null, null, null), null))).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd main/manager-api && mvn -q test -Dtest=ReshapeVoucherRuleTest -DskipTests=false`
Expected: 编译失败 / `ReshapeVoucherRule` 不存在。

- [ ] **Step 3: 实现 `ReshapeVoucherRule`**

创建 `main/manager-api/src/main/java/xiaozhi/modules/companion/util/ReshapeVoucherRule.java`：

```java
package xiaozhi.modules.companion.util;

import xiaozhi.modules.companion.entity.CompanionEntity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 「重塑命运」扣券决策（纯函数）。
 *
 * <p>规则（与产品决策一致）：
 * <ul>
 *   <li>职业变化 -> 扣 occupation_change</li>
 *   <li>灵魂特质 或 小任性 任一变化 -> 扣 1 张 soul_quirk_change</li>
 *   <li>声音变化 -> 扣 voice_change</li>
 * </ul>
 * 「传了某字段但与旧值相同」不算变化、不扣券。
 */
public final class ReshapeVoucherRule {

    public static final String OCCUPATION_CHANGE = "occupation_change";
    public static final String SOUL_QUIRK_CHANGE = "soul_quirk_change";
    public static final String VOICE_CHANGE = "voice_change";

    private ReshapeVoucherRule() {
    }

    /** 决定本次 update 需要消耗的券（保持「职业->性格->声音」顺序）。after 可为 null。 */
    public static Set<String> decide(CompanionEntity before, After after) {
        Set<String> skus = new LinkedHashSet<>();
        if (after == null) {
            return skus;
        }
        if (changed(after.occupation, before.getOccupation())) {
            skus.add(OCCUPATION_CHANGE);
        }
        boolean soulTraitsChanged = changed(after.soulTraits, before.getSoulTraits());
        boolean soulQuirkChanged = changed(after.soulQuirk, before.getSoulQuirk());
        if (soulTraitsChanged || soulQuirkChanged) {
            skus.add(SOUL_QUIRK_CHANGE);
        }
        if (changed(after.voice, before.getVoice())) {
            skus.add(VOICE_CHANGE);
        }
        return skus;
    }

    /** 职业或性格或声音任一变化，都需要重新同步 agent 系统提示词与 TTS 音色。 */
    public static boolean needsAgentSync(Set<String> skus) {
        return skus != null && !skus.isEmpty();
    }

    private static boolean changed(String after, String before) {
        return after != null && !after.equals(before);
    }

    /** update DTO 的投影，避免把整个 DTO 带入纯函数。null 表示「不改」。 */
    public static After after(String occupation, String soulTraits, String soulQuirk, String voice) {
        return new After(occupation, soulTraits, soulQuirk, voice);
    }

    public static final class After {
        private final String occupation;
        private final String soulTraits;
        private final String soulQuirk;
        private final String voice;

        private After(String occupation, String soulTraits, String soulQuirk, String voice) {
            this.occupation = occupation;
            this.soulTraits = soulTraits;
            this.soulQuirk = soulQuirk;
            this.voice = voice;
        }
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `cd main/manager-api && mvn -q test -Dtest=ReshapeVoucherRuleTest -DskipTests=false`
Expected: Tests run: 9, Failures: 0。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/util/ReshapeVoucherRule.java \
        main/manager-api/src/test/java/xiaozhi/modules/companion/util/ReshapeVoucherRuleTest.java
git commit -m "feat(companion): 抽出 ReshapeVoucherRule 扣券决策纯函数及单测"
```

---

### Task 3: 把规则接入 `CompanionServiceImpl.update` + 同步 agent

`update`（`CompanionServiceImpl.java`，已 `@Transactional`）。现状扣券块在 line 123–131；voice 写库 line 142；soulTraits 写库 line 156；写库 line 181。

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`（update 方法，约 line 111–184）

- [ ] **Step 1: 替换扣券块（line 123–131）**

把现有：

```java
        boolean occupationChanged = dto.getOccupation() != null && !dto.getOccupation().equals(entity.getOccupation());
        boolean soulQuirkChanged = dto.getSoulQuirk() != null && !dto.getSoulQuirk().equals(entity.getSoulQuirk());
        if (occupationChanged) {
            itemService.consume(entity.getUserId(), "occupation_change", 1,
                    ConsumeBizType.OCCUPATION_CHANGE, entity.getDeviceId());
        }
        if (soulQuirkChanged) {
            itemService.consume(entity.getUserId(), "soul_quirk_change", 1,
                    ConsumeBizType.SOUL_QUIRK_CHANGE, entity.getDeviceId());
        }
```

替换为（用纯函数决策，集中扣券；任一不足会抛 `ITEM_INSUFFICIENT` 并随事务回滚）：

```java
        // 扣券决策抽到纯函数 ReshapeVoucherRule（便于单测）。扣减在写库前集中完成，
        // 事务内后续任何异常都会让扣减一起回滚。
        CompanionEntity snapshot = entity; // 更新前的快照，供决策与变更检测使用
        ReshapeVoucherRule.After after = ReshapeVoucherRule.after(
                dto.getOccupation(), dto.getSoulTraits(), dto.getSoulQuirk(), dto.getVoice());
        Set<String> consumeSkus = ReshapeVoucherRule.decide(snapshot, after);
        for (String sku : consumeSkus) {
            String bizType = switch (sku) {
                case ReshapeVoucherRule.OCCUPATION_CHANGE -> ConsumeBizType.OCCUPATION_CHANGE;
                case ReshapeVoucherRule.SOUL_QUIRK_CHANGE -> ConsumeBizType.SOUL_QUIRK_CHANGE;
                case ReshapeVoucherRule.VOICE_CHANGE -> ConsumeBizType.VOICE_CHANGE;
                default -> sku;
            };
            itemService.consume(entity.getUserId(), sku, 1, bizType, entity.getDeviceId());
        }
        boolean occupationChanged = consumeSkus.contains(ReshapeVoucherRule.OCCUPATION_CHANGE);
        boolean soulChanged = consumeSkus.contains(ReshapeVoucherRule.SOUL_QUIRK_CHANGE);
        boolean voiceChanged = consumeSkus.contains(ReshapeVoucherRule.VOICE_CHANGE);
```

- [ ] **Step 2: 修正字段写库条件**

找到写库区 `if (dto.getVoice() != null) entity.setVoice(dto.getVoice());`（约 line 142），改为：

```java
        if (voiceChanged) {
            entity.setVoice(dto.getVoice());
        }
```

找到写库区（约 line 155–158）：

```java
        if (dto.getSoulTraits() != null) entity.setSoulTraits(dto.getSoulTraits());
        if (soulQuirkChanged) {
            entity.setSoulQuirk(dto.getSoulQuirk());
        }
```

改为（灵魂特质也只在变化时写；小任性沿用原变量名 `soulChanged`）：

```java
        if (soulChanged) {
            if (dto.getSoulTraits() != null) {
                entity.setSoulTraits(dto.getSoulTraits());
            }
            if (dto.getSoulQuirk() != null) {
                entity.setSoulQuirk(dto.getSoulQuirk());
            }
        }
```

- [ ] **Step 3: 写库后同步 agent**

找到 `companionDao.updateById(entity);`（约 line 181），在其**之后**、`return CompanionVO.toVO(entity);`（约 line 184）**之前**插入：

```java
        // 重塑相关字段变化 -> 同步 agent 系统提示词与 TTS 音色（与扣券/写库同事务）。
        if (ReshapeVoucherRule.needsAgentSync(consumeSkus)) {
            String agentId = deviceService.getAgentIdByDeviceId(entity.getDeviceId());
            if (agentId != null && !agentId.isBlank()) {
                syncPromptToAgent(agentId, entity.getId());
            } else {
                log.warn("重塑后未找到 agent，跳过同步，deviceId={}", entity.getDeviceId());
            }
        }
```

- [ ] **Step 4: 补 import**

在 `CompanionServiceImpl.java` 顶部 import 区追加（按字母序插入；`Set` 与 `CompanionEntity` 若已有则跳过）：

```java
import xiaozhi.modules.companion.util.ReshapeVoucherRule;
import xiaozhi.modules.companion.entity.CompanionEntity;
import java.util.Set;
```

> 实现后删除 Step 1 里仅作说明的临时变量 `CompanionEntity snapshot = entity;`，直接把 `entity` 传给 `decide`（保留 snapshot 注释说明语义即可）。即：`Set<String> consumeSkus = ReshapeVoucherRule.decide(entity, after);`，并在其后才发生任何 `entity.setXxx`。

- [ ] **Step 5: 编译验证**

Run: `cd main/manager-api && mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: 跑全部测试回归**

Run: `cd main/manager-api && mvn -q test -DskipTests=false`
Expected: 无回归失败（`ReshapeVoucherRuleTest` 通过，其余既有测试不受影响）。

- [ ] **Step 7: 手工冒烟（dev profile，需 MySQL/Redis）**

启动 `mvn spring-boot:run`，在 Swagger（`http://localhost:8002/xiaozhi/doc.html`）：
1. 给测试用户发券：调 `/item` 相关或直接 DB 插 `user_item(occupation_change, remain_count=1)`。
2. `POST /companion/update { deviceId, occupation: "<新职业>" }` → 成功，`/item/inventory` 中 `occupation_change.remainCount` 减 1，`item_consume_log` 多一条 `biz_type=occupation_change`。
3. 再调一次（无券）→ 返回 `code=10321`。
4. 改 voice → `voice_change` 扣减、`biz_type=voice_change`；改 soulTraits → `soul_quirk_change` 扣减。
5. 确认对应 `agent.system_prompt` 与 `tts_voice_id` 已更新（查 `ai_agent` 表）。

- [ ] **Step 8: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java
git commit -m "feat(companion): update 接入扣券决策并同步 agent 提示词与音色"
```

---

## 前端

> 小程序无构建步骤、无测试框架。纯逻辑（`logic.js` / `voice-catalog.js`）用 Node `assert` 脚本校验（沿用 `logic.js` 文件头「可在 Node 下用 assert 单测」的约定）；页面在微信开发者工具里手工验收。

### Task 4: 音色目录配置 `config/voice-catalog.js`

**Files:**
- Create: `main/miniprogram/config/voice-catalog.js`
- Test: `main/miniprogram/config/voice-catalog.test.js`

- [ ] **Step 1: 写失败测试**

创建 `main/miniprogram/config/voice-catalog.test.js`：

```js
const assert = require('assert');
const cat = require('./voice-catalog');

assert.strictEqual(Array.isArray(cat.DEFAULT_VOICES), true);
assert.strictEqual(cat.DEFAULT_VOICES.length >= 4, true);
// 每条必须有 id / label / audioUrl
cat.DEFAULT_VOICES.forEach(function (v) {
  assert.ok(v.id, 'voice missing id');
  assert.ok(v.label, 'voice missing label');
  assert.ok(v.audioUrl, 'voice missing audioUrl');
});
// 按 id 查
const hit = cat.findById(cat.DEFAULT_VOICES[0].id);
assert.strictEqual(hit.label, cat.DEFAULT_VOICES[0].label);
console.log('voice-catalog.test.js OK');
```

- [ ] **Step 2: 运行，确认失败**

Run: `node main/miniprogram/config/voice-catalog.test.js`
Expected: `Cannot find module './voice-catalog'`。

- [ ] **Step 3: 实现**

创建 `main/miniprogram/config/voice-catalog.js`（默认音色沿用 `destiny.js` 的 4 个 `VOICES`，预留扩展段）：

```js
/**
 * voice-catalog.js
 *
 * 女友音色目录。换声音页与新人引导共用，便于后续上新。
 * id 与后端 companion.voice / agent.tts_voice_id 对齐。
 */

var CDN = 'https://636c-cloud1-9ghrcw8127746c64-1391989435.tcb.qcloud.la';

var DEFAULT_VOICES = [
  { id: 'TTS_HSDSTTS_V2_0001', label: '温糯', audioUrl: CDN + '/girlfriend/voice/female_xiaohe.mp3', tag: 'default' },
  { id: 'TTS_HSDSTTS_V2_0020', label: '撒娇', audioUrl: CDN + '/girlfriend/voice/female_sajiao.mp3', tag: 'default' },
  { id: 'TTS_HSDSTTS_V2_0017', label: '知性', audioUrl: CDN + '/girlfriend/voice/female_sophie.mp3', tag: 'default' },
  { id: 'TTS_HSDSTTS_V2_0022', label: '甜美', audioUrl: CDN + '/girlfriend/voice/female_tianmei.mp3', tag: 'default' }
];

// 扩展位：后续上新的高级/订阅音色在此追加，无需改页面结构。
var EXTRA_VOICES = [];

function all() {
  return DEFAULT_VOICES.concat(EXTRA_VOICES);
}

function findById(id) {
  var list = all();
  for (var i = 0; i < list.length; i++) {
    if (list[i].id === id) return list[i];
  }
  return null;
}

module.exports = {
  DEFAULT_VOICES: DEFAULT_VOICES,
  EXTRA_VOICES: EXTRA_VOICES,
  all: all,
  findById: findById
};
```

- [ ] **Step 4: 运行，确认通过**

Run: `node main/miniprogram/config/voice-catalog.test.js`
Expected: `voice-catalog.test.js OK`。

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/config/voice-catalog.js main/miniprogram/config/voice-catalog.test.js
git commit -m "feat(miniprogram): 新增音色目录配置 voice-catalog"
```

---

### Task 5: 背包卡片「使用」CTA（`logic.js`，TDD）

决策：`consumable_change` 且 `remainCount>0` 时主 CTA 为 `use`（去更换页），原购买降为次「加购」。

**Files:**
- Test: `main/miniprogram/pages/backpack/logic.test.js`
- Modify: `main/miniprogram/pages/backpack/logic.js`（`cardView`）

- [ ] **Step 1: 写失败测试**

创建 `main/miniprogram/pages/backpack/logic.test.js`：

```js
const assert = require('assert');
const logic = require('./logic');

// consumable_change 且持有 > 0 -> use（去更换）
var held = { category: 'consumable_change', remainCount: 2 };
assert.strictEqual(logic.cardView(held).cta, 'use');
assert.strictEqual(logic.cardView(held).badgeType, 'owned');

// consumable_change 且持有 0 -> buy
var none = { category: 'consumable_change', remainCount: 0 };
assert.strictEqual(logic.cardView(none).cta, 'buy');

// outfit 已解锁 -> 不变（go-equip）
var outfit = { category: 'outfit', remainCount: 1 };
assert.strictEqual(logic.cardView(outfit).cta, 'go-equip');

// 其它类别持有 > 0（如 voice_quota）-> 仍 buy（本类不进更换页）
var quota = { category: 'voice_quota', remainCount: 3 };
assert.strictEqual(logic.cardView(quota).cta, 'buy');

console.log('logic.test.js OK');
```

- [ ] **Step 2: 运行，确认失败**

Run: `node main/miniprogram/pages/backpack/logic.test.js`
Expected: `AssertionError`（`held.cta === 'buy'` ≠ `'use'`）。

- [ ] **Step 3: 改 `cardView`**

在 `main/miniprogram/pages/backpack/logic.js` 的 `cardView`（line 65–73）替换为：

```js
function cardView(item) {
  if (item.category === 'outfit' && item.remainCount > 0) {
    return { badgeType: 'unlocked', badgeText: '已解锁', cta: 'go-equip' };
  }
  if (item.category === 'consumable_change' && item.remainCount > 0) {
    return { badgeType: 'owned', badgeText: '拥有 ×' + item.remainCount, cta: 'use' };
  }
  if (item.remainCount > 0) {
    return { badgeType: 'owned', badgeText: '拥有 ×' + item.remainCount, cta: 'buy' };
  }
  return { badgeType: 'none', badgeText: '', cta: 'buy' };
}
```

- [ ] **Step 4: 运行，确认通过**

Run: `node main/miniprogram/pages/backpack/logic.test.js`
Expected: `logic.test.js OK`。

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/backpack/logic.js main/miniprogram/pages/backpack/logic.test.js
git commit -m "feat(miniprogram): 背包 consumable_change 持有时 CTA 改为使用"
```

---

### Task 6: 背包页渲染「使用」+ 次「加购」并跳转

**Files:**
- Modify: `main/miniprogram/pages/backpack/backpack.wxml`
- Modify: `main/miniprogram/pages/backpack/backpack.js`（`onCardTap` + 新增 `onBuyAgain`）

- [ ] **Step 1: `backpack.js` 加更换页跳转映射与次购买处理**

在 `main/miniprogram/pages/backpack/backpack.js` 的 `onCardTap`（line 104–122）里，在现有 `go-equip` 分支之后、`var rule = ...` 之前插入 `use` 分支：

```js
    if (item.cta === 'use') {
      var target = ({
        occupation_change: '/pages/companion/change-occupation/change-occupation',
        soul_quirk_change: '/pages/companion/change-soul/change-soul',
        voice_change: '/pages/companion/change-voice/change-voice'
      })[item.skuCode];
      if (target) {
        wx.navigateTo({ url: target + '?sku=' + item.skuCode });
      } else {
        wx.showToast({ title: '该道具暂不支持使用', icon: 'none' });
      }
      return;
    }
```

在 `onCardTap` 方法**之后**新增次「加购」处理（consumable_change 持有时的次入口，复用既有 purchase-sheet 打开逻辑——把 `onCardTap` 里打开面板的逻辑抽成内部方法）：

把 `onCardTap` 末尾打开面板的 4 行：

```js
    this.setData({
      showSheet: true,
      sheetItem: item,
      sheetRule: rule,
      sheetQty: qty,
      sheetUnitYuan: this._yuan(item.effectivePriceFen),
      sheetTotalYuan: this._yuan(item.effectivePriceFen * qty)
    });
```

替换为：

```js
    this._openSheet(item, rule, qty);
```

并在 `onCardTap` 之后新增：

```js
  _openSheet(item, rule, qty) {
    this.setData({
      showSheet: true,
      sheetItem: item,
      sheetRule: rule,
      sheetQty: qty,
      sheetUnitYuan: this._yuan(item.effectivePriceFen),
      sheetTotalYuan: this._yuan(item.effectivePriceFen * qty)
    });
  },

  // consumable_change 持有时，次入口「加购」强制走购买面板
  onBuyAgain(e) {
    var skuCode = e.currentTarget.dataset.sku;
    var item = (this.data.allItems || []).filter(function (it) { return it.skuCode === skuCode; })[0];
    if (!item) return;
    var rule = logic.quantityRule(item.category);
    this._openSheet(item, rule, rule.defaultQty);
  },
```

- [ ] **Step 2: `backpack.wxml` 卡片右下角区分 `use` / `buy`**

在 `main/miniprogram/pages/backpack/backpack.wxml` 找到卡片 CTA 渲染（line 45 附近）：

```xml
          <view class="bp-cta {{sku.cta === 'go-equip' ? 'bp-cta-ghost' : ''}}" data-sku="{{sku.skuCode}}" bindtap="onCardTap">{{sku.cta === 'go-equip' ? '去换装' : '购买'}}</view>
```

替换为（`use` 时主按钮「使用」+ 次链接「加购」；其余不变）：

```xml
          <view class="bp-right-cta">
            <block wx:if="{{sku.cta === 'use'}}">
              <view class="bp-cta-add" data-sku="{{sku.skuCode}}" bindtap="onBuyAgain">加购</view>
              <view class="bp-cta" data-sku="{{sku.skuCode}}" bindtap="onCardTap">使用</view>
            </block>
            <view class="bp-cta {{sku.cta === 'go-equip' ? 'bp-cta-ghost' : ''}}" wx:else data-sku="{{sku.skuCode}}" bindtap="onCardTap">{{sku.cta === 'go-equip' ? '去换装' : '购买'}}</view>
          </view>
```

- [ ] **Step 3: 补样式**

在 `main/miniprogram/pages/backpack/backpack.wxss` 末尾追加：

```css
.bp-right-cta { display:flex; align-items:center; gap:12rpx; }
.bp-cta-add { font-size:22rpx; color:#b8860b; padding:6rpx 4rpx; }
```

- [ ] **Step 4: 微信开发者工具验收**

在工具中编译 `pages/backpack`：
- 持有 `consumable_change` 券时卡片显示「使用」主按钮 + 「加购」次链接 + 「拥有 ×N」徽标。
- 点「使用」→ 跳转对应更换页（Task 8–10 完成前会报页面不存在，正常）。
- 点「加购」→ 弹出既有购买面板。

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/backpack/backpack.js main/miniprogram/pages/backpack/backpack.wxml main/miniprogram/pages/backpack/backpack.wxss
git commit -m "feat(miniprogram): 背包券卡片使用入口跳转更换页并保留加购"
```

---

### Task 7: 共享二次确认面板 `components/reshape-confirm`

底部弹层：标题、由→变为、（可选）试听按钮、消耗行、确认按钮。自身不发请求，点确认触发父页面回调。

**Files:**
- Create: `main/miniprogram/components/reshape-confirm/reshape-confirm.{js,wxml,wxss,json}`

- [ ] **Step 1: `reshape-confirm.json`**

```json
{ "component": true, "usingComponents": {} }
```

- [ ] **Step 2: `reshape-confirm.js`**

```js
/**
 * reshape-confirm：重塑命运二次确认底部面板。
 * props: show, title, from, to, voucherName, remainCount, listenable, audioUrl
 * event: confirm（点击「确认重塑」）, listen（点试听）, close（遮罩/关闭）
 */
Component({
  properties: {
    show: { type: Boolean, value: false },
    title: { type: String, value: '为她重塑' },
    from: { type: String, value: '' },
    to: { type: String, value: '' },
    voucherName: { type: String, value: '' },
    remainCount: { type: Number, value: 0 },
    listenable: { type: Boolean, value: false }, // 换声音为 true
    audioUrl: { type: String, value: '' }
  },
  methods: {
    onOverlay() { if (this.data.show) this.triggerEvent('close'); },
    onStop() {},
    onConfirm() { this.triggerEvent('confirm'); },
    onListen() { this.triggerEvent('listen'); }
  }
});
```

- [ ] **Step 3: `reshape-confirm.wxml`**

```xml
<view class="rc-overlay {{show ? 'rc-show' : ''}}" bindtap="onOverlay">
  <view class="rc-sheet" catchtap="onStop" wx:if="{{show}}">
    <view class="rc-grab"></view>
    <view class="rc-title">{{title}}</view>
    <view class="rc-sub">确认后将消耗一张{{voucherName}}，立即生效</view>
    <view class="rc-change">
      <view class="rc-half">由 <text class="rc-from">{{from}}</text></view>
      <view class="rc-arrow">→</view>
      <view class="rc-half rc-right">变为 <text class="rc-to">{{to}}</text></view>
    </view>
    <view class="rc-listen" wx:if="{{listenable}}" bindtap="onListen">▶ 再试听一下「{{to}}」</view>
    <view class="rc-cost">将消耗 1 张{{voucherName}}（剩余 {{remainCount}} 张）</view>
    <view class="rc-btn" bindtap="onConfirm">确认重塑</view>
  </view>
</view>
```

- [ ] **Step 4: `reshape-confirm.wxss`**

```css
.rc-overlay { position:fixed; inset:0; background:rgba(40,30,35,0); visibility:hidden; z-index:100; transition:background .25s }
.rc-overlay.rc-show { background:rgba(40,30,35,.35); visibility:visible }
.rc-sheet { position:absolute; bottom:0; left:0; right:0; background:#fbf9f8; border-radius:32rpx 32rpx 0 0; padding:16rpx 36rpx calc(48rpx + env(safe-area-inset-bottom)); transform:translateY(100%); transition:transform .3s cubic-bezier(.16,1,.3,1) }
.rc-overlay.rc-show .rc-sheet { transform:translateY(0) }
.rc-grab { width:80rpx; height:8rpx; background:#e0d4d6; border-radius:8rpx; margin:8rpx auto 28rpx }
.rc-title { font-size:32rpx; font-weight:700; color:#3a2a2e; text-align:center }
.rc-sub { font-size:22rpx; color:#8a7a7e; text-align:center; margin-top:8rpx }
.rc-change { display:flex; align-items:center; gap:16rpx; background:rgba(255,255,255,.8); border:1rpx solid rgba(134,78,90,.12); border-radius:24rpx; padding:28rpx; margin:28rpx 0 }
.rc-half { flex:1; font-size:24rpx; color:#8a7a7e }
.rc-right { text-align:right }
.rc-from { color:#6a5a5e; font-size:26rpx }
.rc-to { color:#864e5a; font-size:28rpx; font-weight:600 }
.rc-arrow { color:#b8860b; font-size:32rpx }
.rc-listen { display:flex; align-items:center; justify-content:center; gap:12rpx; background:rgba(134,78,90,.08); border:1rpx solid rgba(134,78,90,.2); color:#864e5a; font-size:26rpx; font-weight:600; padding:24rpx; border-radius:24rpx; margin-bottom:24rpx }
.rc-cost { font-size:24rpx; color:#6a5a5e; text-align:center; margin-bottom:28rpx }
.rc-cost { color:#b8860b }
.rc-btn { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:30rpx; font-weight:600; padding:28rpx; border-radius:48rpx; text-align:center; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
```

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/components/reshape-confirm/
git commit -m "feat(miniprogram): 新增重塑二次确认面板组件 reshape-confirm"
```

---

### Task 8: 换职业页 `change-occupation`

**Files:**
- Create: `main/miniprogram/pages/companion/change-occupation/{js,wxml,wxss,json}`

- [ ] **Step 1: `change-occupation.json`**

```json
{ "usingComponents": { "reshape-confirm": "/components/reshape-confirm/reshape-confirm" }, "navigationBarTitleText": "更换职业" }
```

- [ ] **Step 2: `change-occupation.js`**

```js
/**
 * change-occupation：换职业。复用 destiny 的九宫格选择器。
 * 流程：选新职业 -> 二次确认 -> POST /companion/update -> 成功态 -> 置 needReconnectAfterReshape。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');

const OCCUPATIONS = [
  { id: 'design', label: '大厂设计师', icon: '🎨' },
  { id: 'camera', label: '自由摄影师', icon: '📷' },
  { id: 'medical', label: '白衣天使', icon: '🩺' },
  { id: 'child', label: '幼儿园老师', icon: '🧒' },
  { id: 'yoga', label: '瑜伽教练', icon: '🧘' },
  { id: 'radio', label: '电台主播', icon: '📻' },
  { id: 'school', label: '大学生', icon: '🎓' },
  { id: 'music', label: '独立音乐人', icon: '🎵' },
  { id: 'cosplay', label: '知名Coser', icon: '🎮' }
];

const LABELS = OCCUPATIONS.reduce(function (m, o) { m[o.id] = o.label; return m; }, {});

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    currentOcc: '',
    currentLabel: '',
    occupations: OCCUPATIONS,
    selected: '',
    selectedLabel: '',
    remain: 0,
    showConfirm: false,
    submitting: false,
    done: false,
    doneLabel: ''
  },

  onLoad() {
    applyTheme(this);
    const app = getApp();
    const deviceId = (app.globalData && app.globalData.virtualMAC) || '';
    this.setData({ deviceId });
    this._load();
  },
  onShow() { applyTheme(this); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      const occ = c ? c.occupation : '';
      this.setData({ currentOcc: occ, currentLabel: LABELS[occ] || occ });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'occupation_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onOccTap(e) {
    const id = e.currentTarget.dataset.id;
    const occ = OCCUPATIONS.filter(function (o) { return o.id === id; })[0];
    this.setData({ selected: id, selectedLabel: occ ? occ.label : '' });
  },

  onConfirmTap() {
    if (!this.data.selected || this.data.submitting) return;
    if (this.data.selected === this.data.currentOcc) {
      wx.showToast({ title: '请选择不同的职业', icon: 'none' });
      return;
    }
    if (this.data.remain <= 0) {
      this._noVoucher();
      return;
    }
    this.setData({ showConfirm: true });
  },

  _noVoucher() {
    wx.showModal({
      title: '还没有换职业券',
      content: '换职业需要消耗一张换职业券（¥299）',
      confirmText: '去背包获取',
      cancelText: '再想想',
      success: (r) => {
        if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=occupation_change' });
      }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', { deviceId: this.data.deviceId, occupation: this.data.selected });
      wx.hideLoading();
      if (!res || res.code !== 0) {
        const code = res && res.code;
        if (code === 10321) { this._noVoucher(); }
        else { wx.showToast({ title: (res && res.msg) || '更换失败', icon: 'none' }); }
        this.setData({ submitting: false });
        return;
      }
      const app = getApp();
      app.globalData.needReconnectAfterReshape = true;
      this.setData({ done: true, doneLabel: this.data.selectedLabel });
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  onDone() { wx.navigateBack(); },
  onCloseConfirm() { this.setData({ showConfirm: false }); }
});
```

- [ ] **Step 3: `change-occupation.wxml`**

```xml
<view class="co-container {{darkMode ? 'dark' : ''}}">
  <block wx:if="{{!done}}">
    <view class="co-cur">当前 · {{currentLabel || '未设置'}}</view>
    <view class="co-grid">
      <view class="co-cell {{selected === item.id ? 'sel' : ''}}" wx:for="{{occupations}}" wx:key="id"
            data-id="{{item.id}}" bindtap="onOccTap">
        <view class="co-ic">{{item.icon}}</view>
        <view class="co-lb">{{item.label}}</view>
      </view>
    </view>
    <view class="co-foot">
      <view class="co-info">将消耗 1 张换职业券（剩 {{remain}} 张）</view>
      <view class="co-btn {{(!selected || submitting) ? 'dis' : ''}}" bindtap="onConfirmTap">确认更换</view>
    </view>
  </block>

  <view class="co-success" wx:else>
    <view class="co-spark">✨</view>
    <view class="co-st">命运已重塑</view>
    <view class="co-ss">她的职业已更新为「{{doneLabel}}」，下一次对话将以全新姿态陪你</view>
    <view class="co-row"><text class="co-rk">消耗</text><text class="co-rv">1 张换职业券</text></view>
    <view class="co-done" bindtap="onDone">完成</view>
  </view>

  <reshape-confirm show="{{showConfirm}}" title="为她换上新职业" from="{{currentLabel}}" to="{{selectedLabel}}"
                   voucherName="换职业券" remainCount="{{remain}}"
                   bind:confirm="onReshape" bind:close="onCloseConfirm" />
</view>
```

- [ ] **Step 4: `change-occupation.wxss`**

```css
.co-container { min-height:100vh; background:#f6f3f2; padding:24rpx 28rpx 160rpx; box-sizing:border-box }
.co-container.dark { background:#121220 }
.co-cur { background:rgba(184,134,11,.08); border:1rpx solid rgba(184,134,11,.2); border-radius:20rpx; padding:24rpx; font-size:24rpx; color:#6a5a3e; margin-bottom:28rpx }
.co-container.dark .co-cur { color:#daa520; background:rgba(218,165,32,.1) }
.co-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:20rpx }
.co-cell { background:rgba(255,255,255,.75); border:2rpx solid transparent; border-radius:24rpx; padding:28rpx 12rpx; text-align:center; backdrop-filter:blur(8rpx) }
.co-container.dark .co-cell { background:rgba(30,28,46,.85) }
.co-cell.sel { border-color:#864e5a; background:rgba(134,78,90,.1); box-shadow:0 6rpx 18rpx rgba(134,78,90,.18) }
.co-ic { font-size:48rpx }
.co-lb { font-size:22rpx; color:#3a2a2e; margin-top:8rpx; font-weight:600 }
.co-cell.sel .co-lb { color:#864e5a }
.co-container.dark .co-lb { color:#e8e4e3 }
.co-foot { position:fixed; left:0; right:0; bottom:0; background:rgba(251,249,248,.96); backdrop-filter:blur(12rpx); border-top:1rpx solid rgba(134,78,90,.1); display:flex; align-items:center; gap:20rpx; padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)) }
.co-container.dark .co-foot { background:#1e1c2e }
.co-info { flex:1; font-size:22rpx; color:#8a7a7e }
.co-btn { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 52rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
.co-btn.dis { background:#d4c4c6; box-shadow:none }
.co-success { display:flex; flex-direction:column; align-items:center; padding-top:200rpx; text-align:center }
.co-spark { font-size:112rpx; filter:drop-shadow(0 8rpx 16rpx rgba(184,134,11,.4)) }
.co-st { font-size:36rpx; font-weight:700; color:#3a2a2e; margin-top:32rpx }
.co-container.dark .co-st { color:#e8e4e3 }
.co-ss { font-size:24rpx; color:#8a7a7e; margin-top:16rpx; line-height:1.6; width:80% }
.co-row { display:flex; justify-content:space-between; width:84%; background:rgba(255,255,255,.8); border:1rpx solid rgba(134,78,90,.12); border-radius:24rpx; padding:24rpx; margin:40rpx 0 }
.co-container.dark .co-row { background:rgba(30,28,46,.85) }
.co-rk { font-size:24rpx; color:#8a7a7e } .co-rv { font-size:24rpx; color:#b8860b }
.co-done { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 80rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
```

- [ ] **Step 5: 在 `app.json` 注册 + 验收**

在 Task 12 一并注册。本步先在开发者工具手动添加页面路径临时编译，确认九宫格选择、确认面板、无券弹窗、成功态正常。

- [ ] **Step 6: Commit**

```bash
git add main/miniprogram/pages/companion/change-occupation/
git commit -m "feat(miniprogram): 新增换职业页 change-occupation"
```

---

### Task 9: 换性格页 `change-soul`

灵魂特质（最多 2 条，复用 `companion-codes.SOUL_TRAITS`）+ 小任性（单选，`QUIRKS`）。一张 `soul_quirk_change` 同时提交两者。

**Files:**
- Create: `main/miniprogram/pages/companion/change-soul/{js,wxml,wxss,json}`

- [ ] **Step 1: `change-soul.json`**

```json
{ "usingComponents": { "reshape-confirm": "/components/reshape-confirm/reshape-confirm" }, "navigationBarTitleText": "更换性格" }
```

- [ ] **Step 2: `change-soul.js`**

```js
/**
 * change-soul：换性格。一张 soul_quirk_change 同时改灵魂特质(max2)+小任性(单选)。
 * 提交时把灵魂特质逗号分隔成 soulTraits、小任性成 soulQuirk 一并 POST。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');
const codes = require('../../../config/companion-codes');

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    curTraitsStr: '', curTraitsLabel: '', curQuirkLabel: '',
    traits: codes.SOUL_TRAITS.map(function (t) { return { id: t.id, label: t.label, sel: false }; }),
    quirks: codes.QUIRKS.map(function (q) { return { id: q.id, label: q.label }; }),
    traitSel: [], quirkSel: '', curQuirkId: '',
    quirkSelLabel: '',
    fromLabel: '',
    toLabel: '',
    remain: 0,
    showConfirm: false,
    submitting: false,
    done: false
  },

  onLoad() {
    applyTheme(this);
    const app = getApp();
    this.setData({ deviceId: (app.globalData && app.globalData.virtualMAC) || '' });
    this._load();
  },
  onShow() { applyTheme(this); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      if (!c) return;
      const traitsStr = c.soulTraits || '';
      const traitsArr = traitsStr ? traitsStr.split(',') : [];
      const curTraitsLabel = codes.SOUL_TRAITS.filter(function (t) { return traitsArr.indexOf(t.id) > -1; })
        .map(function (t) { return t.label; }).join(' · ');
      const curQuirkLabel = codes.getLabel(codes.QUIRKS, c.soulQuirk);
      // 预选当前值
      const traits = this.data.traits.map(function (t) { t.sel = traitsArr.indexOf(t.id) > -1; return t; });
      this.setData({
        curTraitsStr: traitsStr, curTraitsLabel, curQuirkLabel, curQuirkId: c.soulQuirk || '',
        traits, traitSel: traitsArr, quirkSel: c.soulQuirk || '', quirkSelLabel: curQuirkLabel,
        fromLabel: (curTraitsLabel || '未设置') + ' ／ ' + (curQuirkLabel || '未设置')
      });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'soul_quirk_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onTraitTap(e) {
    const idx = e.currentTarget.dataset.index;
    const traits = this.data.traits;
    if (traits[idx].sel) {
      traits[idx].sel = false;
    } else {
      const cnt = traits.filter(function (t) { return t.sel; }).length;
      if (cnt >= 2) { wx.showToast({ title: '最多选择两条', icon: 'none' }); return; }
      traits[idx].sel = true;
    }
    const sel = traits.filter(function (t) { return t.sel; });
    const ids = sel.map(function (t) { return t.id; });
    const label = sel.map(function (t) { return t.label; }).join(' · ');
    this.setData({ traits, traitSel: ids, toLabel: (label || '未设置') + ' ／ ' + (this.data.quirkSelLabel || '未设置') });
  },

  onQuirkTap(e) {
    const idx = e.currentTarget.dataset.index;
    const q = this.data.quirks[idx];
    const same = this.data.quirkSel === q.id;
    this.setData({
      quirkSel: same ? '' : q.id, quirkSelLabel: same ? '' : q.label,
      toLabel: this._traitsLabel() + ' ／ ' + (same ? '未设置' : q.label)
    });
  },

  _traitsLabel() {
    return this.data.traits.filter(function (t) { return t.sel; }).map(function (t) { return t.label; }).join(' · ') || '未设置';
  },

  // 变化检测：灵魂特质集合（排序后）或小任性不同，才算需要重塑
  _isChanged() {
    const newStr = this.data.traitSel.slice().sort().join(',');
    const oldStr = (this.data.curTraitsStr || '').split(',').filter(Boolean).sort().join(',');
    return newStr !== oldStr || this.data.quirkSel !== this.data.curQuirkId;
  },

  onConfirmTap() {
    if (this.data.submitting) return;
    if (this.data.traitSel.length < 1 || !this.data.quirkSel) {
      wx.showToast({ title: '请选择灵魂特质和小任性', icon: 'none' }); return;
    }
    if (!this._isChanged()) { wx.showToast({ title: '内容未发生变化', icon: 'none' }); return; }
    if (this.data.remain <= 0) { this._noVoucher(); return; }
    this.setData({ showConfirm: true });
  },

  _noVoucher() {
    wx.showModal({
      title: '还没有换性格券', content: '重塑性格需要一张换性格券（¥99）',
      confirmText: '去背包获取', cancelText: '再想想',
      success: (r) => { if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=soul_quirk_change' }); }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', {
        deviceId: this.data.deviceId,
        soulTraits: this.data.traitSel.join(','),
        soulQuirk: this.data.quirkSel
      });
      wx.hideLoading();
      if (!res || res.code !== 0) {
        if (res && res.code === 10321) { this._noVoucher(); }
        else { wx.showToast({ title: (res && res.msg) || '更换失败', icon: 'none' }); }
        this.setData({ submitting: false }); return;
      }
      getApp().globalData.needReconnectAfterReshape = true;
      this.setData({ done: true });
    } catch (e) {
      wx.hideLoading(); wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    } finally { this.setData({ submitting: false }); }
  },

  onDone() { wx.navigateBack(); },
  onCloseConfirm() { this.setData({ showConfirm: false }); }
});
```

- [ ] **Step 3: `change-soul.wxml`**

```xml
<view class="cs-container {{darkMode ? 'dark' : ''}}">
  <block wx:if="{{!done}}">
    <view class="cs-cur">当前 · {{curTraitsLabel || '未设置'}} ／ {{curQuirkLabel || '未设置'}}</view>

    <view class="cs-title">灵魂特质（最多 2 条）</view>
    <view class="cs-traits">
      <view class="cs-trait {{item.sel ? 'sel' : ''}}" wx:for="{{traits}}" wx:key="id"
            data-index="{{index}}" bindtap="onTraitTap">{{item.label}}</view>
    </view>

    <view class="cs-title">小任性（选 1 条）</view>
    <view class="cs-quirks">
      <view class="cs-quirk {{quirkSel === item.id ? 'sel' : ''}}" wx:for="{{quirks}}" wx:key="id"
            data-index="{{index}}" bindtap="onQuirkTap">{{item.label}}</view>
    </view>

    <view class="cs-foot">
      <view class="cs-info">将消耗 1 张换性格券（剩 {{remain}} 张）</view>
      <view class="cs-btn {{submitting ? 'dis' : ''}}" bindtap="onConfirmTap">确认更换</view>
    </view>
  </block>

  <view class="cs-success" wx:else>
    <view class="cs-spark">✨</view>
    <view class="cs-st">命运已重塑</view>
    <view class="cs-ss">她的性格已更新，下一次对话将以全新姿态陪你</view>
    <view class="cs-row"><text class="cs-rk">消耗</text><text class="cs-rv">1 张换性格券</text></view>
    <view class="cs-done" bindtap="onDone">完成</view>
  </view>

  <reshape-confirm show="{{showConfirm}}" title="为她重塑性格" from="{{fromLabel}}" to="{{toLabel}}"
                   voucherName="换性格券" remainCount="{{remain}}"
                   bind:confirm="onReshape" bind:close="onCloseConfirm" />
</view>
```

- [ ] **Step 4: `change-soul.wxss`**

```css
.cs-container { min-height:100vh; background:#f6f3f2; padding:24rpx 28rpx 160rpx; box-sizing:border-box }
.cs-container.dark { background:#121220 }
.cs-cur { background:rgba(184,134,11,.08); border:1rpx solid rgba(184,134,11,.2); border-radius:20rpx; padding:24rpx; font-size:24rpx; color:#6a5a3e; margin-bottom:28rpx }
.cs-container.dark .cs-cur { color:#daa520; background:rgba(218,165,32,.1) }
.cs-title { font-size:26rpx; color:#864e5a; font-weight:600; margin:24rpx 4rpx 16rpx }
.cs-container.dark .cs-title { color:#d8a8ae }
.cs-traits { display:grid; grid-template-columns:repeat(2,1fr); gap:16rpx }
.cs-trait { background:rgba(255,255,255,.75); border:2rpx solid transparent; border-radius:20rpx; padding:24rpx; text-align:center; font-size:26rpx; color:#3a2a2e }
.cs-container.dark .cs-trait { background:rgba(30,28,46,.85); color:#e8e4e3 }
.cs-trait.sel { border-color:#864e5a; background:rgba(134,78,90,.1); color:#864e5a; font-weight:600 }
.cs-quirks { display:flex; flex-wrap:wrap; gap:16rpx }
.cs-quirk { background:rgba(255,255,255,.75); border:2rpx solid transparent; border-radius:40rpx; padding:16rpx 28rpx; font-size:24rpx; color:#3a2a2e }
.cs-container.dark .cs-quirk { background:rgba(30,28,46,.85); color:#e8e4e3 }
.cs-quirk.sel { border-color:#864e5a; background:rgba(134,78,90,.12); color:#864e5a; font-weight:600 }
.cs-foot { position:fixed; left:0; right:0; bottom:0; background:rgba(251,249,248,.96); backdrop-filter:blur(12rpx); border-top:1rpx solid rgba(134,78,90,.1); display:flex; align-items:center; gap:20rpx; padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)) }
.cs-container.dark .cs-foot { background:#1e1c2e }
.cs-info { flex:1; font-size:22rpx; color:#8a7a7e }
.cs-btn { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 52rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
.cs-btn.dis { background:#d4c4c6; box-shadow:none }
.cs-success { display:flex; flex-direction:column; align-items:center; padding-top:200rpx; text-align:center }
.cs-spark { font-size:112rpx; filter:drop-shadow(0 8rpx 16rpx rgba(184,134,11,.4)) }
.cs-st { font-size:36rpx; font-weight:700; color:#3a2a2e; margin-top:32rpx }
.cs-container.dark .cs-st { color:#e8e4e3 }
.cs-ss { font-size:24rpx; color:#8a7a7e; margin-top:16rpx; line-height:1.6; width:80% }
.cs-row { display:flex; justify-content:space-between; width:84%; background:rgba(255,255,255,.8); border:1rpx solid rgba(134,78,90,.12); border-radius:24rpx; padding:24rpx; margin:40rpx 0 }
.cs-container.dark .cs-row { background:rgba(30,28,46,.85) }
.cs-rk { font-size:24rpx; color:#8a7a7e } .cs-rv { font-size:24rpx; color:#b8860b }
.cs-done { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 80rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
```

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/companion/change-soul/
git commit -m "feat(miniprogram): 新增换性格页 change-soul"
```

---

### Task 10: 换声音页 `change-voice`（列表 + 试听）

**Files:**
- Create: `main/miniprogram/pages/companion/change-voice/{js,wxml,wxss,json}`

- [ ] **Step 1: `change-voice.json`**

```json
{ "usingComponents": { "reshape-confirm": "/components/reshape-confirm/reshape-confirm" }, "navigationBarTitleText": "更换声音" }
```

- [ ] **Step 2: `change-voice.js`**

```js
/**
 * change-voice：换声音。列表结构（容纳更多音色），每条可试听。
 * 试听逻辑搬自 destiny.js 的 InnerAudioContext 单实例。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');
const catalog = require('../../../config/voice-catalog');

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    voices: catalog.all(),
    curVoiceId: '',
    curLabel: '',
    selected: '',
    selectedLabel: '',
    selectedAudio: '',
    playingId: '',
    remain: 0,
    showConfirm: false,
    submitting: false,
    done: false
  },

  _audio: null,

  onLoad() {
    applyTheme(this);
    const app = getApp();
    this.setData({ deviceId: (app.globalData && app.globalData.virtualMAC) || '' });
    this._load();
  },
  onShow() { applyTheme(this); },
  onUnload() { this._stop(); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      const id = c ? c.voice : '';
      const v = catalog.findById(id);
      this.setData({ curVoiceId: id, curLabel: v ? v.label : id });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'voice_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  // 点 ▶ 试听
  onPlay(e) {
    const id = e.currentTarget.dataset.id;
    const v = this.data.voices.filter(function (x) { return x.id === id; })[0];
    if (!v) return;
    if (this.data.playingId === id) { this._stop(); return; }
    this._stop();
    const audio = wx.createInnerAudioContext();
    audio.src = v.audioUrl;
    audio.onEnded = () => this.setData({ playingId: '' });
    audio.onError = () => this.setData({ playingId: '' });
    audio.play();
    this._audio = audio;
    this.setData({ playingId: id });
  },
  _stop() {
    if (this._audio) { this._audio.stop(); this._audio.destroy(); this._audio = null; }
    this.setData({ playingId: '' });
  },

  // 点整行（非播放按钮）选中
  onVoiceTap(e) {
    const id = e.currentTarget.dataset.id;
    const v = this.data.voices.filter(function (x) { return x.id === id; })[0];
    if (!v) return;
    this.setData({ selected: id, selectedLabel: v.label, selectedAudio: v.audioUrl });
  },

  onConfirmTap() {
    if (this.data.submitting) return;
    if (!this.data.selected) { wx.showToast({ title: '请选择新声音', icon: 'none' }); return; }
    if (this.data.selected === this.data.curVoiceId) { wx.showToast({ title: '请选择不同的声音', icon: 'none' }); return; }
    if (this.data.remain <= 0) { this._noVoucher(); return; }
    this.setData({ showConfirm: true });
  },

  // 确认面板内的「再试听」
  onListen() {
    if (this.data.selectedAudio) {
      const fake = { currentTarget: { dataset: { id: this.data.selected } } };
      // 复用 onPlay：临时把选中项加入播放
      this._stop();
      const audio = wx.createInnerAudioContext();
      audio.src = this.data.selectedAudio;
      audio.onEnded = () => this.setData({ playingId: '' });
      audio.onError = () => this.setData({ playingId: '' });
      audio.play();
      this._audio = audio;
      this.setData({ playingId: this.data.selected });
    }
  },

  _noVoucher() {
    wx.showModal({
      title: '还没有换声音券', content: '换声音需要一张换声音券（¥99）',
      confirmText: '去背包获取', cancelText: '再想想',
      success: (r) => { if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=voice_change' }); }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    this._stop();
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', { deviceId: this.data.deviceId, voice: this.data.selected });
      wx.hideLoading();
      if (!res || res.code !== 0) {
        if (res && res.code === 10321) { this._noVoucher(); }
        else { wx.showToast({ title: (res && res.msg) || '更换失败', icon: 'none' }); }
        this.setData({ submitting: false }); return;
      }
      getApp().globalData.needReconnectAfterReshape = true;
      this.setData({ done: true });
    } catch (e) {
      wx.hideLoading(); wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    } finally { this.setData({ submitting: false }); }
  },

  onDone() { wx.navigateBack(); },
  onCloseConfirm() { this.setData({ showConfirm: false }); }
});
```

- [ ] **Step 3: `change-voice.wxml`**

```xml
<view class="cv-container {{darkMode ? 'dark' : ''}}">
  <block wx:if="{{!done}}">
    <view class="cv-cur">当前 · {{curLabel || '未设置'}}　点 ▶ 试听，选择她的新音色</view>

    <view class="cv-title">系统音色</view>
    <view class="cv-list">
      <view class="cv-row {{selected === item.id ? 'sel' : ''}}" wx:for="{{voices}}" wx:key="id"
            data-id="{{item.id}}" bindtap="onVoiceTap">
        <view class="cv-play {{playingId === item.id ? 'playing' : ''}}" catchtap="onPlay" data-id="{{item.id}}">
          {{playingId === item.id ? '♪' : '▶'}}
        </view>
        <view class="cv-info">
          <view class="cv-name">{{item.label}}</view>
          <view class="cv-tag">{{item.tag === 'default' ? '默认' : (item.tag || '')}}</view>
        </view>
        <view class="cv-check" wx:if="{{selected === item.id}}">✓</view>
      </view>
    </view>
    <view class="cv-more">更多音色持续上新中</view>

    <view class="cv-foot">
      <view class="cv-info">将消耗 1 张换声音券（剩 {{remain}} 张）</view>
      <view class="cv-btn {{(!selected || submitting) ? 'dis' : ''}}" bindtap="onConfirmTap">确认更换</view>
    </view>
  </block>

  <view class="cv-success" wx:else>
    <view class="cv-spark">✨</view>
    <view class="cv-st">命运已重塑</view>
    <view class="cv-ss">她的声音已更新为「{{selectedLabel}}」，下一次对话将以全新姿态陪你</view>
    <view class="cv-row-sum"><text class="cv-rk">消耗</text><text class="cv-rv">1 张换声音券</text></view>
    <view class="cv-done" bindtap="onDone">完成</view>
  </view>

  <reshape-confirm show="{{showConfirm}}" title="为她换上新声音" from="{{curLabel}}" to="{{selectedLabel}}"
                   voucherName="换声音券" remainCount="{{remain}}" listenable="{{true}}" audioUrl="{{selectedAudio}}"
                   bind:confirm="onReshape" bind:listen="onListen" bind:close="onCloseConfirm" />
</view>
```

- [ ] **Step 4: `change-voice.wxss`**

```css
.cv-container { min-height:100vh; background:#f6f3f2; padding:24rpx 28rpx 160rpx; box-sizing:border-box }
.cv-container.dark { background:#121220 }
.cv-cur { background:rgba(184,134,11,.08); border:1rpx solid rgba(184,134,11,.2); border-radius:20rpx; padding:24rpx; font-size:22rpx; color:#6a5a3e; margin-bottom:20rpx }
.cv-container.dark .cv-cur { color:#daa520; background:rgba(218,165,32,.1) }
.cv-title { font-size:26rpx; color:#864e5a; font-weight:600; margin:20rpx 4rpx 14rpx }
.cv-container.dark .cv-title { color:#d8a8ae }
.cv-row { background:rgba(255,255,255,.75); border:2rpx solid transparent; border-radius:24rpx; padding:24rpx; display:flex; align-items:center; gap:20rpx; margin-bottom:16rpx; backdrop-filter:blur(8rpx) }
.cv-container.dark .cv-row { background:rgba(30,28,46,.85) }
.cv-row.sel { border-color:#864e5a; background:rgba(134,78,90,.08) }
.cv-play { width:68rpx; height:68rpx; border-radius:50%; background:rgba(134,78,90,.12); display:flex; align-items:center; justify-content:center; font-size:28rpx; color:#864e5a }
.cv-play.playing { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff }
.cv-info { flex:1 } .cv-name { font-size:28rpx; font-weight:600; color:#3a2a2e }
.cv-container.dark .cv-name { color:#e8e4e3 }
.cv-row.sel .cv-name { color:#864e5a }
.cv-tag { font-size:20rpx; color:#5a8a4e; margin-top:6rpx }
.cv-check { font-size:32rpx; color:#864e5a }
.cv-more { text-align:center; font-size:20rpx; color:#a99; padding:20rpx 0; border-top:1rpx dashed rgba(134,78,90,.15) }
.cv-foot { position:fixed; left:0; right:0; bottom:0; background:rgba(251,249,248,.96); backdrop-filter:blur(12rpx); border-top:1rpx solid rgba(134,78,90,.1); display:flex; align-items:center; gap:20rpx; padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)) }
.cv-container.dark .cv-foot { background:#1e1c2e }
.cv-info { flex:1; font-size:22rpx; color:#8a7a7e }
.cv-btn { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 52rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
.cv-btn.dis { background:#d4c4c6; box-shadow:none }
.cv-success { display:flex; flex-direction:column; align-items:center; padding-top:200rpx; text-align:center }
.cv-spark { font-size:112rpx; filter:drop-shadow(0 8rpx 16rpx rgba(184,134,11,.4)) }
.cv-st { font-size:36rpx; font-weight:700; color:#3a2a2e; margin-top:32rpx }
.cv-container.dark .cv-st { color:#e8e4e3 }
.cv-ss { font-size:24rpx; color:#8a7a7e; margin-top:16rpx; line-height:1.6; width:80% }
.cv-row-sum { display:flex; justify-content:space-between; width:84%; background:rgba(255,255,255,.8); border:1rpx solid rgba(134,78,90,.12); border-radius:24rpx; padding:24rpx; margin:40rpx 0 }
.cv-container.dark .cv-row-sum { background:rgba(30,28,46,.85) }
.cv-rk { font-size:24rpx; color:#8a7a7e } .cv-rv { font-size:24rpx; color:#b8860b }
.cv-done { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 80rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
```

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/companion/change-voice/
git commit -m "feat(miniprogram): 新增换声音页 change-voice（列表+试听）"
```

---

### Task 11: 我的女友资料页 `profile`（入口②，仅 3 项）

**Files:**
- Create: `main/miniprogram/pages/companion/profile/{js,wxml,wxss,json}`

- [ ] **Step 1: `profile.json`**

```json
{ "usingComponents": {}, "navigationBarTitleText": "我的女友" }
```

- [ ] **Step 2: `profile.js`**

```js
/**
 * profile：我的女友资料页（仅职业/性格/声音 3 项可换）。
 */
const { getTheme, applyTheme } = require('../../utils/theme');
const { get } = require('../../utils/request');
const codes = require('../../config/companion-codes');
const voiceCatalog = require('../../config/voice-catalog');

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    characterName: '', avatar: '',
    occLabel: '', soulLabel: '', voiceLabel: ''
  },

  onLoad() {
    applyTheme(this);
    const app = getApp();
    this.setData({ deviceId: (app.globalData && app.globalData.virtualMAC) || '' });
  },
  onShow() { applyTheme(this); this._load(); },

  async _load() {
    try {
      const res = await get('/companion/detail/' + this.data.deviceId);
      const c = (res && res.code === 0 && res.data) ? res.data : null;
      if (!c) return;
      const traitsArr = c.soulTraits ? c.soulTraits.split(',') : [];
      const soulLabel = codes.SOUL_TRAITS.filter(function (t) { return traitsArr.indexOf(t.id) > -1; })
        .map(function (t) { return t.label; }).join(' · ');
      const quirkLabel = codes.getLabel(codes.QUIRKS, c.soulQuirk);
      const vv = voiceCatalog.findById(c.voice);
      this.setData({
        characterName: c.character || '我的女友',
        avatar: c.avatar || '',
        occLabel: c.occupation || '未设置',
        soulLabel: (soulLabel || '未设置') + ' ／ ' + (quirkLabel || '未设置'),
        voiceLabel: vv ? vv.label : (c.voice || '未设置')
      });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onOcc() { wx.navigateTo({ url: '/pages/companion/change-occupation/change-occupation' }); },
  onSoul() { wx.navigateTo({ url: '/pages/companion/change-soul/change-soul' }); },
  onVoice() { wx.navigateTo({ url: '/pages/companion/change-voice/change-voice' }); }
});
```

- [ ] **Step 3: `profile.wxml`**

```xml
<view class="pf-container {{darkMode ? 'dark' : ''}}">
  <view class="pf-head">
    <image class="pf-ava" src="{{avatar || '/images/avatar-default.png'}}" mode="aspectFill" />
    <view class="pf-name">{{characterName}}</view>
  </view>

  <view class="pf-row" bindtap="onOcc">
    <view class="pf-ic">💼</view>
    <view class="pf-meta"><view class="pf-lb">职业</view><view class="pf-val">{{occLabel}}</view></view>
    <view class="pf-go">更换 ›</view>
  </view>
  <view class="pf-row" bindtap="onSoul">
    <view class="pf-ic">🎭</view>
    <view class="pf-meta"><view class="pf-lb">性格 · 灵魂特质 / 小任性</view><view class="pf-val">{{soulLabel}}</view></view>
    <view class="pf-go">更换 ›</view>
  </view>
  <view class="pf-row" bindtap="onVoice">
    <view class="pf-ic">🎙️</view>
    <view class="pf-meta"><view class="pf-lb">声音</view><view class="pf-val">{{voiceLabel}}</view></view>
    <view class="pf-go">更换 ›</view>
  </view>

  <view class="pf-note">每项更换消耗 1 张对应券（职业券 / 性格券 / 声音券）</view>
</view>
```

- [ ] **Step 4: `profile.wxss`**

```css
.pf-container { min-height:100vh; background:#f6f3f2; padding:32rpx 28rpx; box-sizing:border-box }
.pf-container.dark { background:#121220 }
.pf-head { text-align:center; padding:24rpx 0 40rpx }
.pf-ava { width:144rpx; height:144rpx; border-radius:50%; background:linear-gradient(135deg,#d8a8ae,#864e5a); box-shadow:0 8rpx 24rpx rgba(134,78,90,.25) }
.pf-name { font-size:34rpx; font-weight:600; color:#3a2a2e; margin-top:20rpx }
.pf-container.dark .pf-name { color:#e8e4e3 }
.pf-row { background:rgba(255,255,255,.75); border:1rpx solid rgba(255,255,255,.8); border-radius:24rpx; padding:28rpx; display:flex; align-items:center; gap:20rpx; margin-bottom:20rpx; backdrop-filter:blur(8rpx) }
.pf-container.dark .pf-row { background:rgba(30,28,46,.85); border-color:rgba(255,255,255,.1) }
.pf-ic { width:72rpx; height:72rpx; border-radius:20rpx; background:rgba(184,134,11,.12); display:flex; align-items:center; justify-content:center; font-size:36rpx }
.pf-meta { flex:1 } .pf-lb { font-size:22rpx; color:#8a7a7e } .pf-val { font-size:28rpx; font-weight:600; color:#3a2a2e; margin-top:6rpx }
.pf-container.dark .pf-val { color:#e8e4e3 }
.pf-go { font-size:24rpx; color:#864e5a; font-weight:600 }
.pf-note { text-align:center; font-size:20rpx; color:#b8860b; margin-top:32rpx; line-height:1.6 }
```

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/companion/profile/
git commit -m "feat(miniprogram): 新增我的女友资料页 profile"
```

---

### Task 12: 注册新页 + 设置页入口

**Files:**
- Modify: `main/miniprogram/app.json`
- Modify: `main/miniprogram/pages/settings/settings.js`（新增 `onCompanionTap`）

- [ ] **Step 1: `app.json` 注册 4 个页面**

在 `main/miniprogram/app.json` 的 `pages` 数组末尾（`"pages/memory-anchor/memory-anchor"` 后）追加：

```json
    ,
    "pages/companion/profile/profile",
    "pages/companion/change-occupation/change-occupation",
    "pages/companion/change-soul/change-soul",
    "pages/companion/change-voice/change-voice"
```

- [ ] **Step 2: `settings.js` 增加入口方法**

在 `main/miniprogram/pages/settings/settings.js` 内（与 `onBackpackTap` 同级）新增：

```js
  onCompanionTap() {
    wx.navigateTo({ url: '/pages/companion/profile/profile' });
  },
```

- [ ] **Step 3: 在 settings 页面挂一个可点入口**

在 `main/miniprogram/pages/settings/settings.wxml` 中，在「我的背包」入口卡片附近新增一个入口（沿用既有 entry-card 结构；具体 class 以 settings.wxml 现有为淮）：

```xml
<view class="entry-card" bindtap="onCompanionTap">
  <view class="entry-icon">🧝‍♀️</view>
  <view class="entry-text">我的女友</view>
  <view class="entry-arrow">›</view>
</view>
```

> 若 settings.wxml 的入口结构与上述 class 不同，复用该页「我的背包」入口卡片的同名结构，仅替换文案与 `bindtap="onCompanionTap"`。

- [ ] **Step 4: 微信开发者工具全链路验收**

编译运行：
- 「我」→「我的女友」可见头像/名字/3 项当前值。
- 每项「更换」→ 进对应更换页 → 选新值 → 二次确认 → 成功态。
- 持有 0 券时点确认 → 「还没有 X 券」弹窗 →「去背包获取」跳背包。
- 背包券卡片「使用」→ 直达更换页。

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/app.json main/miniprogram/pages/settings/settings.js main/miniprogram/pages/settings/settings.wxml
git commit -m "feat(miniprogram): 注册伴侣页面并新增资料页入口"
```

---

### Task 13: 在线女友断开重连（`index.js`）

复用 `needReconnectAfterSub` 模式：重塑成功置位 `needReconnectAfterReshape`，聊天页 `onShow` 检测到且当前已连接就断开，下次「召唤」加载新属性。

**Files:**
- Modify: `main/miniprogram/pages/index/index.js`（`onShow`，约 line 173–180）

- [ ] **Step 1: 在 `onShow` 订阅断开块后追加重塑断开块**

在 `main/miniprogram/pages/index/index.js` 的 `onShow` 中，紧接 `needReconnectAfterSub` 处理块（line 174–180）**之后**插入：

```js
    // 重塑命运（换职业/性格/声音）后，断开当前连接，由用户手动「召唤」重新拉取最新 agent 配置
    if (g0 && g0.needReconnectAfterReshape) {
      g0.needReconnectAfterReshape = false;
      if (this.wsManager && this.data.connectionState === 'connected') {
        this.wsManager.disconnect();
      }
    }
```

- [ ] **Step 2: 验收（端到端）**

- 保持女友在线（聊天页已连接）。
- 经背包/资料页重塑任一项 → 成功态 → 「完成」回到聊天页。
- 观察连接状态变为 `disconnected`（召唤按钮恢复可点）。
- 点「召唤」重连 → 新职业/性格/声音生效（语音 TTS 音色、人设回复更新）。

- [ ] **Step 3: Commit**

```bash
git add main/miniprogram/pages/index/index.js
git commit -m "feat(miniprogram): 重塑后断开在线女友连接由召唤重连加载新属性"
```

---

## 收尾

### Task 14: 全量回归与验收清单核对

- [ ] **Step 1: 后端测试**

Run: `cd main/manager-api && mvn -q test -DskipTests=false`
Expected: 全绿（含 `ReshapeVoucherRuleTest` 9 例）。

- [ ] **Step 2: 前端纯逻辑测试**

Run:
```bash
node main/miniprogram/config/voice-catalog.test.js
node main/miniprogram/pages/backpack/logic.test.js
```
Expected: 两个脚本均输出 `OK`。

- [ ] **Step 3: 对照 spec §12 验收标准逐条核对**（在微信开发者工具 + dev 后端）：
- [ ] 入口① 背包三券 `remainCount>0` 显示「使用」，直达更换页。
- [ ] 入口② 「我的女友」展示当前 3 项，「更换」进更换页。
- [ ] 换职业/性格/声音：选值 → 确认面板 → 确认重塑 → 成功态 → 返回，对应券 -1。
- [ ] 换性格一张券同改 灵魂特质+小任性。
- [ ] 换声音确认面板含「再试听」。
- [ ] 无券（`remainCount===0`）→ 弹窗 +「去背包获取」；服务端 10321 同样降级。
- [ ] 后端：四字段任一变化对应券扣减、`item_consume_log` 落库；无券抛 10321。
- [ ] 重塑后 agent `system_prompt` 与 `tts_voice_id` 更新。
- [ ] 在线女友重塑后断开 WS，召唤重连加载新属性。
- [ ] 暗色模式与现有页一致，无新主色。

- [ ] **Step 4: 收尾提交（如有遗留改动）**

```bash
git add -A
git commit -m "test: 重塑命运消费流程回归通过" --allow-empty
```

---

## 备注：测试现实约束

- 后端 `manager-api` 默认 `skipTests=true`，运行单测须加 `-DskipTests=false`。`ReshapeVoucherRuleTest` 为**纯 JUnit+AssertJ 单测**，不依赖 MySQL/Redis/Spring 上下文，CI 可直接跑。
- `CompanionServiceImpl.update` 的端到端验证为**手工冒烟**（Task 3 Step 7），因其依赖 `SecurityUser` 安全上下文与 dev 库，不纳入自动单测（符合本项目既有「集成测试靠 `@SpringBootTest` + dev profile」的现状）。
- 前端纯逻辑用 Node `assert` 脚本（`*.test.js`）校验，页面交互在微信开发者工具手工验收。
