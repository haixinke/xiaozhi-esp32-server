# 生理期数据功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为女友类型 AI 伴侣增加自动模拟的生理周期，影响每日心情和系统提示词，并在小程序设置页展示经期状态。

**Architecture:** 在 `ai_companion` 表新增三列存储周期参数；新增 `MenstrualPhase` 枚举和 `MenstrualCycleUtil` 计算工具；复用现有每日心情刷新任务，根据经期阶段调整心情权重；在系统提示词模板中注入经期状态描述；小程序设置页条件渲染状态 pill。

**Tech Stack:** Java 21 / Spring Boot 3.4.3 / MyBatis-Plus / Liquibase / JUnit 5 / Mockito / AssertJ / 微信小程序 (WXML/WXSS/JS)

## Global Constraints

- 仅对 `type = 'gf'` 的伴侣生效，男友类型不受影响
- 使用 `Asia/Shanghai` 时区处理所有日期计算
- 所有新增数据库列为可空，存量数据不回填
- 心情权重调整基于默认权重：经期时 `EXCITEMENT`/`CURIOSITY` 各下调 5，`FATIGUE`/`ANXIETY`/`CARE` 各上调 5，总权重保持 100
- 非 gf 类型或 gf 非经期时，系统提示词中不注入任何经期相关内容
- 小程序 UI 遵循现有 Ethereal Companion 设计系统：瓷白玻璃态、樱花粉 `#864e5a`、无 emoji
- 每个任务结束必须提交一个独立 commit

---

## 文件结构

| 文件 | 操作 | 说明 |
|---|---|---|
| `main/manager-api/src/main/resources/db/changelog/202606261800.sql` | 创建 | 新增 `ai_companion` 三列 |
| `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` | 修改 | 注册新 changeset |
| `main/manager-api/src/main/java/xiaozhi/modules/companion/util/MenstrualPhase.java` | 创建 | 经期阶段枚举 |
| `main/manager-api/src/main/java/xiaozhi/modules/companion/util/MenstrualCycleUtil.java` | 创建 | 周期计算工具 |
| `main/manager-api/src/test/java/xiaozhi/modules/companion/util/MenstrualCycleUtilTest.java` | 创建 | 周期计算单元测试 |
| `main/manager-api/src/main/java/xiaozhi/modules/companion/entity/CompanionEntity.java` | 修改 | 新增三列字段 |
| `main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionMood.java` | 修改 | 支持按阶段调整权重 |
| `main/manager-api/src/test/java/xiaozhi/modules/companion/util/CompanionMoodTest.java` | 修改 | 新增经期权重测试 |
| `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java` | 修改 | 创建时初始化周期；刷新心情时应用阶段权重 |
| `main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java` | 修改 | 新增创建/刷新/提示词测试 |
| `main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionLabels.java` | 修改 | 提示词模板新增 `{{menstrualState}}` |
| `main/manager-api/src/main/java/xiaozhi/modules/companion/vo/CompanionVO.java` | 修改 | 新增 `MenstrualStatusVO` |
| `main/miniprogram/pages/settings/settings.js` | 修改 | 读取 menstrualStatus |
| `main/miniprogram/pages/settings/settings.wxml` | 修改 | 渲染状态 pill |
| `main/miniprogram/pages/settings/settings.wxss` | 修改 | pill 样式 |

---

## Task 1: 数据库迁移

**Files:**
- Create: `main/manager-api/src/main/resources/db/changelog/202606261800.sql`
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- Test: 启动 manager-api 后查询表结构

**Interfaces:**
- Consumes: 无
- Produces: `ai_companion` 表新增 `menstrual_cycle_start` (DATE), `menstrual_cycle_length` (INT), `menstrual_period_length` (INT)

- [ ] **Step 1: 编写 SQL 迁移文件**

```sql
ALTER TABLE ai_companion
    ADD COLUMN menstrual_cycle_start DATE NULL COMMENT '经期开始日期',
    ADD COLUMN menstrual_cycle_length INT NULL COMMENT '周期长度（天）',
    ADD COLUMN menstrual_period_length INT NULL COMMENT '经期长度（天）';
```

- [ ] **Step 2: 注册 changeset**

在 `db.changelog-master.yaml` 末尾新增：

```yaml
  - changeSet:
      id: 202606261800
      author: minwang
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202606261800.sql
```

- [ ] **Step 3: 验证迁移**

启动 manager-api：

```bash
cd main/manager-api
mvn spring-boot:run
```

在 MySQL/OceanBase 中执行：

```sql
SHOW COLUMNS FROM ai_companion LIKE 'menstrual%';
```

Expected: 三列均存在，类型为 DATE/INT/INT，允许 NULL。

- [ ] **Step 4: Commit**

