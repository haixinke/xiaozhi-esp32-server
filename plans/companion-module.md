# Companion Module Construction Plan

## Objective

在 manager-api (Java Spring Boot) 中新增 companion 模块，管理"AI伴侣"基本信息（支持女友/男友）。提供 3 个接口：创建伴侣、修改伴侣、根据 device_id 查询伴侣。

## Context Brief

- **后端框架**: Java 21, Spring Boot 3.4.3, MyBatis-Plus 3.5.5, Liquibase
- **参考模块**: `pet` 模块 — 完整 CRUD 示例，包含 LLM 调用、八字五行计算、心情枚举
- **数据库**: MySQL 8.0+, 表名 `ai_companion`
- **表结构**: 已确认，见下方 Step 1

### 角色年龄映射

| character (编码) | 角色 | 年龄 |
|---|---|---|
| `linjiamei` | 元气邻家妹 | 18 |
| `erciyuan` | 潮酷二次元 | 20 |
| `baiyueguang` | 高冷白月光 | 22 |
| `zhixingyujie` | 知性御姐 | 25 |

### 字段分工

**前端传入**: type, user_id, device_id, avatar, default_image, character, occupation, voice, quirks_text, soul_traits, soul_quirk, relation_type, pet_type, pet_name

**后端生成**: birthday, zodiac, chinese_zodiac, bazi, wuxing, personality, mood, created_by, created_at

---

## Step 1: Database Migration (Liquibase)

**Depends on**: None
**Files to create/modify**:
- `main/manager-api/src/main/resources/db/changelog/202606041000.sql` (new)
- `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` (append entry)

**Tasks**:
1. Create migration SQL file with the confirmed `ai_companion` table DDL
2. Register the changeSet in `db.changelog-master.yaml` (id: `202606041000`, author: developer name)
3. Follow existing naming convention: `YYYYMMDDHHMM.sql`

**DDL**:
```sql
CREATE TABLE ai_companion (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '关联用户表',
    device_id       VARCHAR(32)  NOT NULL COMMENT '设备ID',
    type            VARCHAR(8)   NOT NULL COMMENT '伴侣类型: gf=女友, bf=男友',
    avatar          VARCHAR(256) NOT NULL COMMENT '女友头像URL',
    default_image   VARCHAR(256) NOT NULL COMMENT '默认图片URL',
    birthday        DATETIME     NOT NULL COMMENT '出生日期',
    zodiac          VARCHAR(16)  NOT NULL COMMENT '星座: aries/taurus/.../pisces',
    chinese_zodiac  VARCHAR(16)  NOT NULL COMMENT '属相: rat/ox/tiger/.../pig',
    bazi            JSON         NOT NULL COMMENT '八字',
    wuxing          JSON         NOT NULL COMMENT '五行',
    character       VARCHAR(32)  NOT NULL COMMENT '角色',
    occupation      VARCHAR(32)  NOT NULL COMMENT '职业',
    voice           VARCHAR(32)  NOT NULL COMMENT '音色',
    personality     VARCHAR(800) NOT NULL COMMENT '性格描述',
    quirks_text     VARCHAR(200) NULL     COMMENT '职业病描述',
    soul_traits     VARCHAR(64)  NOT NULL COMMENT '灵魂特质,逗号分隔',
    soul_quirk      VARCHAR(32)  NOT NULL COMMENT '小任性',
    relation_type   VARCHAR(32)  NOT NULL COMMENT '关系类型',
    pet_type        VARCHAR(16)  NOT NULL COMMENT '宠物类型: cat/dog',
    pet_name        VARCHAR(32)  NOT NULL COMMENT '宠物名',
    mood            VARCHAR(16)  NOT NULL COMMENT '今日心情',
    created_by      BIGINT       NOT NULL COMMENT '创建人ID',
    updated_by      BIGINT       NULL     COMMENT '修改人ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI伴侣配置表';
```

**Exit criteria**: `db.changelog-master.yaml` contains the new changeSet entry; SQL syntax is valid.

---

## Step 2: Entity + DAO

**Depends on**: Step 1
**Files to create**:
- `main/manager-api/src/main/java/xiaozhi/modules/companion/entity/CompanionEntity.java`
- `main/manager-api/src/main/java/xiaozhi/modules/companion/dao/CompanionDao.java`

