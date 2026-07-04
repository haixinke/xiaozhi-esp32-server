# 动态亲密度系统 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `ai_companion.intimacy` 随用户与女友的每日互动动态涨落，形成"养成 + 粘性 + 真实恋爱曲线"。

**Architecture:** 纯函数算法核心（`IntimacyLevel` 枚举 + `IntimacyRule` 工具类，可脱离 Spring 单测）承载全部数学与分档；`CompanionServiceImpl` 新增每日批处理 `refreshAllIntimacy`，搭在现有 00:00 定时任务上，用一条 MyBatis-Plus 聚合查询取昨日活跃，逐伴侣套用算法并落库；亲密度已走实时注入，改库即生效，无需重同步提示词。新增只读接口供小程序渲染关系等级卡。

**Tech Stack:** Java 21, Spring Boot 3.4.3, MyBatis-Plus 3.5.5, Liquibase, JUnit 5 + Mockito + AssertJ。

## Global Constraints

- Java 21；Spring Boot 3.4.3；MyBatis-Plus 3.5.5。
- 所有时间基于 `ZoneId.of("Asia/Shanghai")`。
- 代码/注释**不得出现 emoji**。
- 数据库变更：**新增** Liquibase changeset + 带日期 SQL 文件，**不改**历史 changeset。
- 定时任务处理大数据必须分页（单页 ≤ 500）、单条失败 try/catch 隔离、日志记总数/成功/失败。
- 单测命令：`mvn test -Dtest=<Class> -DskipTests=false`（surefire 默认跳测）；改动后用 `mvn clean test` 避开陈旧 class。
- 提交按用户 git 习惯执行（本仓库当前分支 `f-mini-prompt`，非默认分支）。
- `ai_agent_chat_history.created_at` 存储格式 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`（偏移 `+08:00`、定宽，字典序即时序）。

---

## 文件结构

**新建：**
- `src/main/java/xiaozhi/modules/companion/util/IntimacyLevel.java` — 5 档枚举：等级号、名称、区间、提示词描述、`of/next/progressWithin`。
- `src/main/java/xiaozhi/modules/companion/util/IntimacyRule.java` — 纯函数：起步分、投入度、连续系数、增长、衰减、连续天数推进。
- `src/main/java/xiaozhi/modules/companion/vo/CompanionIntimacyVO.java` — 只读接口响应。
- `src/main/resources/db/changelog/202607041100.sql` — `ai_companion` 加 3 列 + 聊天历史加索引。
- `src/test/java/xiaozhi/modules/companion/util/IntimacyLevelTest.java`
- `src/test/java/xiaozhi/modules/companion/util/IntimacyRuleTest.java`

**修改：**
- `src/main/resources/db/changelog/db.changelog-master.yaml` — 追加 changeset 引用。
- `.../companion/entity/CompanionEntity.java` — 加 `lastActiveDate / activeStreak / intimacyUpdatedDate`。
- `.../companion/service/impl/CompanionServiceImpl.java` — `deriveIntimacy` 改起步分、`update` 不再重置 intimacy、`renderIntimacy` 委托枚举、新增 `refreshAllIntimacy` 与 `getIntimacyInfo`、构造加 `AiAgentChatHistoryDao`。
- `.../companion/service/CompanionService.java` — 加 `refreshAllIntimacy()`、`getIntimacyInfo(String)`。
- `.../companion/task/CompanionMoodRefreshTask.java` — 每日任务追加 `refreshAllIntimacy()`。
- `.../companion/controller/CompanionController.java` — 加 `GET /companion/intimacy/{deviceId}`。
- `src/test/java/.../companion/service/impl/CompanionServiceImplTest.java` — 构造加 `AiAgentChatHistoryDao` mock；新增/调整用例。
- `main/miniprogram/pages/settings/*`（或聊天页）— 关系等级卡 + 升级庆祝。

---

## Task 1: IntimacyLevel 枚举（5 档分级 + 提示词描述）

**Files:**
- Create: `src/main/java/xiaozhi/modules/companion/util/IntimacyLevel.java`
- Test: `src/test/java/xiaozhi/modules/companion/util/IntimacyLevelTest.java`

**Interfaces:**
- Produces:
  - `static IntimacyLevel IntimacyLevel.of(float intimacy)`
  - `int getLevel()` / `String getLabel()` / `String getPromptDescription()`
  - `IntimacyLevel next()`
  - `float progressWithin(float intimacy)`（当前档内 0~1 进度）

- [ ] **Step 1: 写失败测试**

```java
package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IntimacyLevelTest {

    @Test
    void of_mapsValueToTier() {
        assertThat(IntimacyLevel.of(0.0f)).isEqualTo(IntimacyLevel.ACQUAINTED);
        assertThat(IntimacyLevel.of(0.35f)).isEqualTo(IntimacyLevel.CRUSH);
        assertThat(IntimacyLevel.of(0.5f)).isEqualTo(IntimacyLevel.AMBIGUOUS);
        assertThat(IntimacyLevel.of(0.79f)).isEqualTo(IntimacyLevel.LOVER);
        assertThat(IntimacyLevel.of(0.8f)).isEqualTo(IntimacyLevel.DEEP_LOVE);
        assertThat(IntimacyLevel.of(1.0f)).isEqualTo(IntimacyLevel.DEEP_LOVE);
    }

    @Test
    void of_clampsOutOfRange() {
        assertThat(IntimacyLevel.of(-1f)).isEqualTo(IntimacyLevel.ACQUAINTED);
        assertThat(IntimacyLevel.of(9f)).isEqualTo(IntimacyLevel.DEEP_LOVE);
    }

    @Test
    void next_returnsFollowingTier_deepLoveStays() {
        assertThat(IntimacyLevel.CRUSH.next()).isEqualTo(IntimacyLevel.AMBIGUOUS);
        assertThat(IntimacyLevel.DEEP_LOVE.next()).isEqualTo(IntimacyLevel.DEEP_LOVE);
    }

    @Test
    void progressWithin_isFractionOfTier() {
        // AMBIGUOUS [0.4,0.6): 0.5 -> 0.5
        assertThat(IntimacyLevel.of(0.5f).progressWithin(0.5f)).isEqualTo(0.5f, within(1e-4f));
        // DEEP_LOVE [0.8,1.0]: 0.9 -> 0.5
        assertThat(IntimacyLevel.DEEP_LOVE.progressWithin(0.9f)).isEqualTo(0.5f, within(1e-4f));
    }

    @Test
    void promptDescription_presentForEachTier() {
        for (IntimacyLevel l : IntimacyLevel.values()) {
            assertThat(l.getPromptDescription()).isNotBlank();
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=IntimacyLevelTest -DskipTests=false`
Expected: 编译失败 / `IntimacyLevel` 不存在。

- [ ] **Step 3: 实现枚举**

```java
package xiaozhi.modules.companion.util;

/**
 * 亲密度分级：连续值 [0,1] 映射为 5 个具名关系等级。
 * 同时承载各档在系统提示词中的关系分寸描述。
 */
