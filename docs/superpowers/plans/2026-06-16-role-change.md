# 角色变更（role_change）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `role_change`（角色变更券）道具，让用户付费变更女友角色（高冷白月光 / 元气邻家妹 / 知性御姐 / 潮酷二次元），并同步刷新 agent 提示词；小程序新增「更换角色」模块。

**Architecture:** 四个角色即 `ai_companion.character` 字段的既有取值（`CompanionLabels.CHARACTER` 已映射），提示词模板已有 `{{character}}` 占位符。因此把 `character` 接入现成的「重塑扣券 + 同步提示词」机制（`ReshapeVoucherRule` + `CompanionServiceImpl.update()`），完全平行于 `occupation`（职业变更）。顺带修复「只改 character 不触发 agent 提示词同步」的缺口。Python 端零改动（人设为 `ai_agent.system_prompt` 不透明字符串，重连即生效）。

**Tech Stack:** Java 21 / Spring Boot 3.4.3 / MyBatis-Plus / Liquibase（manager-api）；微信小程序 WXML/WXSS/JS（miniprogram）；JUnit 5 + AssertJ（测试）。

**约定：**
- 所有 Java 文件位于 `main/manager-api/src/main/...`，测试位于 `main/manager-api/src/test/...`。
- 小程序文件位于 `main/miniprogram/...`。
- 提交遵循用户的「commit only when the user asks」约定——计划中的 commit 步骤为检查点，执行时需先与用户确认再提交。

---

## File Structure

**后端 (manager-api)**
| 文件 | 责任 | 动作 |
|---|---|---|
| `src/main/resources/db/changelog/202606161000.sql` | 新增 role_change SKU 种子数据 | 新增 |
| `src/main/resources/db/changelog/db.changelog-master.yaml` | 注册新 changeset | 修改（追加） |
| `src/main/java/xiaozhi/modules/item/enums/ConsumeBizType.java` | 道具消耗业务类型常量 | 修改（+1 常量） |
| `src/main/java/xiaozhi/modules/companion/util/ReshapeVoucherRule.java` | 重塑扣券纯函数决策 | 修改（+character 维度） |
| `src/test/java/xiaozhi/modules/companion/util/ReshapeVoucherRuleTest.java` | 扣券决策单测 | 修改（+角色用例） |
| `src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java` | update 门控 + bizType + 同步触发 | 修改（4 处） |

**小程序 (miniprogram)**
| 文件 | 责任 | 动作 |
|---|---|---|
| `config/companion-codes.js` | 角色选项 ROLES 列表 | 修改（+ROLES） |
| `pages/companion/change-role/change-role.{js,wxml,wxss,json}` | 更换角色页（克隆 change-occupation） | 新增 |
| `pages/backpack/backpack.js` | SKU→页面映射 + 图标 stem | 修改（2 处） |
| `pages/companion/profile/profile.{js,wxml}` | 角色资料行 + onRole 跳转 | 修改 |
| `app.json` | 注册新页面 | 修改 |
| `images/role.png` / `images/role-dark.png` | 道具图标（浅/深） | 新增（资源） |

**Python (xiaozhi-server)**：零改动。

---

## Task 1: 新增 role_change SKU 种子数据

**Files:**
- Create: `main/manager-api/src/main/resources/db/changelog/202606161000.sql`
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`（末尾追加）

- [ ] **Step 1: 创建 SQL 文件**

`main/manager-api/src/main/resources/db/changelog/202606161000.sql`：
```sql
-- 新增道具 SKU：角色变更券
INSERT INTO ai_item_sku (sku_code, sku_name, category, price_fen, attributes, description, sort)
VALUES ('role_change', '角色变更', 'consumable_change', 9900, NULL, '一次性变更女友角色', 14);
```

- [ ] **Step 2: 在 master changelog 末尾注册 changeset**

在 `db.changelog-master.yaml` 最末尾（`202606151832` changeSet 之后）追加：
```yaml
  - changeSet:
      id: 202606161000
      author: minwang
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202606161000.sql
```

- [ ] **Step 3: 校验 YAML 缩进与文件存在**

Run: `ls main/manager-api/src/main/resources/db/changelog/202606161000.sql`
Expected: 文件路径回显（存在）。

> 注：该 INSERT 在后端启动时由 Liquibase 自动执行；DB 落库校验放在 Task 10 端到端验证。

---

## Task 2: 新增 ConsumeBizType.ROLE_CHANGE 常量

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/item/enums/ConsumeBizType.java`