**Tasks**:

### CompanionEntity
- `@Data @TableName("ai_companion")`
- `id`: Long, `@TableId(type = IdType.AUTO)`
- All fields from the DDL as Java types:
  - `userId` (Long), `deviceId` (String), `type` (String), `avatar` (String), `defaultImage` (String)
  - `birthday` (LocalDateTime), `zodiac` (String), `chineseZodiac` (String)
  - `bazi` (String - JSON stored as string), `wuxing` (String)
  - `character` (String), `occupation` (String), `voice` (String)
  - `personality` (String), `quirksText` (String)
  - `soulTraits` (String), `soulQuirk` (String), `relationType` (String)
  - `petType` (String), `petName` (String), `mood` (String)
  - `createdBy` (Long), `updatedBy` (Long)
  - `createdAt` (LocalDateTime), `updatedAt` (LocalDateTime)
- Use `@TableField(fill = FieldFill.INSERT)` for `createdBy`/`createdAt`
- Use `@TableField(fill = FieldFill.UPDATE)` for `updatedBy`/`updatedAt`

### CompanionDao
- `@Mapper public interface CompanionDao extends BaseMapper<CompanionEntity> {}`

**Exit criteria**: Entity fields match DDL columns; DAO compiles.

---

## Step 3: DTOs + VO

**Depends on**: Step 2
**Files to create**:
- `main/manager-api/src/main/java/xiaozhi/modules/companion/dto/CompanionCreateDTO.java`
- `main/manager-api/src/main/java/xiaozhi/modules/companion/dto/CompanionUpdateDTO.java`
- `main/manager-api/src/main/java/xiaozhi/modules/companion/vo/CompanionVO.java`

**Tasks**:

### CompanionCreateDTO (前端传入字段)
- `@Data` + `@Schema` annotations
- Required fields (with `@NotBlank`): type(String), userId(Long), deviceId(String), avatar(String), defaultImage(String), character(String), occupation(String), voice(String), soulTraits(String), soulQuirk(String), relationType(String), petType(String), petName(String)
- Optional field: quirksText(String)

### CompanionUpdateDTO (前端可修改字段)
- Required: deviceId(String) — 用于定位记录
- All updatable fields as optional: avatar, defaultImage, character, occupation, voice, quirksText, soulTraits, soulQuirk, relationType, petType, petName, personality, mood, type

### CompanionVO (返回给前端)
- All entity fields exposed
- Private static `toVO(CompanionEntity entity)` static method for mapping

**Exit criteria**: DTOs have validation annotations; VO has toVO mapper.

---

## Step 4: Utility Classes

**Depends on**: None (can parallel with Step 2/3)
**Files to create**:
- `main/manager-api/src/main/java/xiaozhi/modules/companion/util/CharacterAge.java`
- `main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionBirthCalculator.java`
- `main/manager-api/src/main/java/xiaozhi/modules/companion/util/CompanionMood.java`

**Tasks**:

### CharacterAge (enum or static map)
- Maps character code → age: `linjiamei=18`, `erciyuan=20`, `baiyueguang=22`, `zhixingyujie=25`
- Method: `static int getAge(String characterCode)` — throws if unknown

### CompanionBirthCalculator
- **Reference**: `pet/util/PetBirthCalculator.java` — uses `com.nlf.calendar` library
- Method: `static BirthResult calculate(LocalDateTime birthTime)`
- Returns a record: `BirthResult(String bazi, String wuxing, String zodiac, String chineseZodiac)`
- Logic (from PetBirthCalculator):
  1. Convert `LocalDateTime` → `Solar` → `Lunar` → `EightChar`
  2. Extract bazi (year/month/day/hour pillars) as JSON string
  3. Count wuxing elements (metal/wood/water/fire/earth) as JSON string
  4. Calculate western zodiac from month/day
  5. Calculate chinese_zodiac from lunar year branch
- Can either reuse PetBirthCalculator directly (extract to shared util) or create a companion-specific copy. **Recommend: extract shared util to a common package, but for now copy the logic to avoid cross-module coupling.**