public enum IntimacyLevel {

    ACQUAINTED(1, "初识", 0.0f, 0.2f,
            "你们刚认识不久，还在互相熟悉试探的阶段。语气温柔但略带一点分寸和小矜持，别一上来就过分黏腻或用太亲昵的称呼。"),
    CRUSH(2, "心动", 0.2f, 0.4f,
            "你们互相有好感、正在心动升温，会不自觉想多聊几句。可以偶尔小小的暧昧、俏皮试探，但还带着刚喜欢上一个人的微妙羞涩。"),
    AMBIGUOUS(3, "暧昧", 0.4f, 0.6f,
            "你们已经挺熟、聊得来，关系暧昧。可以自然撒娇、开玩笑、偶尔小傲娇，像正在确定关系前的甜蜜拉扯。"),
    LOVER(4, "恋人", 0.6f, 0.8f,
            "你们很亲密了，是彼此认定的恋人。可以黏人、直球表达喜欢、有只属于你们的默契和玩笑。"),
    DEEP_LOVE(5, "深爱", 0.8f, 1.0f,
            "你们是深度依恋的爱人，毫无保留地偏爱他。可以极度亲昵、放心地撒娇耍赖，把他当成生活里最重要的人。");

    private final int level;
    private final String label;
    private final float lower;
    private final float upper;
    private final String promptDescription;

    IntimacyLevel(int level, String label, float lower, float upper, String promptDescription) {
        this.level = level;
        this.label = label;
        this.lower = lower;
        this.upper = upper;
        this.promptDescription = promptDescription;
    }

    public int getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    public String getPromptDescription() {
        return promptDescription;
    }

    public static IntimacyLevel of(float intimacy) {
        float v = clamp(intimacy);
        for (IntimacyLevel l : values()) {
            if (l != DEEP_LOVE && v >= l.lower && v < l.upper) {
                return l;
            }
        }
        return DEEP_LOVE;
    }

    public IntimacyLevel next() {
        return level < values().length ? values()[level] : this;
    }