- [ ] **Step 1: 在 VOICE_CHANGE 之后新增常量**

在 `ConsumeBizType.java` 第 16 行（`VOICE_CHANGE` 常量）之后插入：
```java
    /** 角色变更 */
    public static final String ROLE_CHANGE = "role_change";
```

- [ ] **Step 2: 编译校验**

Run: `cd main/manager-api && mvn -q compile`
Expected: BUILD SUCCESS。

---

## Task 3: ReshapeVoucherRule 支持 character 维度（TDD）

**Files:**
- Modify: `src/test/java/xiaozhi/modules/companion/util/ReshapeVoucherRuleTest.java`（整体重写）
- Modify: `src/main/java/xiaozhi/modules/companion/util/ReshapeVoucherRule.java`（整体重写）

- [ ] **Step 1: 重写测试文件（先写失败用例）**

将 `ReshapeVoucherRuleTest.java` 整体替换为：
```java
package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xiaozhi.modules.companion.entity.CompanionEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReshapeVoucherRule 扣券决策")
class ReshapeVoucherRuleTest {

    // character 置首位，与 after(character, occupation, ...) 顺序一致
    private CompanionEntity entity(String character, String occupation, String soulTraits, String soulQuirk, String voice) {
        CompanionEntity e = new CompanionEntity();
        e.setCharacter(character);
        e.setOccupation(occupation);
        e.setSoulTraits(soulTraits);
        e.setSoulQuirk(soulQuirk);
        e.setVoice(voice);
        return e;
    }

    @Test
    @DisplayName("什么都不改 -> 不扣券")
    void noChange_noVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"), null);
        assertThat(skus).isEmpty();
    }

    @Test
    @DisplayName("改角色 -> 扣 role_change")
    void characterChange_consumesRole() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", null, null, null),
                ReshapeVoucherRule.after("linjiamei", null, null, null, null));
        assertThat(skus).containsExactly("role_change");
    }

    @Test
    @DisplayName("角色与旧值相同（传了但未变）-> 不扣券")
    void characterSameAsBefore_noVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", null, null, null),
                ReshapeVoucherRule.after("baiyueguang", null, null, null, null));
        assertThat(skus).isEmpty();
    }

    @Test
    @DisplayName("改职业 -> 扣 occupation_change")
    void occupationChange_consumesOccupation() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, "music", null, null, null));
        assertThat(skus).containsExactly("occupation_change");
    }

    @Test
    @DisplayName("改灵魂特质（小任性不变）-> 扣 soul_quirk_change")
    void soulTraitsChange_consumesSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, "clingy,flirty", null, null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("改小任性 -> 扣 soul_quirk_change")
    void soulQuirkChange_consumesSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, null, "grumpyMorning", null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("同时改灵魂特质和小任性 -> 只扣 1 张 soul_quirk_change")
    void bothSoulFieldsChange_consumesOneSoulVoucher() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, "clingy,flirty", "grumpyMorning", null));
        assertThat(skus).containsExactly("soul_quirk_change");
    }

    @Test
    @DisplayName("改声音 -> 扣 voice_change")
    void voiceChange_consumesVoice() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after(null, null, null, null, "v2"));
        assertThat(skus).containsExactly("voice_change");
    }

    @Test
    @DisplayName("角色与职业同时变化 -> 各扣一张")
    void characterAndOccupationChange_consumesBoth() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", null, null, null),
                ReshapeVoucherRule.after("linjiamei", "music", null, null, null));
        assertThat(skus).containsExactly("role_change", "occupation_change");
    }

    @Test
    @DisplayName("四项全改 -> 扣四张，顺序 角色->职业->性格->声音")
    void allChange_consumesFourInOrder() {
        Set<String> skus = ReshapeVoucherRule.decide(
                entity("baiyueguang", "camera", "clingy", "jealous", "v1"),
                ReshapeVoucherRule.after("erciyuan", "music", "clingy,flirty", "grumpyMorning", "v2"));
        assertThat(skus).containsExactly("role_change", "occupation_change", "soul_quirk_change", "voice_change");
    }

    @Test
    @DisplayName("有任意券要扣 -> 需要同步 agent")
    void anyConsume_needsAgentSync() {
        assertThat(ReshapeVoucherRule.needsAgentSync(
                ReshapeVoucherRule.decide(entity("baiyueguang", "camera", null, null, null),
                        ReshapeVoucherRule.after("linjiamei", null, null, null, null)))).isTrue();
        assertThat(ReshapeVoucherRule.needsAgentSync(
                ReshapeVoucherRule.decide(entity("baiyueguang", "camera", null, null, null), null))).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败（after 签名不匹配）**

Run: `cd main/manager-api && mvn -q test -Dtest=ReshapeVoucherRuleTest -DskipTests=false`
Expected: 编译失败——`after(String,String,String,String)` 与新调用 `after(String,String,String,String,String)` 参数个数不匹配；`ROLE_CHANGE` 常量不存在。

- [ ] **Step 3: 重写实现，使测试通过**

将 `ReshapeVoucherRule.java` 整体替换为：
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
 *   <li>角色变化 -> 扣 role_change</li>
 *   <li>职业变化 -> 扣 occupation_change</li>
 *   <li>灵魂特质 或 小任性 任一变化 -> 扣 1 张 soul_quirk_change</li>
 *   <li>声音变化 -> 扣 voice_change</li>
 * </ul>
 * 「传了某字段但与旧值相同」不算变化、不扣券。
 */
public final class ReshapeVoucherRule {

    public static final String ROLE_CHANGE = "role_change";
    public static final String OCCUPATION_CHANGE = "occupation_change";
    public static final String SOUL_QUIRK_CHANGE = "soul_quirk_change";
    public static final String VOICE_CHANGE = "voice_change";

    private ReshapeVoucherRule() {
    }

    /** 决定本次 update 需要消耗的券（保持「角色->职业->性格->声音」顺序）。after 可为 null。 */
    public static Set<String> decide(CompanionEntity before, After after) {
        Set<String> skus = new LinkedHashSet<>();
        if (after == null) {
            return skus;
        }
        if (changed(after.character, before.getCharacter())) {
            skus.add(ROLE_CHANGE);
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

    /** 角色或职业或性格或声音任一变化，都需要重新同步 agent 系统提示词与 TTS 音色。 */
    public static boolean needsAgentSync(Set<String> skus) {
        return skus != null && !skus.isEmpty();
    }

    private static boolean changed(String after, String before) {
        return after != null && !after.equals(before);
    }

    /** update DTO 的投影，避免把整个 DTO 带入纯函数。null 表示「不改」。 */
    public static After after(String character, String occupation, String soulTraits, String soulQuirk, String voice) {
        return new After(character, occupation, soulTraits, soulQuirk, voice);
    }

    public static final class After {
        private final String character;
        private final String occupation;
        private final String soulTraits;
        private final String soulQuirk;
        private final String voice;

        private After(String character, String occupation, String soulTraits, String soulQuirk, String voice) {
            this.character = character;
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
Expected: BUILD SUCCESS，全部用例通过（11 个）。

> 注：此步后 `CompanionServiceImpl` 会因 `after(...)` 由 4 参变 5 参而编译失败——这是预期，Task 4 修复。

---

## Task 4: CompanionServiceImpl.update() 接入 character

**Files:**
- Modify: `src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`（4 处）

- [ ] **Step 1: after(...) 调用补传 character（第 126-127 行）**

将：
```java
        ReshapeVoucherRule.After after = ReshapeVoucherRule.after(
                dto.getOccupation(), dto.getSoulTraits(), dto.getSoulQuirk(), dto.getVoice());