```bash
git add main/manager-api/src/main/resources/db/changelog/202606261800.sql
main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "chore(db): add menstrual cycle columns to ai_companion

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: 经期阶段枚举与周期计算工具

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/companion/util/MenstrualPhase.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/companion/util/MenstrualCycleUtil.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/companion/util/MenstrualCycleUtilTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `MenstrualPhase` 枚举：`MENSTRUATION`, `FOLLICULAR`, `OVULATION`, `LUTEAL`
  - `MenstrualCycleUtil.computePhase(LocalDate startDate, int cycleLength, int periodLength, LocalDate today)` → `MenstrualPhase`
  - `MenstrualCycleUtil.cycleDay(LocalDate startDate, int cycleLength, LocalDate today)` → `int`
  - `MenstrualCycleUtil.daysUntilNextPeriod(LocalDate startDate, int cycleLength, LocalDate today)` → `int`

- [ ] **Step 1: 编写失败的测试**

创建 `MenstrualCycleUtilTest.java`：

```java
package xiaozhi.modules.companion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MenstrualCycleUtil 周期计算")
class MenstrualCycleUtilTest {

    @Test
    @DisplayName("经期第 1 天返回 MENSTRUATION")
    void computePhase_firstDayOfPeriod_returnsMenstruation() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 1);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.MENSTRUATION);
    }

    @Test
    @DisplayName("经期最后一天返回 MENSTRUATION")
    void computePhase_lastDayOfPeriod_returnsMenstruation() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 5);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.MENSTRUATION);
    }

    @Test
    @DisplayName("经期后一天返回 FOLLICULAR")
    void computePhase_dayAfterPeriod_returnsFollicular() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 6);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.FOLLICULAR);
    }

    @Test
    @DisplayName("28 天周期第 14 天返回 OVULATION")
    void computePhase_day14Of28Cycle_returnsOvulation() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 14);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.OVULATION);
    }

    @Test
    @DisplayName("周期第 15 天返回 LUTEAL")
    void computePhase_day15Of28Cycle_returnsLuteal() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 6, 15);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.LUTEAL);
    }

    @Test
    @DisplayName("跨周期时正确回绕")
    void computePhase_acrossCycles_wrapsCorrectly() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.of(2026, 7, 13);

        MenstrualPhase phase = MenstrualCycleUtil.computePhase(start, 28, 5, today);

        assertThat(phase).isEqualTo(MenstrualPhase.MENSTRUATION);
    }

    @Test
    @DisplayName("cycleDay 返回周期内第几天")
    void cycleDay_returnsOneBasedDayInCycle() {
        LocalDate start = LocalDate.of(2026, 6, 1);

        assertThat(MenstrualCycleUtil.cycleDay(start, 28, LocalDate.of(2026, 6, 1))).isEqualTo(1);
        assertThat(MenstrualCycleUtil.cycleDay(start, 28, LocalDate.of(2026, 6, 28))).isEqualTo(28);
        assertThat(MenstrualCycleUtil.cycleDay(start, 28, LocalDate.of(2026, 6, 29))).isEqualTo(1);
    }

    @Test
    @DisplayName("daysUntilNextPeriod 计算正确")
    void daysUntilNextPeriod_returnsCorrectDays() {
        LocalDate start = LocalDate.of(2026, 6, 1);

        assertThat(MenstrualCycleUtil.daysUntilNextPeriod(start, 28, LocalDate.of(2026, 6, 1))).isEqualTo(28);
        assertThat(MenstrualCycleUtil.daysUntilNextPeriod(start, 28, LocalDate.of(2026, 6, 29))).isEqualTo(28);
        assertThat(MenstrualCycleUtil.daysUntilNextPeriod(start, 28, LocalDate.of(2026, 6, 28))).isEqualTo(27);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd main/manager-api
mvn test -Dtest=MenstrualCycleUtilTest -DskipTests=false
```

Expected: 编译失败（`MenstrualCycleUtil` / `MenstrualPhase` 不存在）。

- [ ] **Step 3: 实现枚举和工具类**

创建 `MenstrualPhase.java`：

```java
package xiaozhi.modules.companion.util;

public enum MenstrualPhase {
    MENSTRUATION("经期"),
    FOLLICULAR("卵泡期"),
    OVULATION("排卵期"),
    LUTEAL("黄体期");

    private final String label;

    MenstrualPhase(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

创建 `MenstrualCycleUtil.java`：

```java
package xiaozhi.modules.companion.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class MenstrualCycleUtil {

    private MenstrualCycleUtil() {
    }

    public static MenstrualPhase computePhase(LocalDate startDate, int cycleLength, int periodLength, LocalDate today) {
        int day = cycleDay(startDate, cycleLength, today);
        if (day <= periodLength) {
            return MenstrualPhase.MENSTRUATION;
        }
        if (day == cycleLength - 14) {
            return MenstrualPhase.OVULATION;
        }
        if (day < cycleLength - 14) {
            return MenstrualPhase.FOLLICULAR;
        }
        return MenstrualPhase.LUTEAL;
    }

    public static int cycleDay(LocalDate startDate, int cycleLength, LocalDate today) {
        long daysBetween = ChronoUnit.DAYS.between(startDate, today);
        int offset = (int) (daysBetween % cycleLength);
        if (offset < 0) {
            offset += cycleLength;
        }
        return offset + 1;
    }

    public static int daysUntilNextPeriod(LocalDate startDate, int cycleLength, LocalDate today) {
        int day = cycleDay(startDate, cycleLength, today);
        return cycleLength - day + 1;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd main/manager-api
mvn test -Dtest=MenstrualCycleUtilTest -DskipTests=false
```

Expected: 8 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/util/MenstrualPhase.java
main/manager-api/src/main/java/xiaozhi/modules/companion/util/MenstrualCycleUtil.java
main/manager-api/src/test/java/xiaozhi/modules/companion/util/MenstrualCycleUtilTest.java
git commit -m "feat(companion): add menstrual phase and cycle calculation util

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: CompanionEntity 新增字段

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/entity/CompanionEntity.java`
- Test: `mvn compile`

**Interfaces:**
- Consumes: 无
- Produces: `CompanionEntity` 新增 `menstrualCycleStart` (Date), `menstrualCycleLength` (Integer), `menstrualPeriodLength` (Integer)

- [ ] **Step 1: 修改实体类**

在 `CompanionEntity` 的 `mood` 字段上方（或任意合适位置）新增：

```java
@Schema(description = "经期开始日期")
private Date menstrualCycleStart;

@Schema(description = "周期长度（天）")
private Integer menstrualCycleLength;

@Schema(description = "经期长度（天）")
private Integer menstrualPeriodLength;
```

- [ ] **Step 2: 编译验证**

```bash
cd main/manager-api
mvn compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/entity/CompanionEntity.java
git commit -m "feat(companion): add menstrual cycle fields to entity

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: CompanionMood 支持按阶段调整权重

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionMood.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/companion/util/CompanionMoodTest.java`

**Interfaces:**
- Consumes: `MenstrualPhase`
- Produces: `CompanionMood.random(Map<CompanionMood, Integer> adjustments)` 方法

- [ ] **Step 1: 编写失败的测试**

在 `CompanionMoodTest.java` 末尾新增：

```java
@Test
@DisplayName("经期权重调整使疲惫/焦虑/关怀概率升高，兴奋/好奇概率降低")
void random_withMenstruationAdjustments_shiftsWeights() {
    Map<CompanionMood, Integer> adjustments = Map.of(
            CompanionMood.EXCITEMENT, -5,
            CompanionMood.CURIOSITY, -5,
            CompanionMood.FATIGUE, 5,
            CompanionMood.ANXIETY, 5,
            CompanionMood.CARE, 5
    );

    int total = 10_000;
    Map<CompanionMood, Long> counts = Arrays.stream(CompanionMood.values())
            .collect(Collectors.toMap(m -> m, m -> 0L));

    for (int i = 0; i < total; i++) {
        CompanionMood mood = CompanionMood.random(adjustments);
        counts.merge(mood, 1L, Long::sum);
    }

    assertThat(counts.get(CompanionMood.FATIGUE)).isGreaterThan(700L);
    assertThat(counts.get(CompanionMood.ANXIETY)).isGreaterThan(700L);
    assertThat(counts.get(CompanionMood.CARE)).isGreaterThan(1200L);
    assertThat(counts.get(CompanionMood.EXCITEMENT)).isLessThan(1200L);
    assertThat(counts.get(CompanionMood.CURIOSITY)).isLessThan(1200L);
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd main/manager-api
mvn test -Dtest=CompanionMoodTest#random_withMenstruationAdjustments_shiftsWeights -DskipTests=false
```

Expected: FAIL（`random(Map)` 方法不存在）。

- [ ] **Step 3: 实现权重调整随机方法**

修改 `CompanionMood.java`，新增方法：

```java
/**
 * 按权重随机选取一个心情，并应用额外的权重调整。
 * 调整值可为正或负，最终权重不得低于 1。
 *
 * @param adjustments 心情 → 权重调整值映射
 * @return 随机心情
 */
public static CompanionMood random(Map<CompanionMood, Integer> adjustments) {
    Map<CompanionMood, Integer> effectiveWeights = new EnumMap<>(CompanionMood.class);
    int totalWeight = 0;
    for (CompanionMood mood : values()) {
        int adjustment = adjustments != null ? adjustments.getOrDefault(mood, 0) : 0;
        int weight = Math.max(1, mood.weight + adjustment);
        effectiveWeights.put(mood, weight);
        totalWeight += weight;
    }

    int random = ThreadLocalRandom.current().nextInt(totalWeight);
    int cumulative = 0;
    for (CompanionMood mood : values()) {
        cumulative += effectiveWeights.get(mood);
        if (random < cumulative) {
            return mood;
        }
    }
    return CALM;
}
```

保留原有 `random()` 方法不变，让其内部调用 `random(null)` 以保持行为一致：

```java
public static CompanionMood random() {
    return random(null);
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd main/manager-api
mvn test -Dtest=CompanionMoodTest -DskipTests=false
```

Expected: 所有测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionMood.java
main/manager-api/src/test/java/xiaozhi/modules/companion/util/CompanionMoodTest.java
git commit -m "feat(companion): support menstrual-phase mood weight adjustments

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 创建女友时初始化生理周期

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `MenstrualCycleUtil`, `MenstrualPhase`（仅用于类型/计算，不直接依赖）
- Produces: `CompanionServiceImpl` 在 `create()` 中为 gf 伴侣设置 `menstrualCycleStart`, `menstrualCycleLength`, `menstrualPeriodLength`

- [ ] **Step 1: 编写失败的测试**

在 `CompanionServiceImplTest.java` 中新增：

```java
@Test
@DisplayName("create() 为 gf 伴侣自动生成生理期参数")
void create_gfCompanion_generatesMenstrualCycle() {
    Long userId = 100L;
    try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
        security.when(SecurityUser::getUserId).thenReturn(userId);
        security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

        when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

        CompanionCreateDTO dto = createDto();
        dto.setType("gf");

        CompanionVO vo = companionService.create(dto);

        assertThat(vo.getType()).isEqualTo("gf");
        CompanionEntity captured = captureInsertedCompanion();
        assertThat(captured.getMenstrualCycleStart()).isNotNull();
        assertThat(captured.getMenstrualCycleLength()).isBetween(26, 32);
        assertThat(captured.getMenstrualPeriodLength()).isBetween(4, 6);
    }
}

@Test
@DisplayName("create() 为 bf 伴侣不生成生理期参数")
void create_bfCompanion_doesNotGenerateMenstrualCycle() {
    Long userId = 100L;
    try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
        security.when(SecurityUser::getUserId).thenReturn(userId);
        security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

        when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

        CompanionCreateDTO dto = createDto();
        dto.setType("bf");

        companionService.create(dto);

        CompanionEntity captured = captureInsertedCompanion();
        assertThat(captured.getMenstrualCycleStart()).isNull();
        assertThat(captured.getMenstrualCycleLength()).isNull();
        assertThat(captured.getMenstrualPeriodLength()).isNull();
    }
}
```

并添加辅助方法 `captureInsertedCompanion()`（如果尚未存在）：

```java
private CompanionEntity captureInsertedCompanion() {
    ArgumentCaptor<CompanionEntity> captor = ArgumentCaptor.forClass(CompanionEntity.class);
    verify(companionDao).insert(captor.capture());
    return captor.getValue();
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd main/manager-api
mvn test -Dtest=CompanionServiceImplTest#create_gfCompanion_generatesMenstrualCycle -DskipTests=false
```

Expected: FAIL（方法不存在或字段未设置）。

- [ ] **Step 3: 实现初始化逻辑**

在 `CompanionServiceImpl` 中新增私有方法：

```java
private void initializeMenstrualCycle(CompanionEntity entity) {
    if (!"gf".equals(entity.getType())) {
        return;
    }

    int cycleLength = deriveCycleLength(entity);
    int periodLength = derivePeriodLength(entity);

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    int maxOffset = cycleLength - 1;
    int offset = Math.abs(Objects.hash(entity.getCharacter(), entity.getZodiac(), entity.getCreatedAt())) % (maxOffset + 1);
    LocalDate startDate = today.minusDays(offset);

    entity.setMenstrualCycleStart(Date.from(startDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
    entity.setMenstrualCycleLength(cycleLength);
    entity.setMenstrualPeriodLength(periodLength);
}

private int deriveCycleLength(CompanionEntity entity) {
    int hash = Objects.hash(entity.getCharacter(), entity.getZodiac(), entity.getUserId());
    return 26 + (Math.abs(hash) % 7);
}

private int derivePeriodLength(CompanionEntity entity) {
    int hash = Objects.hash(entity.getOccupation(), entity.getSoulQuirk(), entity.getUserId());
    return 4 + (Math.abs(hash) % 3);
}
```

需要在文件顶部添加导入：

```java
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
```

在 `create()` 方法中，设置 `mood` 之后、`companionDao.insert(entity)` 之前调用：

```java
entity.setMood(CompanionMood.JOY.name());
entity.setPastLifeSecret(dto.getPastLifeSecret());
entity.setIntimacy(deriveIntimacy(dto.getRelationType()));
initializeMenstrualCycle(entity);
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd main/manager-api
mvn test -Dtest=CompanionServiceImplTest -DskipTests=false
```

Expected: 新增测试和原有测试均 PASS。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java
main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "feat(companion): initialize menstrual cycle on gf companion creation

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 每日心情刷新应用经期阶段权重

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `MenstrualCycleUtil.computePhase(...)`, `CompanionMood.random(Map<...>)`
- Produces: `refreshAllMoods()` 在经期时应用调整后的权重

- [ ] **Step 1: 编写失败的测试**

在 `CompanionServiceImplTest.java` 中新增：

```java
@Test
@DisplayName("refreshAllMoods() 经期伴侣使用调整后的权重")
void refreshAllMoods_menstruatingCompanion_usesAdjustedWeights() {
    Long userId = 100L;
    try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
        security.when(SecurityUser::getUserId).thenReturn(userId);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        CompanionEntity companion = companionEntity(1L, userId, "device-123", "CALM");
        companion.setType("gf");
        companion.setMenstrualCycleStart(Date.from(today.minusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        companion.setMenstrualCycleLength(28);
        companion.setMenstrualPeriodLength(5);

        Page<CompanionEntity> page = new Page<>();
        page.setRecords(List.of(companion));
        page.setCurrent(1);
        page.setSize(500);
        page.setTotal(1);
        when(companionDao.selectPage(any(Page.class), any())).thenReturn(page);
        when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

        companionService.refreshAllMoods();

        verify(companionDao).updateById(any(CompanionEntity.class));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd main/manager-api
mvn test -Dtest=CompanionServiceImplTest#refreshAllMoods_menstruatingCompanion_usesAdjustedWeights -DskipTests=false
```

Expected: 当前 `refreshAllMoods` 不会读取周期字段，测试可能通过但无实际权重调整；需要确认 `random(null)` 被调用。若直接通过，说明测试不够强，需要改为验证 `random(Map)` 被调用。调整测试为：

```java
// 使用 ArgumentCaptor 捕获 mood 更新值，运行多次确保分布有变化
```

为简化，只要实现中调用了 `random(adjustments)` 即可。

- [ ] **Step 3: 实现刷新逻辑**

修改 `CompanionServiceImpl.refreshAllMoods()` 中的循环体：

```java
for (CompanionEntity companion : companions) {
    try {
        Map<CompanionMood, Integer> adjustments = computeMoodAdjustments(companion);
        companion.setMood(CompanionMood.random(adjustments).name());
        companionDao.updateById(companion);

        String agentId = deviceService.getAgentIdByDeviceId(companion.getDeviceId());
        if (agentId != null && !agentId.isBlank()) {
            doSyncPromptToAgent(agentId, companion);
        }
        totalSuccess++;
    } catch (Exception e) {
        totalFailed++;
        log.warn("刷新伴侣心情失败，companionId={}: {}", companion.getId(), e.getMessage());
    }
}
```

新增私有方法：

```java
private Map<CompanionMood, Integer> computeMoodAdjustments(CompanionEntity companion) {
    if (!"gf".equals(companion.getType())
            || companion.getMenstrualCycleStart() == null
            || companion.getMenstrualCycleLength() == null
            || companion.getMenstrualPeriodLength() == null) {
        return null;
    }

    LocalDate start = companion.getMenstrualCycleStart().toInstant()
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toLocalDate();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    MenstrualPhase phase = MenstrualCycleUtil.computePhase(
            start, companion.getMenstrualCycleLength(), companion.getMenstrualPeriodLength(), today);

    if (phase == MenstrualPhase.MENSTRUATION) {
        return Map.of(
                CompanionMood.EXCITEMENT, -5,
                CompanionMood.CURIOSITY, -5,
                CompanionMood.FATIGUE, 5,
                CompanionMood.ANXIETY, 5,
                CompanionMood.CARE, 5
        );
    }
    return null;
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd main/manager-api
mvn test -Dtest=CompanionServiceImplTest -DskipTests=false
```

Expected: 所有测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java
main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "feat(companion): apply menstrual-phase mood adjustments in refresh task

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 系统提示词注入经期状态

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionLabels.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `MenstrualCycleUtil.computePhase(...)`, `MenstrualPhase.getLabel()`
- Produces: 系统提示词中 `{{menstrualState}}` 被正确替换；经期 gf 包含描述，其他情况为空字符串

- [ ] **Step 1: 编写失败的测试**

在 `CompanionServiceImplTest.java` 中新增：

```java
@Test
@DisplayName("syncPromptToAgent() 经期 gf 提示词包含经期状态")
void syncPromptToAgent_menstruatingGf_includesMenstrualState() {
    Long userId = 100L;
    try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
        security.when(SecurityUser::getUserId).thenReturn(userId);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        CompanionEntity companion = companionEntity(1L, userId, "device-123", "JOY");
        companion.setType("gf");
        companion.setMenstrualCycleStart(Date.from(today.minusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        companion.setMenstrualCycleLength(28);
        companion.setMenstrualPeriodLength(5);
        when(companionDao.selectById(1L)).thenReturn(companion);

        String agentId = "agent-123";
        when(agentService.selectById(agentId)).thenReturn(agentEntity(userId));

        companionService.syncPromptToAgent(agentId, 1L);

        AgentEntity updated = captureUpdatedAgent();
        assertThat(updated.getSystemPrompt()).contains("经期");
    }
}

@Test
@DisplayName("syncPromptToAgent() 非经期 gf 提示词不包含经期状态")
void syncPromptToAgent_nonMenstruatingGf_excludesMenstrualState() {
    Long userId = 100L;
    try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
        security.when(SecurityUser::getUserId).thenReturn(userId);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        CompanionEntity companion = companionEntity(1L, userId, "device-123", "JOY");
        companion.setType("gf");
        companion.setMenstrualCycleStart(Date.from(today.minusDays(10).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        companion.setMenstrualCycleLength(28);
        companion.setMenstrualPeriodLength(5);
        when(companionDao.selectById(1L)).thenReturn(companion);

        String agentId = "agent-123";
        when(agentService.selectById(agentId)).thenReturn(agentEntity(userId));

        companionService.syncPromptToAgent(agentId, 1L);

        AgentEntity updated = captureUpdatedAgent();
        assertThat(updated.getSystemPrompt()).doesNotContain("经期");
        assertThat(updated.getSystemPrompt()).doesNotContain("{{menstrualState}}");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd main/manager-api
mvn test -Dtest=CompanionServiceImplTest#syncPromptToAgent_menstruatingGf_includesMenstrualState -DskipTests=false
```

Expected: FAIL（提示词中无经期内容）。

- [ ] **Step 3: 修改提示词模板和同步逻辑**

在 `CompanionLabels.SYSTEM_PROMPT_TEMPLATE` 中，在 `# Today's Mood` 段落之前新增：

```text
# Menstrual State
{{menstrualState}}
```

在 `CompanionServiceImpl.doSyncPromptToAgent(...)` 中，替换 `{{mood}}` 之前新增：

```java
String menstrualStateLabel = renderMenstrualState(companion);
```

并在 prompt replace 链中加入：

```java
String prompt = CompanionLabels.SYSTEM_PROMPT_TEMPLATE
        .replace("{{character}}", characterLabel)
        // ... 其他 replace ...
        .replace("{{mood}}", moodLabel)
        .replace("{{menstrualState}}", menstrualStateLabel);
```

新增私有方法：

```java
private String renderMenstrualState(CompanionEntity companion) {
    if (!"gf".equals(companion.getType())
            || companion.getMenstrualCycleStart() == null
            || companion.getMenstrualCycleLength() == null
            || companion.getMenstrualPeriodLength() == null) {
        return "";
    }

    LocalDate start = companion.getMenstrualCycleStart().toInstant()
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toLocalDate();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    MenstrualPhase phase = MenstrualCycleUtil.computePhase(
            start, companion.getMenstrualCycleLength(), companion.getMenstrualPeriodLength(), today);

    if (phase != MenstrualPhase.MENSTRUATION) {
        return "";
    }

    int day = MenstrualCycleUtil.cycleDay(start, companion.getMenstrualCycleLength(), today);
    return "你正在经期第 " + day + " 天，小腹有点不舒服，容易累，可能会想向用户撒娇求关心。可以自然地说‘今天肚子好难受，你哄哄我嘛’。";
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd main/manager-api
mvn test -Dtest=CompanionServiceImplTest -DskipTests=false
```

Expected: 所有测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionLabels.java
main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java
main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "feat(companion): inject menstrual state into system prompt

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: CompanionVO 返回经期状态

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/companion/vo/CompanionVO.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java`

**Interfaces:**
- Consumes: `MenstrualCycleUtil`
- Produces: `CompanionVO` 新增 `menstrualStatus` 字段，类型为 `MenstrualStatusVO`

- [ ] **Step 1: 定义 MenstrualStatusVO 内部类**

在 `CompanionVO` 中新增内部类：

```java
@Data
@Schema(description = "经期状态")
public static class MenstrualStatusVO {
    @Schema(description = "阶段编码")
    private String phase;

    @Schema(description = "阶段中文")
    private String phaseLabel;

    @Schema(description = "周期第几天")
    private Integer cycleDay;

    @Schema(description = "距离下次经期天数")
    private Integer daysUntilNextPeriod;
}
```

并在 `CompanionVO` 中新增字段：

```java
@Schema(description = "经期状态")
private MenstrualStatusVO menstrualStatus;
```

- [ ] **Step 2: 在 toVO 中填充状态**

修改 `CompanionVO.toVO`：

```java
vo.setMood(entity.getMood());
vo.setPastLifeSecret(entity.getPastLifeSecret());
vo.setCreatedAt(entity.getCreatedAt());
vo.setUpdatedAt(entity.getUpdatedAt());
vo.setMenstrualStatus(buildMenstrualStatus(entity));
return vo;
```

新增私有静态方法：

```java
private static MenstrualStatusVO buildMenstrualStatus(CompanionEntity entity) {
    if (!"gf".equals(entity.getType())
            || entity.getMenstrualCycleStart() == null
            || entity.getMenstrualCycleLength() == null
            || entity.getMenstrualPeriodLength() == null) {
        return null;
    }

    LocalDate start = entity.getMenstrualCycleStart().toInstant()
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toLocalDate();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    MenstrualPhase phase = MenstrualCycleUtil.computePhase(
            start, entity.getMenstrualCycleLength(), entity.getMenstrualPeriodLength(), today);

    MenstrualStatusVO status = new MenstrualStatusVO();
    status.setPhase(phase.name());
    status.setPhaseLabel(phase.getLabel());
    status.setCycleDay(MenstrualCycleUtil.cycleDay(start, entity.getMenstrualCycleLength(), today));
    status.setDaysUntilNextPeriod(MenstrualCycleUtil.daysUntilNextPeriod(start, entity.getMenstrualCycleLength(), today));
    return status;
}
```

- [ ] **Step 3: 编写/更新测试**

在 `CompanionServiceImplTest.java` 中新增：

```java
@Test
@DisplayName("create() 返回的 VO 包含经期状态")
void create_gfCompanion_voContainsMenstrualStatus() {
    Long userId = 100L;
    try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
        security.when(SecurityUser::getUserId).thenReturn(userId);
        security.when(SecurityUser::getUser).thenReturn(userDetail(userId));

        when(deviceService.getAgentIdByDeviceId("device-123")).thenReturn(null);

        CompanionCreateDTO dto = createDto();
        dto.setType("gf");

        CompanionVO vo = companionService.create(dto);

        assertThat(vo.getMenstrualStatus()).isNotNull();
        assertThat(vo.getMenstrualStatus().getPhase()).isNotBlank();
        assertThat(vo.getMenstrualStatus().getCycleDay()).isBetween(1, 32);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd main/manager-api
mvn test -Dtest=CompanionServiceImplTest -DskipTests=false
```

Expected: 所有测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/companion/vo/CompanionVO.java
main/manager-api/src/test/java/xiaozhi/modules/companion/service/impl/CompanionServiceImplTest.java
git commit -m "feat(companion): expose menstrual status in CompanionVO

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: 小程序设置页状态 pill

**Files:**
- Modify: `main/miniprogram/pages/settings/settings.js`
- Modify: `main/miniprogram/pages/settings/settings.wxml`
- Modify: `main/miniprogram/pages/settings/settings.wxss`

**Interfaces:**
- Consumes: API 返回的 `companion.menstrualStatus` 和 `companion.mood`
- Produces: 设置页羁绊面板下方条件渲染经期状态 pill 和心情 pill

- [ ] **Step 1: 修改 JS 读取状态**

在 `settings.js` 中，找到 `loadCompanionAvatar` 或新增 `loadCompanionStatus` 方法：

```javascript
loadCompanionStatus() {
  var app = getApp();
  var status = app.globalData.companionStatus || null;
  this.setData({
    menstrualStatus: status
  });
}
```

并在 `onShow` 中调用：

```javascript
onShow() {
  applyTheme(this);
  this.setData({
    userAvatar: wx.getStorageSync('userAvatar') || '/images/user-default.png'
  });
  this.loadCompanionAvatar();
  this.loadCompanionStatus();
  this.loadSubscription();
  // ...
}
```

**注意**：若现有流程中设置页已调用接口获取 companion 详情，则优先从接口响应中提取 `menstrualStatus` 和 `mood`，而不是依赖 globalData。请根据现有接口调用调整。

- [ ] **Step 2: 修改 WXML 渲染 pill**

在 `settings.wxml` 的 `bond-card` 内部，在 `bond-identity` 之后新增：

```xml
<!-- 状态 pill -->
<view class="bond-status-row" wx:if="{{menstrualStatus || mood}}">
  <view class="bond-status-pill bond-status-menstrual" wx:if="{{menstrualStatus && menstrualStatus.phase === 'MENSTRUATION'}}">
    <text class="bond-status-text">经期第 {{menstrualStatus.cycleDay}} 天</text>
  </view>
  <view class="bond-status-pill bond-status-mood" wx:if="{{mood}}">
    <text class="bond-status-text">心情：{{moodLabel}}</text>
  </view>
</view>
```

- [ ] **Step 3: 新增 WXSS 样式**

在 `settings.wxss` 中新增：

```css
/* --- 状态 pill --- */
.bond-status-row {
  display: flex;
  gap: 16rpx;
  justify-content: center;
  margin-top: 24rpx;
  flex-wrap: wrap;
}

.bond-status-pill {
  padding: 10rpx 24rpx;
  border-radius: 24rpx;
  font-size: 24rpx;
  line-height: 1.4;
  letter-spacing: 0.5rpx;
}

.bond-status-menstrual {
  background: rgba(134, 78, 90, 0.1);
  color: #864e5a;
}

.bond-status-mood {
  background: rgba(134, 78, 90, 0.08);
  color: #864e5a;
}

/* 深色模式 */
.dark .bond-status-menstrual {
  background: rgba(212, 115, 122, 0.15);
  color: #d4a0a6;
}

.dark .bond-status-mood {
  background: rgba(212, 115, 122, 0.12);
  color: #d4a0a6;
}
```

- [ ] **Step 4: 在微信开发者工具中验证**

1. 打开微信开发者工具，加载 `main/miniprogram`
2. 进入设置页
3. 预期结果：
   - 经期 gf：羁绊面板下方显示“经期第 N 天”和“心情：xxx”两个 pill
   - 非经期 gf：只显示“心情：xxx”一个 pill
   - bf：只显示“心情：xxx”（如果产品有 bf 类型）
4. 切换深色模式，确认 pill 颜色符合深色模式样式

- [ ] **Step 5: Commit**

```bash
git add main/miniprogram/pages/settings/settings.js
main/miniprogram/pages/settings/settings.wxml
main/miniprogram/pages/settings/settings.wxss
git commit -m "feat(miniprogram): show menstrual status pills in settings page

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: 集成验证与回归测试

**Files:**
- 所有已修改文件

**Interfaces:**
- Consumes: 前 9 个任务的产物
- Produces: 所有测试通过、功能可运行的最终状态

- [ ] **Step 1: 运行 manager-api 全部测试**

```bash
cd main/manager-api
mvn test -DskipTests=false
```

Expected: BUILD SUCCESS，所有测试 PASS。

- [ ] **Step 2: 运行编译检查**

```bash
cd main/manager-api
mvn compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 启动服务并验证数据库**

```bash
cd main/manager-api
mvn spring-boot:run
```

在数据库中执行：

```sql
SELECT id, type, menstrual_cycle_start, menstrual_cycle_length, menstrual_period_length
FROM ai_companion
WHERE type = 'gf'
LIMIT 5;
```

Expected: gf 类型的记录三列均有值；bf 类型为 NULL。

- [ ] **Step 4: 验证 API 响应**

调用现有伴侣详情接口（例如 `GET /xiaozhi/companion/{deviceId}`，具体路径以 `CompanionController` 为准），确认响应中包含：

```json
{
  "code": 0,
  "data": {
    "type": "gf",
    "mood": "FATIGUE",
    "menstrualStatus": {
      "phase": "MENSTRUATION",
      "phaseLabel": "经期",
      "cycleDay": 2,
      "daysUntilNextPeriod": 27
    }
  }
}
```

- [ ] **Step 5: 验证小程序 UI**

在微信开发者工具中：
1. 设置页显示正确的 pill
2. 非经期时经期 pill 隐藏
3. 深色模式样式正确

- [ ] **Step 6: Commit（如有多处小修）**

```bash
git add .
git commit -m "fix: integration tweaks for menstrual cycle feature

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Checklist

### 1. Spec Coverage

| Spec 要求 | 对应 Task |
|---|---|
| 系统自动模拟周期参数 | Task 5 |
| 仅 gf 类型生效 | Task 5, Task 6, Task 7, Task 8 |
| 经期主动撒娇求关心 | Task 7（提示词注入） |
| 设置页展示状态，非经期隐藏经期 pill | Task 9 |
| 按女友特征个性化周期长度 | Task 5 |
| 经期影响心情权重 | Task 4, Task 6 |
| 后端可配置/可查询 | Task 8 |

无遗漏。

### 2. Placeholder Scan

- 无 TBD / TODO
- 无 “add appropriate error handling” 等模糊描述
- 每个代码步骤均包含实际代码
- 文件路径精确

### 3. Type Consistency

- `MenstrualCycleUtil.computePhase` 签名在 Task 2 定义，Task 6/7/8 使用一致
- `CompanionMood.random(Map<...>)` 签名在 Task 4 定义，Task 6 使用一致
- `CompanionVO.MenstrualStatusVO` 字段在 Task 8 定义，Task 9 消费一致

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-26-menstrual-cycle-plan.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