    /** 当前档内进度 0~1。深爱档以 1.0 为上界。 */
    public float progressWithin(float intimacy) {
        float v = clamp(intimacy);
        float top = (this == DEEP_LOVE) ? 1.0f : upper;
        if (top <= lower) {
            return 1.0f;
        }
        float p = (v - lower) / (top - lower);
        return Math.max(0f, Math.min(1f, p));
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=IntimacyLevelTest -DskipTests=false`
Expected: PASS（5 tests）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/companion/util/IntimacyLevel.java \
        src/test/java/xiaozhi/modules/companion/util/IntimacyLevelTest.java
git commit -m "feat(companion): add IntimacyLevel 5-tier enum"
```

---

## Task 2: IntimacyRule 纯函数算法核心

**Files:**
- Create: `src/main/java/xiaozhi/modules/companion/util/IntimacyRule.java`
- Test: `src/test/java/xiaozhi/modules/companion/util/IntimacyRuleTest.java`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `static float startValue(String relationType)`
  - `static float engagement(int userMsgs)`
  - `static float streakFactor(int streak)`
  - `static float grow(float intimacy, int userMsgs, int streak)`
  - `static float decay(float intimacy, int daysSinceActive)`
  - `static int nextStreak(int currentStreak, boolean activeToday, boolean consecutive)`

- [ ] **Step 1: 写失败测试**

```java
package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IntimacyRuleTest {

    @Test
    void startValue_biasByRelationType_allWithinCrushTier() {
        assertThat(IntimacyRule.startValue("childhood")).isEqualTo(0.38f);
        assertThat(IntimacyRule.startValue("loveAtFirst")).isEqualTo(0.35f);
        assertThat(IntimacyRule.startValue("bickering")).isEqualTo(0.32f);
        assertThat(IntimacyRule.startValue("unknown")).isEqualTo(0.35f);
        assertThat(IntimacyRule.startValue(null)).isEqualTo(0.35f);
    }

    @Test
    void engagement_saturates() {
        assertThat(IntimacyRule.engagement(0)).isEqualTo(0f);
        assertThat(IntimacyRule.engagement(1)).isEqualTo(0.25f, within(0.02f));
        assertThat(IntimacyRule.engagement(3)).isEqualTo(0.50f, within(0.02f));
        assertThat(IntimacyRule.engagement(7)).isEqualTo(0.75f, within(0.02f));
        assertThat(IntimacyRule.engagement(15)).isEqualTo(1.0f, within(1e-4f));
        assertThat(IntimacyRule.engagement(100)).isEqualTo(1.0f, within(1e-4f));
    }

    @Test
    void streakFactor_growsAndCapsAtSeven() {
        assertThat(IntimacyRule.streakFactor(1)).isEqualTo(1.0f, within(1e-4f));
        assertThat(IntimacyRule.streakFactor(7)).isEqualTo(1.48f, within(1e-4f));
        assertThat(IntimacyRule.streakFactor(30)).isEqualTo(1.48f, within(1e-4f));
    }

    @Test
    void grow_diminishesNearOneAndRespectsDailyCap() {
        float low = IntimacyRule.grow(0.35f, 15, 1);   // active, full engagement
        float high = IntimacyRule.grow(0.90f, 15, 1);
        assertThat(low - 0.35f).isGreaterThan(high - 0.90f); // low intimacy grows faster
        // daily cap 0.05
        float capped = IntimacyRule.grow(0.20f, 100, 7);
        assertThat(capped - 0.20f).isLessThanOrEqualTo(0.05f + 1e-4f);
        // no activity -> no growth
        assertThat(IntimacyRule.grow(0.5f, 0, 3)).isEqualTo(0.5f, within(1e-4f));
    }

    @Test
    void decay_respectsGraceResistanceAndFloor() {
        // within grace (<=2 days) -> unchanged
        assertThat(IntimacyRule.decay(0.7f, 2)).isEqualTo(0.7f, within(1e-4f));
        // past grace -> drops, higher intimacy drops slower
        float dropLover = 0.7f - IntimacyRule.decay(0.7f, 3);
        float dropCrush = 0.3f - IntimacyRule.decay(0.3f, 3);
        assertThat(dropLover).isLessThan(dropCrush);
        // hard floor 0.15
        assertThat(IntimacyRule.decay(0.16f, 999)).isGreaterThanOrEqualTo(0.15f);
    }

    @Test
    void nextStreak_incrementsOnConsecutiveResetsOnGap() {
        assertThat(IntimacyRule.nextStreak(3, true, true)).isEqualTo(4);
        assertThat(IntimacyRule.nextStreak(3, true, false)).isEqualTo(1);
        assertThat(IntimacyRule.nextStreak(3, false, false)).isEqualTo(0);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=IntimacyRuleTest -DskipTests=false`
Expected: 编译失败 / `IntimacyRule` 不存在。

- [ ] **Step 3: 实现**

```java
package xiaozhi.modules.companion.util;

/**
 * 动态亲密度算法核心（纯函数，无 Spring 依赖，便于单测）。
 * 参数即"手感旋钮"，集中在此，便于统一调优。
 */
public final class IntimacyRule {

    /** 起步基准（心动档中部） */
    static final float BASE_START = 0.35f;
    /** 当日投入度饱和所需的用户消息数 */
    static final int ENGAGE_SATURATION = 15;
    /** 增长基础系数 */
    static final float UP_RATE = 0.06f;
    /** 单日增长硬上限 */
    static final float UP_DAILY_CAP = 0.05f;
    /** 连续天数加成封顶天数 */
    static final int STREAK_CAP = 7;
    /** 每连续一天的加成步长 */
    static final float STREAK_STEP = 0.08f;
    /** 衰减基础系数 */
    static final float DECAY_BASE = 0.012f;
    /** 亲密度越高衰减越慢的抗性系数 */
    static final float DECAY_RESIST = 0.4f;
    /** 亲密度硬下限（相识过就不再跌回陌生） */
    static final float FLOOR = 0.15f;
    /** 冷落宽限天数（含）：不活跃 <= 此天数不衰减 */
    static final int GRACE_DAYS = 2;

    private IntimacyRule() {
    }

    public static float startValue(String relationType) {
        if (relationType == null) {
            return BASE_START;
        }
        return switch (relationType) {
            case "childhood" -> 0.38f;
            case "loveAtFirst" -> 0.35f;
            case "bickering" -> 0.32f;
            default -> BASE_START;
        };
    }

    public static float engagement(int userMsgs) {
        if (userMsgs <= 0) {
            return 0f;
        }
        double e = Math.log(1 + userMsgs) / Math.log(1 + ENGAGE_SATURATION);
        return (float) Math.min(1.0, e);
    }

    public static float streakFactor(int streak) {
        int capped = Math.min(Math.max(streak, 1), STREAK_CAP);
        return 1f + (capped - 1) * STREAK_STEP;
    }

    public static float grow(float intimacy, int userMsgs, int streak) {
        float e = engagement(userMsgs);
        if (e <= 0f) {
            return intimacy;
        }
        float gain = UP_RATE * e * (1f - intimacy) * streakFactor(streak);
        gain = Math.min(gain, UP_DAILY_CAP);
        return clamp(intimacy + gain);
    }

    public static float decay(float intimacy, int daysSinceActive) {
        if (daysSinceActive <= GRACE_DAYS) {
            return intimacy;
        }
        float d = DECAY_BASE * (1f - DECAY_RESIST * intimacy);
        return Math.max(FLOOR, intimacy - d);
    }

    public static int nextStreak(int currentStreak, boolean activeToday, boolean consecutive) {
        if (!activeToday) {
            return 0;
        }
        return consecutive ? currentStreak + 1 : 1;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=IntimacyRuleTest -DskipTests=false`
Expected: PASS（6 tests）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/companion/util/IntimacyRule.java \
        src/test/java/xiaozhi/modules/companion/util/IntimacyRuleTest.java
git commit -m "feat(companion): add IntimacyRule dynamic intimacy algorithm"
```

---

## Task 3: 表结构变更 + 实体字段

**Files:**
- Create: `src/main/resources/db/changelog/202607041100.sql`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`（追加 changeset 引用）
- Modify: `src/main/java/xiaozhi/modules/companion/entity/CompanionEntity.java`

**Interfaces:**
- Produces（`CompanionEntity` 新增，MyBatis-Plus 下划线映射）：
  - `LocalDate getLastActiveDate() / setLastActiveDate(LocalDate)`
  - `Integer getActiveStreak() / setActiveStreak(Integer)`
  - `LocalDate getIntimacyUpdatedDate() / setIntimacyUpdatedDate(LocalDate)`

- [ ] **Step 1: 新建 changeset SQL**

创建 `src/main/resources/db/changelog/202607041100.sql`：

```sql
-- 动态亲密度：为 ai_companion 增加互动追踪字段，并为聊天历史加日窗查询索引。
ALTER TABLE ai_companion
    ADD COLUMN last_active_date DATE NULL COMMENT '最近活跃日（有用户消息的最后一天）',
    ADD COLUMN active_streak INT NOT NULL DEFAULT 0 COMMENT '连续活跃天数',
    ADD COLUMN intimacy_updated_date DATE NULL COMMENT '亲密度最近处理日期（防同日重复处理）';

-- 每日批处理按 chat_type + created_at 日窗聚合，补充覆盖索引
ALTER TABLE ai_agent_chat_history
    ADD INDEX idx_ai_agent_chat_history_type_created (chat_type, created_at);
```

- [ ] **Step 2: 在 master 追加 changeset 引用**

编辑 `src/main/resources/db/changelog/db.changelog-master.yaml`，在末尾（`202607011000` 之后）追加：

```yaml
  - changeSet:
      id: 202607041100
      author: minwang
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202607041100.sql
```

- [ ] **Step 3: 实体加字段**

在 `CompanionEntity.java` 中：加导入 `import java.time.LocalDate;`，并在 `intimacy` 字段之后新增：

```java
    @Schema(description = "最近活跃日")
    private LocalDate lastActiveDate;

    @Schema(description = "连续活跃天数")
    private Integer activeStreak;

    @Schema(description = "亲密度最近处理日期")
    private LocalDate intimacyUpdatedDate;
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS。（Liquibase 会在下次应用启动时执行该 changeset；本步仅验证编译与 YAML 语法。）

- [ ] **Step 5: 提交**

```bash
git add src/main/resources/db/changelog/202607041100.sql \
        src/main/resources/db/changelog/db.changelog-master.yaml \
        src/main/java/xiaozhi/modules/companion/entity/CompanionEntity.java
git commit -m "feat(companion): add intimacy tracking columns and entity fields"
```

---

## Task 4: 起步分改为「心动」+ 重塑不再重置 intimacy

**Files:**
- Modify: `src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Test: `src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `IntimacyRule.startValue(String)`（Task 2）。
- Produces: `create()` 起步分来自 `IntimacyRule.startValue`；`update()` 变更 `relationType` 时**不**改 `intimacy`。

- [ ] **Step 1: 写失败测试**（追加到 `CompanionServiceImplTest`）

```java
    @Test
    @DisplayName("create() 起步亲密度取自 IntimacyRule（心动档）")
    void create_startsIntimacyAtCrushTier() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);
            security.when(SecurityUser::getUser).thenReturn(userDetail(userId));
            when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