```
改为：
```java
        ReshapeVoucherRule.After after = ReshapeVoucherRule.after(
                dto.getCharacter(), dto.getOccupation(), dto.getSoulTraits(), dto.getSoulQuirk(), dto.getVoice());
```

- [ ] **Step 2: bizType switch 增加 ROLE_CHANGE 分支（第 130-135 行）**

将：
```java
            String bizType = switch (sku) {
                case ReshapeVoucherRule.OCCUPATION_CHANGE -> ConsumeBizType.OCCUPATION_CHANGE;
                case ReshapeVoucherRule.SOUL_QUIRK_CHANGE -> ConsumeBizType.SOUL_QUIRK_CHANGE;
                case ReshapeVoucherRule.VOICE_CHANGE -> ConsumeBizType.VOICE_CHANGE;
                default -> sku;
            };
```
改为：
```java
            String bizType = switch (sku) {
                case ReshapeVoucherRule.ROLE_CHANGE -> ConsumeBizType.ROLE_CHANGE;
                case ReshapeVoucherRule.OCCUPATION_CHANGE -> ConsumeBizType.OCCUPATION_CHANGE;
                case ReshapeVoucherRule.SOUL_QUIRK_CHANGE -> ConsumeBizType.SOUL_QUIRK_CHANGE;
                case ReshapeVoucherRule.VOICE_CHANGE -> ConsumeBizType.VOICE_CHANGE;
                default -> sku;
            };