### CompanionMood
- **Reference**: `pet/util/PetMood.java`
- Enum with mood options: JOY("愉快"), CALM("平静"), EXCITEMENT("兴奋"), CURIOSITY("好奇"), CARE("关怀"), ANXIETY("焦虑"), FRUSTRATION("沮丧"), FATIGUE("疲惫")
- Default mood for companion creation: `CALM` ("平静")
- Can reuse PetMood or create a companion copy. **Recommend: reference PetMood.CALM.name() for default, or create a simple enum.**

**Exit criteria**: Unit-testable utilities with no Spring dependency.

---

## Step 5: Service Layer

**Depends on**: Step 2, Step 3, Step 4
**Files to create**:
- `main/manager-api/src/main/java/xiaozhi/modules/companion/service/CompanionService.java`
- `main/manager-api/src/main/java/xiaozhi/modules/companion/service/impl/CompanionServiceImpl.java`

**Tasks**:

### CompanionService (interface)
```java
public interface CompanionService extends BaseService<CompanionEntity> {
    CompanionVO create(CompanionCreateDTO dto);
    CompanionVO update(CompanionUpdateDTO dto);
    CompanionVO getByDeviceId(String deviceId);
}
```

### CompanionServiceImpl

**Dependencies** (injected via `@AllArgsConstructor`):
- `CompanionDao`
- `LLMService` (from `modules/llm/service/LLMService`)

**create(CompanionCreateDTO dto)** flow:
1. Validate device_id uniqueness — if companion already exists for this device, throw error (COMPANION_ALREADY_EXISTS)
2. Determine age from `CharacterAge.getAge(dto.getCharacter())`
3. Calculate birthday: `LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusYears(age)`
4. Calculate bazi/wuxing/zodiac/chineseZodiac: `CompanionBirthCalculator.calculate(birthday)`
5. Generate personality via LLM: construct prompt from character + soulTraits + soulQuirk + relationType, call `llmService.generateSummary("", prompt)`, truncate to 200 chars
6. Set mood = "CALM" (平静)
7. Build CompanionEntity, set all fields
8. Insert via `companionDao.insert(entity)`
9. Return `CompanionVO.toVO(entity)`

**update(CompanionUpdateDTO dto)** flow:
1. Find companion by deviceId, throw if not found (COMPANION_NOT_FOUND)
2. Update only non-null fields from DTO
3. If character changed → recalculate birthday/bazi/wuxing/zodiac/personality
4. If soulTraits/soulQuirk/relationType changed → regenerate personality via LLM
5. Update via `companionDao.updateById(entity)`
6. Return updated VO

**getByDeviceId(String deviceId)** flow:
1. Query by device_id: `companionDao.selectOne(new QueryWrapper<CompanionEntity>().eq("device_id", deviceId))`
2. Throw if not found
3. Return `CompanionVO.toVO(entity)`

**LLM prompt for personality generation** (draft):
```
你是一个性格分析师。请根据以下信息，用200字以内描述这个角色的性格特点：
- 角色：{character}
- 灵魂特质：{soulTraits}
- 小任性：{soulQuirk}
- 关系类型：{relationType}
要求：语言自然流畅，突出个性特点，避免模板化描述。只输出性格描述文本，不要输出其他内容。
```

**Exit criteria**: All 3 methods compile; LLM call has graceful fallback (if LLM unavailable, use a default personality template).

---

## Step 6: Controller

**Depends on**: Step 5
**Files to create**:
- `main/manager-api/src/main/java/xiaozhi/modules/companion/controller/CompanionController.java`

**Tasks**:

```java
@RestController
@RequestMapping("/companion")
@AllArgsConstructor
@Tag(name = "完美女友管理")
public class CompanionController {

    private final CompanionService companionService;

    @PostMapping("/create")
    @Operation(summary = "创建女友")
    public Result<CompanionVO> create(@RequestBody @Valid CompanionCreateDTO dto) {
        return new Result<CompanionVO>().ok(companionService.create(dto));
    }

    @PostMapping("/update")
    @Operation(summary = "修改女友")
    public Result<CompanionVO> update(@RequestBody @Valid CompanionUpdateDTO dto) {
        return new Result<CompanionVO>().ok(companionService.update(dto));
    }

    @GetMapping("/detail/{deviceId}")
    @Operation(summary = "根据设备ID查询女友")
    public Result<CompanionVO> detail(@PathVariable String deviceId) {
        return new Result<CompanionVO>().ok(companionService.getByDeviceId(deviceId));
    }
}
```