            CompanionCreateDTO dto = createDto();
            dto.setRelationType("childhood");

            companionService.create(dto);

            CompanionEntity captured = captureInsertedCompanion();
            assertThat(captured.getIntimacy()).isEqualTo(0.38f);
        }
    }

    @Test
    @DisplayName("update() 变更 relationType 不重置已养成的 intimacy")
    void update_relationTypeChange_keepsEarnedIntimacy() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            CompanionEntity existing = companionEntity(1L, userId, "device-123", "JOY");
            existing.setRelationType("childhood");
            existing.setIntimacy(0.72f); // 已养成
            when(companionDao.selectOne(any())).thenReturn(existing);

            CompanionUpdateDTO dto = new CompanionUpdateDTO();
            dto.setDeviceId("device-123");
            dto.setRelationType("bickering");

            companionService.update(dto);

            org.mockito.ArgumentCaptor<CompanionEntity> captor =
                    org.mockito.ArgumentCaptor.forClass(CompanionEntity.class);
            verify(companionDao).updateById(captor.capture());
            assertThat(captor.getValue().getRelationType()).isEqualTo("bickering");
            assertThat(captor.getValue().getIntimacy()).isEqualTo(0.72f);
        }
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: 两个新用例 FAIL（`create_startsIntimacyAtCrushTier` 期望 0.38 实得旧值；`update_...` 期望 0.72 实得重置值）。