```

- [ ] **Step 3: 增加 characterChanged 标志（第 138-140 行区域）**

将：
```java
        boolean occupationChanged = consumeSkus.contains(ReshapeVoucherRule.OCCUPATION_CHANGE);
        boolean soulChanged = consumeSkus.contains(ReshapeVoucherRule.SOUL_QUIRK_CHANGE);
        boolean voiceChanged = consumeSkus.contains(ReshapeVoucherRule.VOICE_CHANGE);
```
改为：
```java
        boolean characterChanged = consumeSkus.contains(ReshapeVoucherRule.ROLE_CHANGE);
        boolean occupationChanged = consumeSkus.contains(ReshapeVoucherRule.OCCUPATION_CHANGE);
        boolean soulChanged = consumeSkus.contains(ReshapeVoucherRule.SOUL_QUIRK_CHANGE);
        boolean voiceChanged = consumeSkus.contains(ReshapeVoucherRule.VOICE_CHANGE);
```

- [ ] **Step 4: 把免费的 character 修改门控为扣券后执行（第 162-165 行）**

将：
```java
        if (dto.getCharacter() != null && !dto.getCharacter().equals(entity.getCharacter())) {
            entity.setCharacter(dto.getCharacter());
            needRecalcBirth = true;
        }
```
改为：
```java
        if (characterChanged) {
            entity.setCharacter(dto.getCharacter());
            needRecalcBirth = true;
        }
```

> 说明：character 变更进入 `consumeSkus` 后，第 199 行 `ReshapeVoucherRule.needsAgentSync(consumeSkus)` 为真，自动触发 `syncPromptToAgent`，重渲染 `{{character}}` 并写回 `ai_agent.system_prompt`——「同步调整提示词」无需额外代码。

- [ ] **Step 5: 编译 + 回归测试**

Run: `cd main/manager-api && mvn -q test -DskipTests=false`
Expected: BUILD SUCCESS，全量测试通过（含 ReshapeVoucherRuleTest）。

> 服务层单测说明：核心决策逻辑已由 `ReshapeVoucherRuleTest`（纯函数）覆盖；`update()` 此处为薄映射层（`consumeSkus.contains(ROLE_CHANGE)` → 扣券/门控/同步），其行为可由 Task 10 端到端验证。如团队测试基建支持 `SecurityUser` 静态 Mock（mockito-inline），可另行补充 `CompanionServiceImpl` 集成测试，非必需。

---

## Task 5: 小程序新增 ROLES 选项列表

**Files:**
- Modify: `main/miniprogram/config/companion-codes.js`

- [ ] **Step 1: 在 PET_TYPES 之后新增 ROLES，并在 module.exports 导出**

在第 37 行（`PET_TYPES` 数组结束）之后插入：
```js
var ROLES = [
  { id: 'baiyueguang', label: '高冷白月光' },
  { id: 'linjiamei', label: '元气邻家妹' },
  { id: 'zhixingyujie', label: '知性御姐' },
  { id: 'erciyuan', label: '潮酷二次元' },
];
```

并在 `module.exports`（第 79-90 行）中追加导出键：
```js
  ROLES: ROLES,