**Exit criteria**: Swagger docs visible at /xiaozhi/doc.html; endpoints match spec.

---

## Step 7: Shiro Config + Error Codes

**Depends on**: Step 6
**Files to modify**:
- `main/manager-api/src/main/java/xiaozhi/modules/security/config/ShiroConfig.java`
- `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java`

**Tasks**:

### ShiroConfig — add route filters:
```java
filterMap.put("/companion/create", "anon");          // 小程序创建，无需登录
filterMap.put("/companion/update", "anon");           // 小程序修改
filterMap.put("/companion/detail/**", "server");      // xiaozhi-server 查询
```
Note: Route permission strategy should match pet module pattern. If companion creation comes from the miniprogram (similar to pet/birth), use "anon". Adjust if auth requirements differ.

### ErrorCode — add companion error codes:
```java
int COMPANION_NOT_FOUND = 10210;
int COMPANION_ALREADY_EXISTS = 10211;
int COMPANION_INVALID_CHARACTER = 10212;
```

**Exit criteria**: Routes accessible according to config; error codes compile.

---

## Step 8: Verification

**Depends on**: All previous steps
**Tasks**:
1. Start the application: verify Liquibase migration runs successfully
2. Verify Swagger UI at `http://localhost:8002/xiaozhi/doc.html` shows companion endpoints
3. Test create API with curl (skip LLM if unavailable):
   ```bash
   curl -X POST http://localhost:8002/xiaozhi/companion/create \
     -H "Content-Type: application/json" \
     -d '{
       "type": "gf",
       "userId": 1,
       "deviceId": "test-device-001",
       "avatar": "https://example.com/avatar.jpg",
       "defaultImage": "https://example.com/default.jpg",
       "character": "linjiamei",
       "occupation": "design",
       "voice": "wenruo",
       "soulTraits": "粘人精,护短狂魔",
       "soulQuirk": "小醋坛子",
       "relationType": "qingmeizhuma",
       "petType": "cat",
       "petName": "小橘"
     }'
   ```
4. Verify: birthday ≈ 2008-06-04 (current - 18 years), zodiac/wuxing/bazi populated, personality generated or defaulted, mood = "CALM"
5. Test query by device_id: `GET /xiaozhi/companion/detail/test-device-001`
6. Test update: change a field, verify updated_at changes
7. Test duplicate device_id creation: should return error

**Exit criteria**: All 3 endpoints return correct responses; auto-generated fields are populated; error cases handled.

---

## Dependency Graph

```
Step 1 (DB Migration)
  |
  v
Step 2 (Entity + DAO)
  |         \
  v          v
Step 3 (DTO/VO)   Step 4 (Utils)  ← can run in parallel
  |         /          |
  v        v           |
Step 5 (Service)  -----+
  |
  v
Step 6 (Controller)
  |
  v
Step 7 (Shiro + ErrorCodes)
  |
  v
Step 8 (Verification)
```

**Parallel opportunities**: Step 3 and Step 4 can run in parallel (no shared files).

---

## Rollback Strategy

Each step is independently reversible:
- Step 1: `DROP TABLE ai_companion;` + remove changeSet entry
- Steps 2-7: Delete the created Java files, revert modified files
- Step 8: No side effects

---

## File Inventory

### New files (12):
```
main/manager-api/src/main/resources/db/changelog/202606041000.sql
main/manager-api/src/main/java/xiaozhi/modules/companion/
├── controller/CompanionController.java
├── dao/CompanionDao.java
├── dto/CompanionCreateDTO.java
├── dto/CompanionUpdateDTO.java
├── entity/CompanionEntity.java
├── service/CompanionService.java
├── service/impl/CompanionServiceImpl.java
├── util/CharacterAge.java
├── util/CompanionBirthCalculator.java
├── util/CompanionMood.java
└── vo/CompanionVO.java
```

### Modified files (2):
```
main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml
main/manager-api/src/main/java/xiaozhi/modules/security/config/ShiroConfig.java
main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java
```