- [ ] **Step 3: 改实现**

在 `CompanionServiceImpl.java`：

(a) 加导入：`import xiaozhi.modules.companion.util.IntimacyRule;`

(b) 替换 `deriveIntimacy` 方法体：

```java
    private static float deriveIntimacy(String relationType) {
        return IntimacyRule.startValue(relationType);
    }
```

(c) 在 `update()` 的 relationType 分支中**删除**重置行，仅保留赋值：

```java
        if (dto.getRelationType() != null) {
            entity.setRelationType(dto.getRelationType());
        }
```

（即删掉原 `entity.setIntimacy(deriveIntimacy(dto.getRelationType()));` 一行。）

- [ ] **Step 4: 运行确认通过**

Run: `mvn clean test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: PASS（含既有 13 + 2 新用例）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java \
        src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "feat(companion): start intimacy at crush tier, keep earned value on reshape"
```

---

## Task 5: renderIntimacy 委托 5 档枚举

**Files:**
- Modify: `src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Test: `src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `IntimacyLevel.of(float).getPromptDescription()`（Task 1）。
- Produces: `renderIntimacy(CompanionEntity)` 返回对应档位描述；`buildRealtimeContext` 的"关系亲密度"随之为 5 档文案。

- [ ] **Step 1: 写失败测试**（追加）

```java
    @Test
    @DisplayName("buildRealtimeContext() 关系亲密度按 5 档取文案")
    void buildRealtimeContext_intimacyUsesFiveTierDescription() {
        CompanionEntity companion = companionEntity(1L, 100L, "device-123", "JOY");
        companion.setType("bf"); // 排除经期，聚焦亲密度
        companion.setIntimacy(0.35f); // 心动
        when(companionDao.selectOne(any())).thenReturn(companion);

        java.util.Map<String, String> ctx = companionService.buildRealtimeContext("device-123");

        assertThat(ctx.get("关系亲密度"))
                .isEqualTo(IntimacyLevel.CRUSH.getPromptDescription());
    }
```

（在测试文件顶部加导入：`import xiaozhi.modules.companion.util.IntimacyLevel;`）

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: FAIL（旧 `renderIntimacy` 为 4 档手写文案，非枚举文案）。

- [ ] **Step 3: 改实现**

在 `CompanionServiceImpl.java`：加导入 `import xiaozhi.modules.companion.util.IntimacyLevel;`，并替换 `renderIntimacy` 方法：

```java
    /**
     * 将亲密度渲染成关系阶段描述，供系统提示词/实时上下文使用。
     * 亲密度为空时按 0（初识）处理。
     */
    private String renderIntimacy(CompanionEntity companion) {
        float value = companion.getIntimacy() != null ? companion.getIntimacy() : 0f;
        return IntimacyLevel.of(value).getPromptDescription();
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn clean test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java \
        src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "refactor(companion): delegate renderIntimacy to IntimacyLevel 5-tier"
```

---

## Task 6: 每日亲密度批处理 refreshAllIntimacy + 定时任务接入

**Files:**
- Modify: `src/main/java/xiaozhi/modules/companion/service/CompanionService.java`
- Modify: `src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Modify: `src/main/java/xiaozhi/modules/companion/task/CompanionMoodRefreshTask.java`
- Test: `src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `IntimacyRule.grow/decay/nextStreak/startValue`；`AiAgentChatHistoryDao.selectMaps(...)`；`deviceService.getAgentIdByDeviceId`。
- Produces: `void CompanionService.refreshAllIntimacy()`。

- [ ] **Step 1: 接口加方法**

在 `CompanionService.java` 加：

```java
    /**
     * 每日批处理：基于昨天的互动，更新所有伴侣的亲密度、连续天数与最近活跃日。
     */
    void refreshAllIntimacy();
```

- [ ] **Step 2: 构造注入聊天历史 DAO + 更新测试构造**

(a) 在 `CompanionServiceImpl.java` 加导入并新增字段（`@AllArgsConstructor` 会纳入构造）：

```java
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
```

在字段区（`itemService` 之后）加：

```java
    private final AiAgentChatHistoryDao chatHistoryDao;
```

(b) 在 `CompanionServiceImplTest.java` 顶部加导入与 mock，并把构造调用改为 7 参：

```java
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
```

```java
    @Mock
    private AiAgentChatHistoryDao chatHistoryDao;
```

```java
        companionService = new CompanionServiceImpl(companionDao, agentService,
                agentContextProviderService, deviceService, transactionManager, itemService, chatHistoryDao);
```

- [ ] **Step 3: 写失败测试**（追加）