```
（放在 `PET_TYPES: PET_TYPES,` 之后。）

> 标签须与后端 `CompanionLabels.CHARACTER` 完全一致；id 与 `CHARACTER_AVATARS`/`CHARACTER_IMAGES` 的 key 一致。

- [ ] **Step 2: 语法校验**

在微信开发者工具中编译 miniprogram，Expected: 无报错。

---

## Task 6: 新增更换角色页（克隆 change-occupation）

**Files:**
- Create: `main/miniprogram/pages/companion/change-role/change-role.js`
- Create: `main/miniprogram/pages/companion/change-role/change-role.wxml`
- Create: `main/miniprogram/pages/companion/change-role/change-role.wxss`
- Create: `main/miniprogram/pages/companion/change-role/change-role.json`

- [ ] **Step 1: 创建 change-role.js**

`change-role.js`：
```js
/**
 * change-role：换角色。复用九宫格选择器（4 选项）。
 * 流程：选新角色 -> 二次确认 -> POST /companion/update -> 成功态 -> 置 needReconnectAfterReshape。
 */
const { getTheme, applyTheme } = require('../../../utils/theme');
const { get, post } = require('../../../utils/request');
const codes = require('../../../config/companion-codes');

const ROLES = codes.ROLES;
const LABELS = ROLES.reduce(function (m, r) { m[r.id] = r.label; return m; }, {});
const CHARACTER_AVATARS = codes.CHARACTER_AVATARS;
const CHARACTER_IMAGES = codes.CHARACTER_IMAGES;

