# 宠物原型共享故事状态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 每小时为锦鲤、玉兔两个宠物原型安全推进共享故事状态，原子归档历史，并提供按宠物 ID 查询共享当前状态和历史的后端接口。

**Architecture:** 现有 `storyengine` 继续管理基础内容；新增纯 `StoryStateSelector`、批量内容装载器、原型级事务服务和整点调度器。当前状态按 `pet_prototype` 唯一，历史保存不可变快照；多实例通过原型状态行锁和 `last_evaluated_hour` 时槽标记共同保证每原型每小时最多计算一次。

**Tech Stack:** Java 21、Spring Boot 3.4.3、Spring `@Scheduled`/`TransactionTemplate`、MyBatis-Plus 3.5.17、MySQL、Liquibase、JUnit 5、Mockito、AssertJ、Maven、JaCoCo。

## Global Constraints

- 设计依据：`docs/superpowers/specs/2026-08-08-pet-prototype-story-state-design.md`。
- 本次只修改 `main/manager-api` 和对应后端测试，不修改 manager-web 或蛋宝宝小程序。
- 计算维度固定为宠物原型；当前支持列表只能是“锦鲤、玉兔”，不得按单只宠物调度。
- cron 必须为 `0 0 * * * ?`，时区必须为 `Asia/Shanghai`。
- 普通切换的剩余概率表示保持原状态；首次初始化按配置完整候选的相对权重选择。
- 当前状态与历史必须保存名称、图片 URL 和选中配文快照，不得依赖查询时关联基础表。
- 多实例幂等必须同时使用 `SELECT ... FOR UPDATE` 和 `last_evaluated_hour`；概率未命中也必须提交本时槽标记。
- 不引入 Redis 锁、Quartz、ShedLock、消息队列或新的生产依赖。
- 不修改历史 Liquibase changeSet，只新增 `202608081000.sql` 并注册到 master。
- 日志不得包含用户 ID、宠物实例 ID、设备 ID、token 或其他敏感数据。
- 新增运行时代码目标覆盖率不低于 80%；所有 Maven 测试命令必须显式带 `-DskipTests=false`。
- 保留并且不得提交现有用户修改 `main/manager-api/.factorypath`。

---

### Task 1: 当前状态、历史快照与 Liquibase 持久化契约

**Files:**
- Create: `main/manager-api/src/main/resources/db/changelog/202608081000.sql`
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/entity/PetStoryStateEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/entity/PetStoryHistoryEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/dao/PetStoryStateDao.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/dao/PetStoryHistoryDao.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/storyengine/StoryRuntimePersistenceContractTest.java`

**Interfaces:**
- Produces: `PetStoryStateDao.selectByPrototypeForUpdate(String prototype): PetStoryStateEntity`
- Produces: MyBatis-Plus CRUD for `ai_pet_story_state` and `ai_pet_story_history`
- Produces: exactly two seeded state rows with `runtime_status='UNINITIALIZED'`

- [ ] **Step 1: Write the failing persistence contract test**

Create a test that verifies table mappings, the locking query signature, migration registration, the prototype unique key, history paging index, and both seed rows.

```java
package xiaozhi.modules.storyengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.annotation.TableName;

import xiaozhi.modules.storyengine.dao.PetStoryStateDao;
import xiaozhi.modules.storyengine.entity.PetStoryHistoryEntity;
import xiaozhi.modules.storyengine.entity.PetStoryStateEntity;