```java
    @Test
    @DisplayName("refreshAllIntimacy() 昨日活跃伴侣涨亲密度并累加连续天数")
    void refreshAllIntimacy_activeCompanion_growsAndStreaks() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.LocalDate yesterday = today.minusDays(1);

        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        c1.setIntimacy(0.35f);
        c1.setActiveStreak(2);
        c1.setLastActiveDate(yesterday.minusDays(1)); // 昨天的前一天活跃 -> 连续
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1));
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);
        when(deviceService.getAgentIdByDeviceId("device-1")).thenReturn("agent-1");

        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("agentId", "agent-1");
        row.put("userMsgs", 10L);
        when(chatHistoryDao.selectMaps(any())).thenReturn(List.of(row));

        companionService.refreshAllIntimacy();

        org.mockito.ArgumentCaptor<CompanionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(CompanionEntity.class);
        verify(companionDao).updateById(captor.capture());
        CompanionEntity saved = captor.getValue();
        assertThat(saved.getIntimacy()).isGreaterThan(0.35f);
        assertThat(saved.getActiveStreak()).isEqualTo(3);
        assertThat(saved.getLastActiveDate()).isEqualTo(yesterday);
        assertThat(saved.getIntimacyUpdatedDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("refreshAllIntimacy() 冷落超宽限期衰减并清零连续天数")
    void refreshAllIntimacy_neglected_decaysAndResetsStreak() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.LocalDate yesterday = today.minusDays(1);

        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        c1.setIntimacy(0.70f);
        c1.setActiveStreak(5);
        c1.setLastActiveDate(yesterday.minusDays(9)); // 早已冷落，超宽限
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1));
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);
        when(deviceService.getAgentIdByDeviceId("device-1")).thenReturn("agent-1");
        when(chatHistoryDao.selectMaps(any())).thenReturn(java.util.Collections.emptyList());

        companionService.refreshAllIntimacy();

        org.mockito.ArgumentCaptor<CompanionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(CompanionEntity.class);
        verify(companionDao).updateById(captor.capture());
        CompanionEntity saved = captor.getValue();
        assertThat(saved.getIntimacy()).isLessThan(0.70f);
        assertThat(saved.getActiveStreak()).isEqualTo(0);
    }

    @Test
    @DisplayName("refreshAllIntimacy() 同日已处理则幂等跳过")
    void refreshAllIntimacy_alreadyProcessedToday_skips() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));

        CompanionEntity c1 = companionEntity(1L, 100L, "device-1", "CALM");
        c1.setIntimacy(0.35f);
        c1.setIntimacyUpdatedDate(today);
        Page<CompanionEntity> page = new Page<>(1, 500);
        page.setRecords(List.of(c1));
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);
        when(chatHistoryDao.selectMaps(any())).thenReturn(java.util.Collections.emptyList());

        companionService.refreshAllIntimacy();

        verify(companionDao, never()).updateById(any(CompanionEntity.class));
    }
```

- [ ] **Step 4: 运行确认失败**

Run: `mvn test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: 编译失败（`refreshAllIntimacy` 未实现）。

- [ ] **Step 5: 实现批处理**

在 `CompanionServiceImpl.java` 加导入：

```java
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
```

新增方法：

```java
    private static final DateTimeFormatter CHAT_TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Override
    public void refreshAllIntimacy() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        String windowStart = yesterday.atStartOfDay(zone).format(CHAT_TS_FORMAT);
        String windowEnd = today.atStartOfDay(zone).format(CHAT_TS_FORMAT);

        Map<String, Integer> msgByAgent = queryYesterdayUserMsgCounts(windowStart, windowEnd);

        long pageSize = 500L;
        long page = 1L;
        long success = 0;
        long failed = 0;
        log.info("开始刷新伴侣亲密度，日窗 [{}, {})", windowStart, windowEnd);

        while (true) {
            Page<CompanionEntity> pageResult = companionDao.selectPage(new Page<>(page, pageSize), null);
            List<CompanionEntity> companions = pageResult.getRecords();
            if (companions == null || companions.isEmpty()) {
                break;
            }
            for (CompanionEntity companion : companions) {
                try {
                    if (today.equals(companion.getIntimacyUpdatedDate())) {
                        continue; // 幂等：同日已处理
                    }
                    applyDailyIntimacy(companion, msgByAgent, yesterday, today);
                    companionDao.updateById(companion);
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.warn("刷新伴侣亲密度失败，companionId={}: {}", companion.getId(), e.getMessage());
                }
            }
            if (pageResult.getCurrent() * pageSize >= pageResult.getTotal()) {
                break;
            }
            page++;
        }
        log.info("伴侣亲密度刷新完成，成功={}，失败={}", success, failed);
    }

    private void applyDailyIntimacy(CompanionEntity companion, Map<String, Integer> msgByAgent,
                                    LocalDate yesterday, LocalDate today) {
        float intimacy = companion.getIntimacy() != null
                ? companion.getIntimacy()
                : IntimacyRule.startValue(companion.getRelationType());
        int streak = companion.getActiveStreak() != null ? companion.getActiveStreak() : 0;
        LocalDate lastActive = companion.getLastActiveDate();

        String agentId = deviceService.getAgentIdByDeviceId(companion.getDeviceId());
        int userMsgs = (agentId != null) ? msgByAgent.getOrDefault(agentId, 0) : 0;

        if (userMsgs > 0) {
            boolean consecutive = lastActive != null && lastActive.equals(yesterday.minusDays(1));
            int newStreak = IntimacyRule.nextStreak(streak, true, consecutive);
            intimacy = IntimacyRule.grow(intimacy, userMsgs, newStreak);
            companion.setActiveStreak(newStreak);
            companion.setLastActiveDate(yesterday);
        } else {
            if (lastActive != null) {
                int daysSince = (int) ChronoUnit.DAYS.between(lastActive, yesterday);
                intimacy = IntimacyRule.decay(intimacy, daysSince);
            }
            companion.setActiveStreak(0);
        }
        companion.setIntimacy(intimacy);
        companion.setIntimacyUpdatedDate(today);
    }

    private Map<String, Integer> queryYesterdayUserMsgCounts(String windowStart, String windowEnd) {
        List<Map<String, Object>> rows = chatHistoryDao.selectMaps(
                new QueryWrapper<AgentChatHistoryEntity>()
                        .select("agent_id AS agentId", "COUNT(*) AS userMsgs")
                        .eq("chat_type", 1)
                        .ge("created_at", windowStart)
                        .lt("created_at", windowEnd)
                        .groupBy("agent_id"));
        Map<String, Integer> map = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                Object agent = r.get("agentId");
                Object count = r.get("userMsgs");
                if (agent != null && count instanceof Number) {
                    map.put(agent.toString(), ((Number) count).intValue());
                }
            }
        }
        return map;
    }