Page({
  data: {
    darkMode: getTheme(),
    deviceId: '',
    currentRole: '',
    currentLabel: '',
    roles: ROLES,
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
      const role = c ? c.character : '';
      this.setData({
        currentRole: role,
        currentLabel: LABELS[role] || role,
        selected: role,
        selectedLabel: LABELS[role] || role
      });
      const inv = await get('/item/inventory');
      const list = (inv && inv.code === 0 && inv.data) ? inv.data : [];
      const row = list.filter(function (i) { return i.skuCode === 'role_change'; })[0];
      this.setData({ remain: row ? (row.remainCount || 0) : 0 });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onRoleTap(e) {
    const id = e.currentTarget.dataset.id;
    const r = ROLES.filter(function (x) { return x.id === id; })[0];
    this.setData({ selected: id, selectedLabel: r ? r.label : '' });
  },

  onConfirmTap() {
    if (!this.data.selected || this.data.submitting) return;
    if (this.data.selected === this.data.currentRole) {
      wx.showToast({ title: '请选择不同的角色', icon: 'none' });
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
      title: '还没有换角色券',
      content: '换角色需要消耗一张换角色券（¥99）',
      confirmText: '去背包获取',
      cancelText: '再想想',
      success: (r) => {
        if (r.confirm) wx.navigateTo({ url: '/pages/backpack/backpack?focus=role_change' });
      }
    });
  },

  async onReshape() {
    if (this.data.submitting) return;
    this.setData({ submitting: true, showConfirm: false });
    wx.showLoading({ title: '重塑中', mask: true });
    try {
      const res = await post('/companion/update', {
        deviceId: this.data.deviceId,
        character: this.data.selected,
        avatar: CHARACTER_AVATARS[this.data.selected],
        defaultImage: CHARACTER_IMAGES[this.data.selected]
      });
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

- [ ] **Step 2: 创建 change-role.wxml**

`change-role.wxml`（4 选项，沿用 `co-` 类名，小程序页面级 wxss 不冲突）：
```html
<view class="co-container {{darkMode ? 'dark' : ''}}">
  <block wx:if="{{!done}}">
    <view class="co-cur">当前 · {{currentLabel || '未设置'}}</view>
    <view class="co-grid">
      <view class="co-cell {{selected === item.id ? 'sel' : ''}}" wx:for="{{roles}}" wx:key="id"
            data-id="{{item.id}}" bindtap="onRoleTap">
        <view class="co-lb">{{item.label}}</view>
      </view>
    </view>
    <view class="co-foot">
      <view class="co-info">将消耗 1 张角色变更券（剩 {{remain}} 张）</view>
      <view class="co-btn {{(!selected || submitting) ? 'dis' : ''}}" bindtap="onConfirmTap">确认更换</view>
    </view>
  </block>

  <view class="co-success" wx:else>
    <view class="co-st">命运已重塑</view>
    <view class="co-ss">她的角色已更新为「{{doneLabel}}」，下一次对话将以全新姿态陪你</view>
    <view class="co-row"><text class="co-rk">消耗</text><text class="co-rv">1 张换角色券</text></view>
    <view class="co-done" bindtap="onDone">完成</view>
  </view>

  <reshape-confirm show="{{showConfirm}}" title="为她换上新角色" from="{{currentLabel}}" to="{{selectedLabel}}"
                   voucherName="换角色券" remainCount="{{remain}}"
                   bind:confirm="onReshape" bind:close="onCloseConfirm" />
</view>
```

- [ ] **Step 3: 创建 change-role.wxss**

`change-role.wxss`（与 change-occupation 一致，仅网格改为 2 列以适配 4 选项）：
```css
.co-container { min-height:100vh; background:#f6f3f2; padding:24rpx 28rpx 160rpx; box-sizing:border-box }
.co-container.dark { background:#121220 }
.co-cur { background:rgba(184,134,11,.08); border:1rpx solid rgba(184,134,11,.2); border-radius:20rpx; padding:24rpx; font-size:24rpx; color:#6a5a3e; margin-bottom:28rpx }
.co-container.dark .co-cur { color:#daa520; background:rgba(218,165,32,.1) }
.co-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:20rpx }
.co-cell { background:rgba(255,255,255,.75); border:2rpx solid transparent; border-radius:24rpx; padding:36rpx 12rpx; text-align:center; backdrop-filter:blur(8rpx) }
.co-container.dark .co-cell { background:rgba(30,28,46,.85) }
.co-cell.sel { border-color:#864e5a; background:rgba(134,78,90,.1); box-shadow:0 6rpx 18rpx rgba(134,78,90,.18) }
.co-lb { font-size:26rpx; color:#3a2a2e; line-height:1.4; font-weight:600 }
.co-cell.sel .co-lb { color:#864e5a }
.co-container.dark .co-lb { color:#e8e4e3 }
.co-foot { position:fixed; left:0; right:0; bottom:0; background:rgba(251,249,248,.96); backdrop-filter:blur(12rpx); border-top:1rpx solid rgba(134,78,90,.1); display:flex; align-items:center; gap:20rpx; padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)) }
.co-container.dark .co-foot { background:#1e1c2e }
.co-info { flex:1; font-size:22rpx; color:#8a7a7e }
.co-btn { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 52rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
.co-btn.dis { background:#d4c4c6; box-shadow:none }
.co-success { display:flex; flex-direction:column; align-items:center; padding-top:200rpx; text-align:center }
.co-st { font-size:36rpx; font-weight:700; color:#3a2a2e; margin-top:32rpx }
.co-container.dark .co-st { color:#e8e4e3 }
.co-ss { font-size:24rpx; color:#8a7a7e; margin-top:16rpx; line-height:1.6; width:80% }
.co-row { display:flex; justify-content:space-between; width:84%; background:rgba(255,255,255,.8); border:1rpx solid rgba(134,78,90,.12); border-radius:24rpx; padding:24rpx; margin:40rpx 0 }
.co-container.dark .co-row { background:rgba(30,28,46,.85) }
.co-rk { font-size:24rpx; color:#8a7a7e } .co-rv { font-size:24rpx; color:#b8860b }
.co-done { background:linear-gradient(135deg,#864e5a,#d4737a); color:#fff; font-size:28rpx; font-weight:600; padding:24rpx 80rpx; border-radius:48rpx; box-shadow:0 8rpx 24rpx rgba(134,78,90,.3) }
```

- [ ] **Step 4: 创建 change-role.json**

`change-role.json`：
```json
{ "usingComponents": { "reshape-confirm": "/components/reshape-confirm/reshape-confirm" }, "navigationBarTitleText": "更换角色" }
```

- [ ] **Step 5: 编译校验**

微信开发者工具编译 miniprogram，Expected: 无报错（页面需先在 Task 7 注册到 app.json 后方可预览）。

---

## Task 7: 注册路由与道具图标映射

**Files:**
- Modify: `main/miniprogram/app.json`
- Modify: `main/miniprogram/pages/backpack/backpack.js`

- [ ] **Step 1: app.json pages 数组注册新页面**

在 `app.json` 的 `pages` 数组中，`"pages/companion/change-voice/change-voice"` 之后追加：
```json
,
    "pages/companion/change-role/change-role"
```
（即数组变为 12 项，`change-role` 在末尾。）

- [ ] **Step 2: backpack.js 的 ICON_IMG_BY_SKU 增加 role_change**

将 `backpack.js` 第 13-17 行：
```js
var ICON_IMG_BY_SKU = {
  occupation_change: 'occupation',
  soul_quirk_change: 'soul',
  voice_change: 'voice'
};
```
改为：
```js
var ICON_IMG_BY_SKU = {
  occupation_change: 'occupation',
  soul_quirk_change: 'soul',
  voice_change: 'voice',
  role_change: 'role'
};
```

- [ ] **Step 3: backpack.js 的 onCardTap 目标映射增加 role_change**

将 `backpack.js` 第 113-117 行：
```js
      var target = ({
        occupation_change: '/pages/companion/change-occupation/change-occupation',
        soul_quirk_change: '/pages/companion/change-soul/change-soul',
        voice_change: '/pages/companion/change-voice/change-voice'
      })[item.skuCode];
```
改为：
```js
      var target = ({
        occupation_change: '/pages/companion/change-occupation/change-occupation',
        soul_quirk_change: '/pages/companion/change-soul/change-soul',
        voice_change: '/pages/companion/change-voice/change-voice',
        role_change: '/pages/companion/change-role/change-role'
      })[item.skuCode];
```

- [ ] **Step 4: 编译校验**

微信开发者工具编译，Expected: 无报错；背包页 consumable_change 分组出现「角色变更」道具卡片。

---

## Task 8: 资料页新增角色行 + onRole 跳转

**Files:**
- Modify: `main/miniprogram/pages/companion/profile/profile.js`
- Modify: `main/miniprogram/pages/companion/profile/profile.wxml`

- [ ] **Step 1: profile.js 增加 roleLabel 数据与 onRole 方法**

在 `profile.js` 的 `data`（第 14 行）：
```js
    occLabel: '', soulLabel: '', voiceLabel: ''
```
改为：
```js
    occLabel: '', roleLabel: '', soulLabel: '', voiceLabel: ''
```

在 `_load` 的 `this.setData({ ... })`（第 34-40 行）中，于 `occLabel: c.occupation || '未设置',` 之后新增一行：
```js
        roleLabel: codes.getLabel(codes.ROLES, c.character) || '未设置',
```

在文件末尾方法区（第 48 行 `onVoice()` 之后）新增：
```js
  onRole() { wx.navigateTo({ url: '/pages/companion/change-role/change-role' }); }
```

- [ ] **Step 2: profile.wxml 增加角色行（置于职业行之前，遵循「禁 emoji」用 PNG 图标）**

在 `profile.wxml` 第 6 行（`</view>` 头部结束）之后、第 7 行职业行之前插入：
```html
  <view class="pf-row" bindtap="onRole">
    <image class="pf-ic-img" src="/images/role{{darkMode ? '-dark' : ''}}.png" mode="aspectFit" />
    <view class="pf-meta"><view class="pf-lb">角色</view><view class="pf-val">{{roleLabel}}</view></view>
    <view class="pf-go">更换 ›</view>
  </view>
```

并在 `profile.wxss` 增加（若该 class 不存在）图标样式：
```css
.pf-ic-img { width:48rpx; height:48rpx; margin-right:20rpx }
```
> 注：现有 `pf-ic` 为 emoji 文本容器；新行用 PNG 图标，遵循小程序「禁止 emoji」规范。若 `profile.wxss` 中 `.pf-row` 已含 `align-items:center`，图标会自然垂直居中。

- [ ] **Step 3: 编译校验**

微信开发者工具编译，Expected: 资料页出现「角色」行，显示当前角色中文标签。

---

## Task 9: 道具图标资源

**Files:**
- Create: `main/miniprogram/images/role.png`
- Create: `main/miniprogram/images/role-dark.png`

- [ ] **Step 1: 提供图标资源**

准备 `role.png`（浅色态）与 `role-dark.png`（深色态），尺寸建议 96×96 或与现有 `occupation.png`/`soul.png` 一致。
- 若暂无设计稿：可临时复制现有 `images/occupation.png` → `role.png`（及 dark 版）占位，确保背包卡片与资料行图标不空白；后续替换正式素材。

- [ ] **Step 2: 校验资源被引用无误**

微信开发者工具编译，Expected: 背包「角色变更」卡片图标、资料页角色行图标正常显示（浅/深态切换正常）。

---

## Task 10: 端到端验证

- [ ] **Step 1: 后端全量测试 + 启动**

Run: `cd main/manager-api && mvn -q test -DskipTests=false`
Expected: BUILD SUCCESS，全量测试通过。

启动后端（应用 Liquibase 迁移）：参考项目 `/start-api` 技能或 `mvn spring-boot:run`。

- [ ] **Step 2: 校验 SKU 落库**

查询 DB（egg_database / OceanBase）：
```sql
SELECT sku_code, sku_name, category, price_fen, sort FROM ai_item_sku WHERE sku_code = 'role_change';
```
Expected: 一行——`role_change | 角色变更 | consumable_change | 9900 | 14`。

- [ ] **Step 3: 小程序端到端流程**

在微信开发者工具中：
1. 背包页购买「角色变更」券（mock 支付）→ 库存 +1。
2. 资料页点「角色 ›」→ 进入更换角色页，显示当前角色。
3. 选一个不同角色 → 确认 → 「命运已重塑」成功态。
4. 返回聊天页 → 自动断开连接 → 手动「召唤」重连。
5. 验证：对话中女友人设为新角色；DB `ai_companion.character` 已更新，`ai_agent.system_prompt` 中 `{{character}}` 已替换为新角色标签；`companion.avatar`/`default_image` 已切到新角色素材。

- [ ] **Step 4: 无券拦截验证**

清空 role_change 库存后再次尝试更换角色 → 弹「还没有换角色券」→ 引导去背包；后端返回 10321，character/avatar 不落库。

---

## Self-Review（计划 vs Spec 对照）

- **Spec 5.1 数据层** → Task 1 ✓
- **Spec 5.2 ConsumeBizType / ReshapeVoucherRule / CompanionServiceImpl / agent 同步** → Task 2、3、4 ✓（agent 同步为 Task 4 Step 4 的自动副作用，已在说明中标注）
- **Spec 5.3 companion-codes ROLES** → Task 5 ✓
- **Spec 5.3 change-role 页** → Task 6 ✓
- **Spec 5.3 背包注册** → Task 7 ✓
- **Spec 5.3 资料页入口** → Task 8 ✓
- **Spec 5.3 app.json 注册 + role 图标资源** → Task 7（app.json）+ Task 9（资源）✓
- **Spec 5.4 Python 零改动** → 无任务（确认）✓
- **Spec 6 测试** → Task 3（ReshapeVoucherRuleTest）✓；服务层集成测试标注为可选 ✓
- **决策（¥99 / 头像联动 / 最小提示词）** → Task 1（9900）、Task 6（avatar+defaultImage 随请求）、Task 3/4（仅 {{character}} 标签，无新人设文本）✓

**Placeholder 扫描**：无 TBD/TODO；所有代码步骤含完整代码。✓
**类型一致**：`ROLE_CHANGE` 常量、`after(character,...)` 签名、`characterChanged` 标志、`codes.ROLES` 在所有任务中命名一致。✓