class StoryRuntimePersistenceContractTest {
    @Test
    void entitiesMapToRuntimeTables() {
        assertThat(PetStoryStateEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("ai_pet_story_state");
        assertThat(PetStoryHistoryEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("ai_pet_story_history");
        assertThat(PetStoryStateDao.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains("selectByPrototypeForUpdate");
    }

    @Test
    void migrationDefinesSharedPrototypeConstraintsAndSeeds() throws Exception {
        String sql = resource("/db/changelog/202608081000.sql");
        String master = resource("/db/changelog/db.changelog-master.yaml");
        assertThat(sql).contains("CREATE TABLE `ai_pet_story_state`")
                .contains("CREATE TABLE `ai_pet_story_history`")
                .contains("UNIQUE KEY `uk_pet_story_state_prototype` (`pet_prototype`)")
                .contains("KEY `idx_pet_story_history_prototype_started` (`pet_prototype`, `started_at`, `id`)")
                .contains("'锦鲤', 'UNINITIALIZED'")
                .contains("'玉兔', 'UNINITIALIZED'");
        assertThat(master).contains("id: 202608081000")
                .contains("path: classpath:db/changelog/202608081000.sql");
    }

    private String resource(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 2: Run the contract test and verify RED**

Run from `main/manager-api`:

```bash
mvn -DskipTests=false -Dtest=StoryRuntimePersistenceContractTest test
```

Expected: test compilation fails because the two entities and DAOs do not exist.

- [ ] **Step 3: Add the new Liquibase changeSet**

Create `202608081000.sql` as a new formatted SQL changeSet. Use the following columns exactly; current rows allow nullable snapshots only while `UNINITIALIZED`, while history rows contain only completed `ACTIVE` snapshots.

```sql
-- liquibase formatted sql

-- changeset minwang:202608081000
CREATE TABLE `ai_pet_story_state` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键UUID',
    `pet_prototype` VARCHAR(20) NOT NULL COMMENT '宠物原型：锦鲤/玉兔',
    `runtime_status` VARCHAR(20) NOT NULL DEFAULT 'UNINITIALIZED' COMMENT 'UNINITIALIZED/ACTIVE',
    `big_scene_id` VARCHAR(32) NULL,
    `big_scene_name` VARCHAR(64) NULL,
    `small_scene_id` VARCHAR(32) NULL,
    `small_scene_name` VARCHAR(100) NULL,
    `action_id` VARCHAR(32) NULL,
    `action_name` VARCHAR(100) NULL,
    `action_image_id` VARCHAR(32) NULL,
    `weight_period` VARCHAR(20) NULL COMMENT 'NIGHT/MORNING/AFTERNOON/EVENING',
    `image_time_of_day` VARCHAR(20) NULL COMMENT 'DAY/SUNSET/NIGHT',
    `image_url` VARCHAR(512) NULL,
    `caption` VARCHAR(1000) NULL,
    `duration_hours` INT NULL,
    `started_at` DATETIME NULL,
    `expected_end_at` DATETIME NULL,
    `last_evaluated_hour` DATETIME NULL COMMENT 'Asia/Shanghai整点时槽',
    `creator` BIGINT NULL,
    `create_date` DATETIME NULL,
    `updater` BIGINT NULL,
    `update_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pet_story_state_prototype` (`pet_prototype`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物原型共享故事当前状态';

CREATE TABLE `ai_pet_story_history` (
    `id` VARCHAR(32) NOT NULL COMMENT '历史主键UUID',
    `pet_prototype` VARCHAR(20) NOT NULL,
    `big_scene_id` VARCHAR(32) NOT NULL,
    `big_scene_name` VARCHAR(64) NOT NULL,
    `small_scene_id` VARCHAR(32) NOT NULL,
    `small_scene_name` VARCHAR(100) NOT NULL,
    `action_id` VARCHAR(32) NOT NULL,
    `action_name` VARCHAR(100) NOT NULL,
    `action_image_id` VARCHAR(32) NOT NULL,
    `weight_period` VARCHAR(20) NOT NULL,
    `image_time_of_day` VARCHAR(20) NOT NULL,
    `image_url` VARCHAR(512) NOT NULL,
    `caption` VARCHAR(1000) NULL,
    `duration_hours` INT NOT NULL,
    `started_at` DATETIME NOT NULL,
    `expected_end_at` DATETIME NOT NULL,
    `archived_at` DATETIME NOT NULL,
    `creator` BIGINT NULL,
    `create_date` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `idx_pet_story_history_prototype_started` (`pet_prototype`, `started_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物原型共享故事历史快照';

INSERT INTO `ai_pet_story_state` (`id`, `pet_prototype`, `runtime_status`, `create_date`)
VALUES
    (REPLACE(UUID(), '-', ''), '锦鲤', 'UNINITIALIZED', NOW()),
    (REPLACE(UUID(), '-', ''), '玉兔', 'UNINITIALIZED', NOW());
```

Append one matching `changeSet` entry to `db.changelog-master.yaml`; do not edit `202608071000.sql` or any earlier migration.

- [ ] **Step 4: Add entities and DAOs**

Map every snake-case column to a camel-case Lombok field using the existing entity conventions. State uses `@TableId(type = IdType.ASSIGN_UUID)` and audit fills; history uses its own assigned UUID.

```java
@Data
@TableName("ai_pet_story_state")
public class PetStoryStateEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String petPrototype;
    private String runtimeStatus;
    private String bigSceneId;
    private String bigSceneName;
    private String smallSceneId;
    private String smallSceneName;
    private String actionId;
    private String actionName;
    private String actionImageId;
    private String weightPeriod;
    private String imageTimeOfDay;
    private String imageUrl;
    private String caption;
    private Integer durationHours;
    private Date startedAt;
    private Date expectedEndAt;
    private Date lastEvaluatedHour;
    @TableField(fill = FieldFill.INSERT) private Long creator;
    @TableField(fill = FieldFill.INSERT) private Date createDate;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updater;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Date updateDate;
}
```

`PetStoryHistoryEntity` contains the same immutable snapshot fields plus `archivedAt`, `creator`, and `createDate`, but no runtime status, last-evaluated field, updater, or update date.

```java
@Mapper
public interface PetStoryStateDao extends BaseMapper<PetStoryStateEntity> {
    @Select("SELECT * FROM ai_pet_story_state WHERE pet_prototype = #{prototype} FOR UPDATE")
    PetStoryStateEntity selectByPrototypeForUpdate(@Param("prototype") String prototype);
}

@Mapper
public interface PetStoryHistoryDao extends BaseMapper<PetStoryHistoryEntity> {
}
```

- [ ] **Step 5: Run the contract test and verify GREEN**

Run:

```bash
mvn -DskipTests=false -Dtest=StoryRuntimePersistenceContractTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the persistence slice**

```bash
git add main/manager-api/src/main/resources/db/changelog/202608081000.sql main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml main/manager-api/src/main/java/xiaozhi/modules/storyengine/entity/PetStoryStateEntity.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/entity/PetStoryHistoryEntity.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/dao/PetStoryStateDao.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/dao/PetStoryHistoryDao.java main/manager-api/src/test/java/xiaozhi/modules/storyengine/StoryRuntimePersistenceContractTest.java
git commit -m "feat: add shared pet story persistence"
```

---

### Task 2: 纯时段解析与确定性故事选择器

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/constant/StoryPetPrototype.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/constant/StoryRuntimeStatus.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/constant/StoryWeightPeriod.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/constant/StoryImageTimeOfDay.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StoryPeriodContext.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StoryImageCandidate.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StoryActionCandidate.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StorySceneCandidate.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/SelectedStoryState.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StorySelectionResult.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StorySelectionResultType.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryRandomSource.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/impl/ThreadLocalStoryRandomSource.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryPeriodResolver.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryStateSelector.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/StoryPeriodResolverTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/StoryStateSelectorTest.java`

**Interfaces:**
- Produces: `StoryPeriodResolver.resolve(ZonedDateTime time): StoryPeriodContext`
- Produces: `StoryStateSelector.selectInitial(List<StorySceneCandidate>): StorySelectionResult`
- Produces: `StoryStateSelector.selectTransition(List<StorySceneCandidate>): StorySelectionResult`
- `StorySelectionResultType` values: `SELECTED`, `REMAIN`, `INVALID_CONFIGURATION`
- `StoryRandomSource.nextInt(int originInclusive, int boundExclusive): int`

- [ ] **Step 1: Write failing period-boundary tests**

Use `Asia/Shanghai` zoned timestamps and assert the exact mapping.

```java
@ParameterizedTest
@CsvSource({
    "00:00,NIGHT,NIGHT", "05:59,NIGHT,NIGHT",
    "06:00,MORNING,DAY", "11:59,MORNING,DAY",
    "12:00,AFTERNOON,DAY", "17:59,AFTERNOON,DAY",
    "18:00,EVENING,SUNSET", "23:59,EVENING,SUNSET"
})
void resolvesWeightAndImagePeriods(String time, StoryWeightPeriod weight, StoryImageTimeOfDay image) {
    ZonedDateTime value = ZonedDateTime.of(LocalDate.parse("2026-08-08"),
            LocalTime.parse(time), ZoneId.of("Asia/Shanghai"));
    assertThat(new StoryPeriodResolver().resolve(value))
            .isEqualTo(new StoryPeriodContext(weight, image));
}
```

- [ ] **Step 2: Write failing selector tests**

Use a queue-backed test random source so every draw and bound is deterministic. Cover these concrete cases:

```java
@Test
void transitionKeepsStateWhenRollFallsIntoRemainingProbability() {
    StoryStateSelector selector = selectorWithRolls(81);
    StorySceneCandidate scene = scene("卧室", 80, validAction(1, 2));
    assertThat(selector.selectTransition(List.of(scene)).type())
            .isEqualTo(StorySelectionResultType.REMAIN);
}

@Test
void initialSelectionNormalizesValidWeightsAndSelectsCompleteSnapshot() {
    StoryStateSelector selector = selectorWithRolls(60, 0, 0, 1);
    StorySelectionResult result = selector.selectInitial(List.of(
            scene("卧室", 40, validAction(1, 2)),
            scene("公园", 30, validAction(1, 2)),
            scene("无图场景", 30)));
    assertThat(result.type()).isEqualTo(StorySelectionResultType.SELECTED);
    assertThat(result.state().smallSceneName()).isEqualTo("公园");
    assertThat(result.state().durationHours()).isEqualTo(2);
}

@Test
void transitionRejectsInvalidGlobalWeightInsteadOfTruncatingIt() {
    StoryStateSelector selector = selectorWithRolls();
    assertThat(selector.selectTransition(List.of(scene("A", 60), scene("B", 50))).type())
            .isEqualTo(StorySelectionResultType.INVALID_CONFIGURATION);
}
```

Also assert: zero/negative weights, selected scene with no eligible action, invalid duration bounds, image choice, empty caption behavior, `|` trimming, and repeated selection of the same IDs.

- [ ] **Step 3: Run selector tests and verify RED**

```bash
mvn -DskipTests=false -Dtest=StoryPeriodResolverTest,StoryStateSelectorTest test
```

Expected: test compilation fails because the period, candidate, result, random-source, and selector types do not exist.

- [ ] **Step 4: Add constants and immutable model records**

Use string-backed enums only at the service boundary; persistence entities continue storing strings.

```java
public enum StoryPetPrototype {
    KOI("锦鲤"), RABBIT("玉兔");
    private final String value;
    StoryPetPrototype(String value) { this.value = value; }
    public String value() { return value; }
}

public enum StoryRuntimeStatus { UNINITIALIZED, ACTIVE }
public enum StoryWeightPeriod { NIGHT, MORNING, AFTERNOON, EVENING }

public enum StoryImageTimeOfDay {
    DAY("白天"), SUNSET("落日"), NIGHT("黑夜");
    private final String databaseValue;
    StoryImageTimeOfDay(String value) { this.databaseValue = value; }
    public String databaseValue() { return databaseValue; }
}

public record StoryPeriodContext(StoryWeightPeriod weightPeriod,
        StoryImageTimeOfDay imageTimeOfDay) {}

public record StoryImageCandidate(String id, String imageUrl, String captions) {}
public record StoryActionCandidate(String id, String name, int durationMin, int durationMax,
        List<StoryImageCandidate> images) {}
public record StorySceneCandidate(String bigSceneId, String bigSceneName,
        String smallSceneId, String smallSceneName, int weight,
        List<StoryActionCandidate> actions) {}
public record SelectedStoryState(String bigSceneId, String bigSceneName,
        String smallSceneId, String smallSceneName, String actionId, String actionName,
        String actionImageId, String imageUrl, String caption, int durationHours) {}
public record StorySelectionResult(StorySelectionResultType type, SelectedStoryState state) {
    public static StorySelectionResult selected(SelectedStoryState state) {
        return new StorySelectionResult(StorySelectionResultType.SELECTED, state);
    }
    public static StorySelectionResult remain() {
        return new StorySelectionResult(StorySelectionResultType.REMAIN, null);
    }
    public static StorySelectionResult invalid() {
        return new StorySelectionResult(StorySelectionResultType.INVALID_CONFIGURATION, null);
    }
}
```

Constructors for list-containing records must defensively use `List.copyOf` so the pure selector cannot observe caller mutation.

- [ ] **Step 5: Implement period resolver and selector minimally**

`StoryPeriodResolver` switches on `time.getHour()`. `StoryStateSelector` must validate all weights before drawing, preserve true 1～100 probabilities for transitions, and normalize only initialization.

Register all production collaborators with constructor injection:

```java
@Component
public class StoryPeriodResolver {
    public StoryPeriodContext resolve(ZonedDateTime time) {
        int hour = time.getHour();
        if (hour < 6) return new StoryPeriodContext(StoryWeightPeriod.NIGHT, StoryImageTimeOfDay.NIGHT);
        if (hour < 12) return new StoryPeriodContext(StoryWeightPeriod.MORNING, StoryImageTimeOfDay.DAY);
        if (hour < 18) return new StoryPeriodContext(StoryWeightPeriod.AFTERNOON, StoryImageTimeOfDay.DAY);
        return new StoryPeriodContext(StoryWeightPeriod.EVENING, StoryImageTimeOfDay.SUNSET);
    }
}

@FunctionalInterface
public interface StoryRandomSource {
    int nextInt(int originInclusive, int boundExclusive);
}

@Component
public class ThreadLocalStoryRandomSource implements StoryRandomSource {
    @Override
    public int nextInt(int originInclusive, int boundExclusive) {
        return ThreadLocalRandom.current().nextInt(originInclusive, boundExclusive);
    }
}

@Component
@RequiredArgsConstructor
public class StoryStateSelector {
    private final StoryRandomSource random;
}
```

```java
public StorySelectionResult selectTransition(List<StorySceneCandidate> scenes) {
    int total = validatedTotal(scenes, true);
    if (total < 0) return StorySelectionResult.invalid();
    if (total == 0) return StorySelectionResult.remain();
    int roll = random.nextInt(1, 101);
    if (roll > total) return StorySelectionResult.remain();
    return chooseSceneByRoll(scenes, roll);
}

public StorySelectionResult selectInitial(List<StorySceneCandidate> scenes) {
    if (validatedTotal(scenes, true) < 0) return StorySelectionResult.invalid();
    List<StorySceneCandidate> valid = scenes.stream()
            .filter(scene -> scene.weight() > 0 && !scene.actions().isEmpty())
            .toList();
    int total = validatedTotal(valid, false);
    if (total <= 0) return StorySelectionResult.invalid();
    return chooseSceneByRoll(valid, random.nextInt(1, total + 1));
}

private StorySelectionResult chooseSceneByRoll(List<StorySceneCandidate> scenes, int roll) {
    int cumulative = 0;
    for (StorySceneCandidate scene : scenes) {
        cumulative += scene.weight();
        if (roll <= cumulative) return chooseWithinScene(scene);
    }
    return StorySelectionResult.invalid();
}
```

`chooseWithinScene` returns invalid when the chosen scene has no eligible action; otherwise it draws action, image, trimmed non-empty caption, and inclusive duration. `validatedTotal(scenes, enforceMaximum)` returns `-1` for negative weights and, when `enforceMaximum=true`, totals above 100. Initialization first validates the original global configuration, then totals only complete candidates for its relative draw. Duration must satisfy `durationMin >= 1 && durationMax >= durationMin`.

- [ ] **Step 6: Run tests and verify GREEN**

```bash
mvn -DskipTests=false -Dtest=StoryPeriodResolverTest,StoryStateSelectorTest test
```

Expected: PASS with deterministic random assertions.

- [ ] **Step 7: Commit the pure selection slice**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/storyengine/constant main/manager-api/src/main/java/xiaozhi/modules/storyengine/model main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryRandomSource.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryPeriodResolver.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryStateSelector.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/impl/ThreadLocalStoryRandomSource.java main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/StoryPeriodResolverTest.java main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/StoryStateSelectorTest.java
git commit -m "feat: add deterministic story state selector"
```

---

### Task 3: 批量装载启用故事内容

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryContentLoader.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/StoryContentLoaderTest.java`

**Interfaces:**
- Consumes: existing `BigSceneDao`, `SmallSceneDao`, `ActionDao`, `ActionImageDao`
- Consumes: `StoryPeriodContext`
- Produces: `StoryContentLoader.load(String prototype, StoryPeriodContext period): List<StorySceneCandidate>`
- Ordering: big scenes, small scenes, and actions use `sort_order ASC, id ASC`

- [ ] **Step 1: Write failing content-loader tests**

Mock all four DAOs. Build one enabled big scene, two small scenes, enabled and disabled actions, and images for both prototypes. Assert that the loader:

- uses the requested period's correct weight field;
- keeps a weighted scene even when its eligible action list is empty, so transition can report invalid configuration;
- includes only status `1` parents, scenes, and actions;
- attaches only images matching the requested prototype and `period.imageTimeOfDay().databaseValue()`;
- performs one batched query per table rather than one query per scene/action.

```java
@Test
void loadsMorningKoiTreeAndLeavesUnconfiguredSceneVisible() {
    when(bigSceneDao.selectList(any())).thenReturn(List.of(bigScene("home", "在家")));
    when(smallSceneDao.selectList(any())).thenReturn(List.of(
            smallScene("bedroom", "home", "卧室", 40),
            smallScene("garden", "home", "花园", 20)));
    when(actionDao.selectList(any())).thenReturn(List.of(action("read", "bedroom", "看书", 1, 2)));
    when(actionImageDao.selectList(any())).thenReturn(List.of(
            image("img-1", "read", "锦鲤", "白天")));

    List<StorySceneCandidate> result = loader.load("锦鲤",
            new StoryPeriodContext(StoryWeightPeriod.MORNING, StoryImageTimeOfDay.DAY));

    assertThat(result).extracting(StorySceneCandidate::smallSceneName)
            .containsExactly("卧室", "花园");
    assertThat(result.get(0).actions()).hasSize(1);
    assertThat(result.get(1).actions()).isEmpty();
    verify(actionImageDao, times(1)).selectList(any());
}
```

- [ ] **Step 2: Run the loader test and verify RED**

```bash
mvn -DskipTests=false -Dtest=StoryContentLoaderTest test
```

Expected: test compilation fails because `StoryContentLoader` does not exist.

- [ ] **Step 3: Implement four-query tree assembly**

Implement `StoryContentLoader` as a focused `@Component`. Return early when a parent query is empty so no `.in(...)` receives an empty collection.

```java
@Component
@RequiredArgsConstructor
public class StoryContentLoader {
    private static final int ENABLED = 1;
    private final BigSceneDao bigSceneDao;
    private final SmallSceneDao smallSceneDao;
    private final ActionDao actionDao;
    private final ActionImageDao actionImageDao;

    public List<StorySceneCandidate> load(String prototype, StoryPeriodContext period) {
        List<BigSceneEntity> bigScenes = loadBigScenes();
        if (bigScenes.isEmpty()) return List.of();
        List<String> bigSceneIds = bigScenes.stream().map(BigSceneEntity::getId).toList();
        List<SmallSceneEntity> smallScenes = loadSmallScenes(bigSceneIds);
        if (smallScenes.isEmpty()) return List.of();
        List<String> smallSceneIds = smallScenes.stream().map(SmallSceneEntity::getId).toList();
        List<ActionEntity> actions = loadActions(smallSceneIds);
        List<String> actionIds = actions.stream().map(ActionEntity::getId).toList();
        Map<String, List<ActionImageEntity>> images = actionIds.isEmpty()
                ? Map.of()
                : loadMatchingImages(actionIds, prototype, period.imageTimeOfDay().databaseValue());
        return assemble(bigScenes, smallScenes, actions, images, period.weightPeriod());
    }
}
```

`weightOf` must map `NIGHT/MORNING/AFTERNOON/EVENING` to the four `SmallSceneEntity` getters and treat null as zero. During assembly, retain all enabled small scenes in stable order; attach only actions whose matching image list is non-empty.

- [ ] **Step 4: Run the loader and selector suites**

```bash
mvn -DskipTests=false -Dtest=StoryContentLoaderTest,StoryStateSelectorTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the content loader**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/StoryContentLoader.java main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/StoryContentLoaderTest.java
git commit -m "feat: load story runtime candidates"
```

---

### Task 4: 原型级事务、历史归档与时槽幂等

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StoryEvaluationResult.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/PrototypeStoryStateService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/impl/PrototypeStoryStateServiceImpl.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/impl/PrototypeStoryStateServiceImplTest.java`

**Interfaces:**
- Consumes: `PetStoryStateDao.selectByPrototypeForUpdate`, `StoryPeriodResolver`, `StoryContentLoader`, `StoryStateSelector`, `PetStoryHistoryDao`
- Produces: `PrototypeStoryStateService.evaluate(String prototype, ZonedDateTime evaluatedAt): StoryEvaluationResult`
- `StoryEvaluationResult` values: `INITIALIZED`, `SWITCHED`, `KEPT_NOT_DUE`, `KEPT_REMAINDER`, `KEPT_INVALID_CONFIGURATION`, `SKIPPED_ALREADY_EVALUATED`

- [ ] **Step 1: Write failing service tests for initialization and switching**

Mock the DAOs, loader and selector; use a mocked `PlatformTransactionManager` returning a mocked `TransactionStatus`. Cover exact state mutations and DAO calls.

```java
@Test
void initializesPlaceholderWithoutHistory() {
    PetStoryStateEntity current = uninitialized("锦鲤");
    when(stateDao.selectByPrototypeForUpdate("锦鲤")).thenReturn(current);
    when(selector.selectInitial(anyList())).thenReturn(StorySelectionResult.selected(selected(2)));

    StoryEvaluationResult result = service.evaluate("锦鲤", at("2026-08-08T10:00:00+08:00"));

    assertThat(result).isEqualTo(StoryEvaluationResult.INITIALIZED);
    assertThat(current.getRuntimeStatus()).isEqualTo("ACTIVE");
    assertThat(current.getExpectedEndAt()).isEqualTo(date("2026-08-08T12:00:00+08:00"));
    verify(historyDao, never()).insert(any());
    verify(stateDao).updateById(current);
}

@Test
void archivesOldSnapshotBeforeReplacingExpiredState() {
    PetStoryStateEntity current = activeExpired("锦鲤");
    when(stateDao.selectByPrototypeForUpdate("锦鲤")).thenReturn(current);
    when(selector.selectTransition(anyList())).thenReturn(StorySelectionResult.selected(selected(3)));

    assertThat(service.evaluate("锦鲤", at("2026-08-08T10:00:00+08:00")))
            .isEqualTo(StoryEvaluationResult.SWITCHED);

    InOrder writes = inOrder(historyDao, stateDao);
    writes.verify(historyDao).insert(argThat(history ->
            history.getActionName().equals("旧动作")
                    && history.getArchivedAt().equals(date("2026-08-08T10:00:00+08:00"))));
    writes.verify(stateDao).updateById(current);
}
```

- [ ] **Step 2: Add failing multi-instance and rollback-oriented tests**

Add tests proving:

- second call in the same normalized hour returns `SKIPPED_ALREADY_EVALUATED` and does not call loader/selector twice;
- future `expected_end_at` returns `KEPT_NOT_DUE` but still persists `last_evaluated_hour`;
- remainder returns `KEPT_REMAINDER`, keeps snapshot fields and persists the slot;
- invalid configuration returns `KEPT_INVALID_CONFIGURATION`, keeps snapshot fields and persists the slot;
- a DAO exception causes `PlatformTransactionManager.rollback(...)`, not commit;
- missing seeded prototype row throws `IllegalStateException` and rolls back.

```java
@Test
void samePrototypeAndHourIsEvaluatedOnlyOnceAcrossInstances() {
    PetStoryStateEntity current = activeExpired("玉兔");
    when(stateDao.selectByPrototypeForUpdate("玉兔")).thenReturn(current);
    when(selector.selectTransition(anyList())).thenReturn(StorySelectionResult.remain());
    ZonedDateTime now = at("2026-08-08T10:15:00+08:00");

    assertThat(service.evaluate("玉兔", now)).isEqualTo(StoryEvaluationResult.KEPT_REMAINDER);
    assertThat(service.evaluate("玉兔", now.plusMinutes(20)))
            .isEqualTo(StoryEvaluationResult.SKIPPED_ALREADY_EVALUATED);

    verify(selector, times(1)).selectTransition(anyList());
    assertThat(current.getLastEvaluatedHour()).isEqualTo(date("2026-08-08T10:00:00+08:00"));
}
```

- [ ] **Step 3: Run the state-service test and verify RED**

```bash
mvn -DskipTests=false -Dtest=PrototypeStoryStateServiceImplTest test
```

Expected: test compilation fails because the evaluation service and result enum do not exist.

- [ ] **Step 4: Implement one-transaction evaluation**

Create one `TransactionTemplate` from the injected `PlatformTransactionManager`. The public method is the only transaction entry point.

```java
@Service
public class PrototypeStoryStateServiceImpl implements PrototypeStoryStateService {
    private final PetStoryStateDao stateDao;
    private final PetStoryHistoryDao historyDao;
    private final StoryPeriodResolver periodResolver;
    private final StoryContentLoader contentLoader;
    private final StoryStateSelector selector;
    private final TransactionTemplate transactionTemplate;

    public PrototypeStoryStateServiceImpl(PetStoryStateDao stateDao,
            PetStoryHistoryDao historyDao, StoryPeriodResolver periodResolver,
            StoryContentLoader contentLoader, StoryStateSelector selector,
            PlatformTransactionManager transactionManager) {
        this.stateDao = stateDao;
        this.historyDao = historyDao;
        this.periodResolver = periodResolver;
        this.contentLoader = contentLoader;
        this.selector = selector;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public StoryEvaluationResult evaluate(String prototype, ZonedDateTime evaluatedAt) {
        return transactionTemplate.execute(status -> evaluateLocked(prototype, evaluatedAt));
    }

    private StoryEvaluationResult evaluateLocked(String prototype, ZonedDateTime evaluatedAt) {
        PetStoryStateEntity current = stateDao.selectByPrototypeForUpdate(prototype);
        if (current == null) throw new IllegalStateException("缺少宠物原型故事状态占位行: " + prototype);

        Date hourSlot = Date.from(evaluatedAt.truncatedTo(ChronoUnit.HOURS).toInstant());
        if (hourSlot.equals(current.getLastEvaluatedHour())) {
            return StoryEvaluationResult.SKIPPED_ALREADY_EVALUATED;
        }

        StoryPeriodContext period = periodResolver.resolve(evaluatedAt);
        StoryEvaluationResult result = evaluateDueState(current, period, evaluatedAt);
        current.setLastEvaluatedHour(hourSlot);
        stateDao.updateById(current);
        return result;
    }
}
```

`evaluateDueState` follows this exact branch order:

1. `ACTIVE` and `expectedEndAt.after(now)` → `KEPT_NOT_DUE` without loading content.
2. Load candidates once.
3. `UNINITIALIZED` → `selector.selectInitial`; selected outcome activates current row and returns `INITIALIZED`.
4. `ACTIVE` and due → `selector.selectTransition`; selected outcome inserts history from the untouched old snapshot, then applies new state and returns `SWITCHED`.
5. `REMAIN` → `KEPT_REMAINDER`.
6. `INVALID_CONFIGURATION` → `KEPT_INVALID_CONFIGURATION`.

Use dedicated private helpers with exact responsibilities:

```java
private PetStoryHistoryEntity snapshot(PetStoryStateEntity source, Date archivedAt);
private void activate(PetStoryStateEntity target, SelectedStoryState selected,
        String prototype, StoryPeriodContext period, Date startedAt);
```

`activate` sets `expectedEndAt` from `evaluatedAt.plusHours(selected.durationHours())`. Never mutate the current entity before creating the history snapshot.

- [ ] **Step 5: Run service and selector tests and verify GREEN**

```bash
mvn -DskipTests=false -Dtest=PrototypeStoryStateServiceImplTest,StoryStateSelectorTest,StoryContentLoaderTest test
```

Expected: PASS, including commit/rollback verification on the mocked transaction manager.

- [ ] **Step 6: Commit the transactional state engine**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/storyengine/model/StoryEvaluationResult.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/PrototypeStoryStateService.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/impl/PrototypeStoryStateServiceImpl.java main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/impl/PrototypeStoryStateServiceImplTest.java
git commit -m "feat: advance shared story states transactionally"
```

---

### Task 5: 每小时双原型调度与故障隔离

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/config/StoryRuntimeConfig.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/task/PrototypeStoryStateRefreshTask.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/storyengine/task/PrototypeStoryStateRefreshTaskTest.java`

**Interfaces:**
- Consumes: `PrototypeStoryStateService.evaluate`
- Produces: Spring bean `@Qualifier("storyRuntimeClock") Clock`
- Produces: scheduled `PrototypeStoryStateRefreshTask.refreshStates()`

- [ ] **Step 1: Write the failing scheduler tests**

Use a fixed `Asia/Shanghai` clock. Verify both exact prototype values are called with the same timestamp and a failure for 锦鲤 does not prevent 玉兔.

```java
@Test
void refreshesExactlyTwoSupportedPrototypes() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-08T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    PrototypeStoryStateRefreshTask task = new PrototypeStoryStateRefreshTask(service, clock);

    task.refreshStates();

    ZonedDateTime expected = ZonedDateTime.ofInstant(clock.instant(), clock.getZone());
    verify(service).evaluate("锦鲤", expected);
    verify(service).evaluate("玉兔", expected);
    verifyNoMoreInteractions(service);
}

@Test
void koiFailureDoesNotBlockRabbit() {
    doThrow(new IllegalStateException("boom")).when(service).evaluate(eq("锦鲤"), any());
    task.refreshStates();
    verify(service).evaluate(eq("玉兔"), any());
}
```

Reflectively inspect `refreshStates` and assert its `@Scheduled` annotation has cron `0 0 * * * ?` and zone `Asia/Shanghai`.

- [ ] **Step 2: Run the scheduler test and verify RED**

```bash
mvn -DskipTests=false -Dtest=PrototypeStoryStateRefreshTaskTest test
```

Expected: test compilation fails because task and clock config do not exist.

- [ ] **Step 3: Implement the clock configuration and scheduler**

```java
@Configuration
@EnableScheduling
public class StoryRuntimeConfig {
    @Bean("storyRuntimeClock")
    Clock storyRuntimeClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}

@Slf4j
@Component
public class PrototypeStoryStateRefreshTask {
    private final PrototypeStoryStateService service;
    private final Clock clock;

    public PrototypeStoryStateRefreshTask(PrototypeStoryStateService service,
            @Qualifier("storyRuntimeClock") Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * ?", zone = "Asia/Shanghai")
    public void refreshStates() {
        ZonedDateTime evaluatedAt = ZonedDateTime.ofInstant(clock.instant(), clock.getZone());
        Map<StoryEvaluationResult, Integer> counts = new EnumMap<>(StoryEvaluationResult.class);
        int failures = 0;
        for (StoryPetPrototype prototype : StoryPetPrototype.values()) {
            try {
                StoryEvaluationResult result = service.evaluate(prototype.value(), evaluatedAt);
                counts.merge(result, 1, Integer::sum);
                if (result == StoryEvaluationResult.KEPT_INVALID_CONFIGURATION) {
                    log.warn("宠物原型故事配置不完整 prototype={}, hour={}",
                            prototype.value(), evaluatedAt.truncatedTo(ChronoUnit.HOURS));
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("宠物原型故事状态刷新失败 prototype={}, hour={}",
                        prototype.value(), evaluatedAt.truncatedTo(ChronoUnit.HOURS), exception);
            }
        }
        log.info("宠物原型故事状态刷新完成 hour={}, results={}, failures={}",
                evaluatedAt.truncatedTo(ChronoUnit.HOURS), counts, failures);
    }
}
```

Log only prototype, hour slot, result counters and failure count. Do not query pets or include any user/device identifier. Extend the scheduler test to assert `KEPT_INVALID_CONFIGURATION` does not abort the loop and both service calls still occur.

- [ ] **Step 4: Run scheduler and state-service tests**

```bash
mvn -DskipTests=false -Dtest=PrototypeStoryStateRefreshTaskTest,PrototypeStoryStateServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the scheduler**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/storyengine/config/StoryRuntimeConfig.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/task/PrototypeStoryStateRefreshTask.java main/manager-api/src/test/java/xiaozhi/modules/storyengine/task/PrototypeStoryStateRefreshTaskTest.java
git commit -m "feat: schedule prototype story refresh"
```

---

### Task 6: 按宠物查询共享当前状态和分页历史

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/vo/PetStoryStateVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/vo/PetStoryHistoryVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/PetStoryQueryService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/impl/PetStoryQueryServiceImpl.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pet/controller/PetStoryController.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/impl/PetStoryQueryServiceImplTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pet/controller/PetStoryControllerTest.java`

**Interfaces:**
- Produces: `PetStoryQueryService.getCurrent(Long userId, String petId): PetStoryStateVO`
- Produces: `PetStoryQueryService.getHistory(Long userId, String petId, Map<String,Object> params): PageData<PetStoryHistoryVO>`
- Produces: `GET /pet/{id}/story-state`
- Produces: `GET /pet/{id}/story-history?page=1&limit=10`

- [ ] **Step 1: Write failing query-service tests**

Mock `PetDao`, `PetStoryStateDao`, and `PetStoryHistoryDao`. Cover ownership, hatch state, prototype resolution and stable paging.

```java
@Test
void hatchedPetReadsSharedStateByPrototype() {
    when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));
    when(stateDao.selectOne(any())).thenReturn(activeState("锦鲤", "看书"));

    PetStoryStateVO result = service.getCurrent(7L, "pet-1");

    assertThat(result.getPetPrototype()).isEqualTo("锦鲤");
    assertThat(result.getActionName()).isEqualTo("看书");
}

@Test
void eggCannotReadSharedStateOrHistory() {
    when(petDao.selectById("egg-1")).thenReturn(pet("egg-1", 7L, "EGG", "锦鲤"));
    assertThat(service.getCurrent(7L, "egg-1")).isNull();
    assertThat(service.getHistory(7L, "egg-1", Map.of("page", "1", "limit", "10")).getList())
            .isEmpty();
    verifyNoInteractions(stateDao, historyDao);
}
```

Also test `PET_NOT_FOUND`, `PET_NO_PERMISSION`, `UNINITIALIZED -> null`, two same-prototype pets receiving the same state, different prototypes, page less than 1, limit outside 1～100, and history ordering `started_at DESC, id DESC`.

- [ ] **Step 2: Write failing controller tests**

Instantiate `PetStoryController` directly, statically mock `SecurityUser.getUserId()`, and verify it delegates with the authenticated user ID and returns the existing `Result<T>` envelope.

```java
@Test
void currentDelegatesAuthenticatedUserAndPetId() {
    PetStoryStateVO expected = new PetStoryStateVO();
    when(queryService.getCurrent(7L, "pet-1")).thenReturn(expected);
    try (MockedStatic<SecurityUser> security = mockStatic(SecurityUser.class)) {
        security.when(SecurityUser::getUserId).thenReturn(7L);
        Result<PetStoryStateVO> result = controller.current("pet-1");
        assertThat(result.getData()).isSameAs(expected);
        verify(queryService).getCurrent(7L, "pet-1");
    }
}
```

Reflectively verify both methods carry `@RequiresPermissions("sys:role:normal")` and exact `@GetMapping` paths.

- [ ] **Step 3: Run query tests and verify RED**

```bash
mvn -DskipTests=false -Dtest=PetStoryQueryServiceImplTest,PetStoryControllerTest test
```

Expected: test compilation fails because query service, VOs, and controller do not exist.

- [ ] **Step 4: Implement immutable snapshot-to-VO mapping and query service**

VOs expose IDs, names, prototype, weight period, image period, image URL, selected caption, duration, start/end times; history adds `archivedAt`.

```java
public interface PetStoryQueryService {
    PetStoryStateVO getCurrent(Long userId, String petId);
    PageData<PetStoryHistoryVO> getHistory(Long userId, String petId, Map<String, Object> params);
}
```

Implementation rules:

```java
private PetEntity ownedPet(Long userId, String petId) {
    PetEntity pet = petDao.selectById(petId);
    if (pet == null) throw new RenException(ErrorCode.PET_NOT_FOUND);
    if (!userId.equals(pet.getUserId())) throw new RenException(ErrorCode.PET_NO_PERMISSION);
    return pet;
}

private boolean isHatched(PetEntity pet) {
    return "HATCHED".equals(pet.getHatchStatus());
}
```

For current state, query `pet_prototype = pet.prototype AND runtime_status = 'ACTIVE'`; return null otherwise. For history, clamp page to at least 1 and reject limit outside 1～100 with `RenException("分页大小必须在1到100之间")`. Use MyBatis-Plus `Page`, filter by prototype, and order with both `orderByDesc("started_at")` and `orderByDesc("id")`.

- [ ] **Step 5: Add the dedicated pet story controller**

```java
@Tag(name = "宠物故事状态")
@RestController
@RequestMapping("/pet")
@RequiredArgsConstructor
public class PetStoryController {
    private final PetStoryQueryService queryService;

    @GetMapping("/{id}/story-state")
    @RequiresPermissions("sys:role:normal")
    public Result<PetStoryStateVO> current(@PathVariable String id) {
        return new Result<PetStoryStateVO>().ok(
                queryService.getCurrent(SecurityUser.getUserId(), id));
    }

    @GetMapping("/{id}/story-history")
    @RequiresPermissions("sys:role:normal")
    public Result<PageData<PetStoryHistoryVO>> history(@PathVariable String id,
            @RequestParam Map<String, Object> params) {
        return new Result<PageData<PetStoryHistoryVO>>().ok(
                queryService.getHistory(SecurityUser.getUserId(), id, params));
    }
}
```

- [ ] **Step 6: Run query and controller tests and verify GREEN**

```bash
mvn -DskipTests=false -Dtest=PetStoryQueryServiceImplTest,PetStoryControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the read APIs**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/storyengine/vo/PetStoryStateVO.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/vo/PetStoryHistoryVO.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/PetStoryQueryService.java main/manager-api/src/main/java/xiaozhi/modules/storyengine/service/impl/PetStoryQueryServiceImpl.java main/manager-api/src/main/java/xiaozhi/modules/pet/controller/PetStoryController.java main/manager-api/src/test/java/xiaozhi/modules/storyengine/service/impl/PetStoryQueryServiceImplTest.java main/manager-api/src/test/java/xiaozhi/modules/pet/controller/PetStoryControllerTest.java
git commit -m "feat: expose shared pet story queries"
```

---

### Task 7: 全量回归、覆盖率与迁移验证

**Files:**
- Verify only: all files created or modified in Tasks 1–6
- Do not modify or stage: `main/manager-api/.factorypath`

**Interfaces:**
- Verifies the complete scheduled-write and authenticated-read feature
- Produces no new production interface

- [ ] **Step 1: Run all focused story runtime tests**

```bash
mvn -DskipTests=false -Dtest=StoryRuntimePersistenceContractTest,StoryPeriodResolverTest,StoryStateSelectorTest,StoryContentLoaderTest,PrototypeStoryStateServiceImplTest,PrototypeStoryStateRefreshTaskTest,PetStoryQueryServiceImplTest,PetStoryControllerTest test
```

Expected: all focused tests PASS.

- [ ] **Step 2: Run the complete manager-api test suite**

```bash
mvn -DskipTests=false test
```

Expected: BUILD SUCCESS with no existing regression. If an unrelated environment-dependent Spring test fails, capture its exact test name and failure separately; do not weaken or delete the new tests.

- [ ] **Step 3: Generate and inspect JaCoCo coverage**

```bash
mvn -DskipTests=false verify
```

Expected: `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.xml` are generated. Confirm the newly added selector, loader, state service, scheduler and query service collectively meet the project target of at least 80% line coverage. Close selector gaps in `StoryStateSelectorTest`, loader gaps in `StoryContentLoaderTest`, transaction gaps in `PrototypeStoryStateServiceImplTest`, scheduling gaps in `PrototypeStoryStateRefreshTaskTest`, and authorization/paging gaps in `PetStoryQueryServiceImplTest`; do not lower the target or exclude runtime classes.

- [ ] **Step 4: Validate Liquibase registration and clean diff**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only intended feature files are staged or committed; `main/manager-api/.factorypath` remains modified but unstaged and unchanged from its pre-task state.

- [ ] **Step 5: Review security and concurrency invariants**

Confirm from tests and final diff:

- both endpoints derive user ID from `SecurityUser` and enforce pet ownership;
- `EGG` never exposes shared state/history;
- scheduler loops only two enum values and never queries `ai_pet`;
- `selectByPrototypeForUpdate` happens before checking `last_evaluated_hour`;
- remainder/config-invalid/not-due paths persist the hour slot without writing history;
- exceptions roll back hour slot, history and current update;
- no log contains user, pet instance, device or credential values.

- [ ] **Step 6: Create a final verification commit only if tests required fixes**

If Steps 1–5 required test or implementation corrections, stage only those focused files and commit them:

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/storyengine main/manager-api/src/main/java/xiaozhi/modules/pet/controller/PetStoryController.java main/manager-api/src/test/java/xiaozhi/modules/storyengine main/manager-api/src/test/java/xiaozhi/modules/pet/controller/PetStoryControllerTest.java main/manager-api/src/main/resources/db/changelog/202608081000.sql main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "test: harden shared story state coverage"
```

If no corrections were needed, do not create an empty commit.