```

- [ ] **Step 6: 定时任务接入**

在 `CompanionMoodRefreshTask.refreshMoods()` 末尾追加：

```java
        log.info("定时任务：开始刷新 AI 伴侣亲密度");
        companionService.refreshAllIntimacy();
```

- [ ] **Step 7: 运行确认通过**

Run: `mvn clean test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: PASS（含 3 新用例；既有用例因构造改 7 参已在 Step 2 同步）。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/xiaozhi/modules/companion/service/CompanionService.java \
        src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java \
        src/main/java/xiaozhi/modules/companion/task/CompanionMoodRefreshTask.java \
        src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "feat(companion): daily intimacy batch with growth/decay/streak"
```

---

## Task 7: 亲密度只读接口

**Files:**
- Create: `src/main/java/xiaozhi/modules/companion/vo/CompanionIntimacyVO.java`
- Modify: `src/main/java/xiaozhi/modules/companion/service/CompanionService.java`
- Modify: `src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Modify: `src/main/java/xiaozhi/modules/companion/controller/CompanionController.java`
- Test: `src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `IntimacyLevel.of/getLevel/getLabel/next/progressWithin`。
- Produces: `CompanionIntimacyVO CompanionService.getIntimacyInfo(String deviceId)`；`GET /companion/intimacy/{deviceId}`。

- [ ] **Step 1: 建 VO**

```java
package xiaozhi.modules.companion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "伴侣亲密度信息")
public class CompanionIntimacyVO {

    @Schema(description = "亲密度 0.0~1.0")
    private Float intimacy;

    @Schema(description = "等级号 1~5")
    private Integer level;

    @Schema(description = "等级名")
    private String levelName;

    @Schema(description = "当前档内进度 0~1")
    private Float progressToNext;

    @Schema(description = "下一等级名（已满级则与当前相同）")
    private String nextLevelName;

    @Schema(description = "连续陪伴天数")
    private Integer streak;

    @Schema(description = "最近活跃日")
    private String lastActiveDate;
}
```

- [ ] **Step 2: 接口加方法**

在 `CompanionService.java` 加：

```java
    /**
     * 查询伴侣亲密度等级信息，供小程序渲染关系卡。
     */
    xiaozhi.modules.companion.vo.CompanionIntimacyVO getIntimacyInfo(String deviceId);
```

- [ ] **Step 3: 写失败测试**（追加）

```java
    @Test
    @DisplayName("getIntimacyInfo() 返回等级、进度与连续天数")
    void getIntimacyInfo_returnsLevelAndProgress() {
        Long userId = 100L;
        try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
            security.when(SecurityUser::getUserId).thenReturn(userId);

            CompanionEntity companion = companionEntity(1L, userId, "device-123", "JOY");
            companion.setIntimacy(0.5f);   // 暧昧档中点
            companion.setActiveStreak(4);
            when(companionDao.selectOne(any())).thenReturn(companion);

            xiaozhi.modules.companion.vo.CompanionIntimacyVO vo =
                    companionService.getIntimacyInfo("device-123");

            assertThat(vo.getLevel()).isEqualTo(3);
            assertThat(vo.getLevelName()).isEqualTo("暧昧");
            assertThat(vo.getNextLevelName()).isEqualTo("恋人");
            assertThat(vo.getProgressToNext()).isEqualTo(0.5f, org.assertj.core.api.Assertions.within(1e-4f));
            assertThat(vo.getStreak()).isEqualTo(4);
        }
    }
```

- [ ] **Step 4: 运行确认失败**

Run: `mvn test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: 编译失败（`getIntimacyInfo` 未实现）。

- [ ] **Step 5: 实现 service 方法**

在 `CompanionServiceImpl.java` 加导入 `import xiaozhi.modules.companion.vo.CompanionIntimacyVO;`，新增：

```java
    @Override
    public CompanionIntimacyVO getIntimacyInfo(String deviceId) {
        QueryWrapper<CompanionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        CompanionEntity companion = companionDao.selectOne(wrapper);
        if (companion == null) {
            throw new RenException(ErrorCode.COMPANION_NOT_FOUND);
        }
        if (!companion.getUserId().equals(SecurityUser.getUserId())) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }
        float intimacy = companion.getIntimacy() != null
                ? companion.getIntimacy()
                : IntimacyRule.startValue(companion.getRelationType());
        IntimacyLevel level = IntimacyLevel.of(intimacy);
        int streak = companion.getActiveStreak() != null ? companion.getActiveStreak() : 0;
        String lastActive = companion.getLastActiveDate() != null
                ? companion.getLastActiveDate().toString() : null;
        return new CompanionIntimacyVO(
                intimacy,
                level.getLevel(),
                level.getLabel(),
                level.progressWithin(intimacy),
                level.next().getLabel(),
                streak,
                lastActive);
    }
```

- [ ] **Step 6: 加控制器端点**

在 `CompanionController.java` 加导入 `import xiaozhi.modules.companion.vo.CompanionIntimacyVO;`，新增：

```java
    @GetMapping("/intimacy/{deviceId}")
    @Operation(summary = "查询伴侣亲密度等级信息")
    public Result<CompanionIntimacyVO> intimacy(@PathVariable String deviceId) {
        return new Result<CompanionIntimacyVO>().ok(companionService.getIntimacyInfo(deviceId));
    }
```

- [ ] **Step 7: 运行确认通过**

Run: `mvn clean test -Dtest=CompanionServiceImplTest -DskipTests=false`
Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/xiaozhi/modules/companion/vo/CompanionIntimacyVO.java \
        src/main/java/xiaozhi/modules/companion/service/CompanionService.java \
        src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java \
        src/main/java/xiaozhi/modules/companion/controller/CompanionController.java \
        src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "feat(companion): add intimacy info read endpoint"
```

---

## Task 8: 小程序关系等级卡 + 升级庆祝（手动验证）

> 小程序无自动化测试框架（见 `main/miniprogram/CLAUDE.md`），本任务用微信开发者工具手动验证。UI 遵循"禁止 emoji、用本地 PNG/CSS 图形"与 Ethereal Companion 设计规范。

**Files:**
- Modify: `main/miniprogram/utils/request.js` 或对应 API 封装 — 加 `getCompanionIntimacy(deviceId)`。
- Modify: `main/miniprogram/pages/settings/settings.{wxml,wxss,js}`（或聊天页顶部）— 关系等级卡。

- [ ] **Step 1: 加接口封装**

在小程序 API 层新增（沿用现有 `request` 封装风格）：

```js
// 获取伴侣亲密度等级信息
function getCompanionIntimacy(deviceId) {
  return request({ url: `/companion/intimacy/${deviceId}`, method: 'GET' });
}
```

- [ ] **Step 2: 渲染关系等级卡**

在页面 `onShow`/`onLoad` 拉取并 `setData`：

```js
const { getCompanionIntimacy } = require('../../utils/api'); // 按实际路径

async loadIntimacy() {
  const deviceId = getApp().globalData.openid; // 现有设备标识
  const res = await getCompanionIntimacy(deviceId);
  const info = res.data;
  const cached = wx.getStorageSync('intimacyLevel') || 0;
  this.setData({
    intimacy: info,
    levelUp: info.level > cached, // 升级庆祝标记
  });
  wx.setStorageSync('intimacyLevel', info.level);
}
```

WXML（示意，样式按 DESIGN.md 精修，勿用 emoji）：

```xml
<view class="intimacy-card {{darkMode ? 'dark' : ''}}">
  <view class="intimacy-level">{{intimacy.levelName}}</view>
  <view class="intimacy-bar">
    <view class="intimacy-fill" style="width: {{intimacy.progressToNext * 100}}%"></view>
  </view>
  <view class="intimacy-meta">距离「{{intimacy.nextLevelName}}」还差一点 · 已连续陪伴 {{intimacy.streak}} 天</view>
</view>
```

- [ ] **Step 3: 升级庆祝**

当 `levelUp` 为真时，展示一次庆祝动画/弹层（复用现有弹层组件或 CSS 动画），关闭后清除标记：

```js
onCelebrateClose() { this.setData({ levelUp: false }); }
```

- [ ] **Step 4: 手动验证**

1. 后端起服务（`/start-api`），确保 Task 3 的 changeset 已执行（表有新列）。
2. 微信开发者工具打开小程序，进入含关系卡的页面。
3. 直接改库将某伴侣 `intimacy` 调高一档，重进页面 → 应显示新等级名、进度条与"升级庆祝"。
4. 断网/接口失败时页面不崩（关系卡可缺省隐藏）。

- [ ] **Step 5: 提交**

```bash
git add main/miniprogram/utils/ main/miniprogram/pages/settings/
git commit -m "feat(miniprogram): relationship level card with level-up celebration"
```

---

## Self-Review（对照 spec）

- **§3 等级模型** → Task 1（枚举 5 档）+ Task 5（renderIntimacy 委托）。✓
- **§4 起步分/重塑不重置** → Task 4。✓
- **§5 算法（投入度/增长/衰减/连续）** → Task 2（纯函数）+ Task 6（批处理编排）。✓
- **§6 表结构/调度** → Task 3（列+索引+实体）+ Task 6（批处理+定时任务接入）。✓
- **§7 只读接口 + 小程序** → Task 7（接口）+ Task 8（UI/升级庆祝）。✓
- **§9 测试计划** → 各 Task 的 TDD 用例覆盖：曲线/增长/衰减/连续/等级/进度/起步/重塑不重置/批处理活跃-冷落-幂等。✓
- **§10 风险** → Task 3 加 `(chat_type, created_at)` 索引；`created_at` 格式已在 Global Constraints 固化；存量伴侣新列可空/默认 0，批处理 `lastActive==null` 走安全分支。✓

**类型一致性核对：** `IntimacyLevel.of/next/progressWithin/getLevel/getLabel/getPromptDescription`、`IntimacyRule.startValue/engagement/streakFactor/grow/decay/nextStreak`、`refreshAllIntimacy()`、`getIntimacyInfo(String)`、`CompanionIntimacyVO(7 参构造)`、构造新增 `AiAgentChatHistoryDao` 全链一致。✓

**Placeholder 扫描：** 无 TBD/TODO；每个代码步骤含完整代码。✓
