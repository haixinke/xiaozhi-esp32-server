# Research Notes — 游戏分类与外部游戏跳转

Scoping for tickets `01-04` of `.scratch/game-category-and-external-launch/spec.md`. Concrete `path:line` references; verbatim quotes kept minimal but load-bearing.

---

## 1. manager-api (Java) — game module

### Entity / table mapping
- `main/manager-api/src/main/java/xiaozhi/modules/game/entity/GameEntity.java:19` — `@TableName("ai_game")`. Extends `BaseEntity` (id, creator, createDate). Fields: `gameType` (L25), `earnStars`, `consumeStars`, `dailyLimit`, `icon`, `description`, `entrances` (comma-separated `GameEntranceEnum` codes, L55), `status`, `updater`, `updateDate`. **No `gameCategory` / `jumpUrl` yet — Liquibase + entity change required.**
- Table DDL in `main/manager-api/src/main/resources/db/changelog/202610161000.sql` — creates `ai_game` plus the `GAME_TYPE` dict type (`id=106`) and 5 seed dict data rows (`DRAW_TOGETHER`, `LISTEN_TO_YOU`, `MEET_YOU`, `TRAVEL`, `WORK`).
- `202610171000.sql` — adds `uk_ai_game_game_type` unique index.
- The DB layer is **not** soft-delete; `GameServiceImpl.delete` is `deleteById` (physical).

### DAO
- `main/manager-api/src/main/java/xiaozhi/modules/game/dao/GameDao.java:13` — extends `BaseDao<GameEntity>`. Only custom query: `selectByGameType(@Param("gameType"))` at L20 (`SELECT * FROM ai_game WHERE game_type = #{gameType}`), used by settlement to find config by type.

### Service interface + impl
- `main/manager-api/src/main/java/xiaozhi/modules/game/service/GameService.java` — interface with `page/get/save/update/delete/listEnabled(String entrance)`.
- `main/manager-api/src/main/java/xiaozhi/modules/game/service/impl/GameServiceImpl.java`:
  - L42 `page(params)` — `QueryWrapper` with optional `like(game_type)`.
  - L56 `get(Long id)` — throws `RenException(ErrorCode.GAME_NOT_FOUND)` on miss.
  - L64 `save(GameDTO)` — calls `validate(dto)`, then `ensureGameTypeUnique(dto.getGameType(), null)`, then `insert`. `DuplicateKeyException` → `GAME_TYPE_ALREADY_EXISTS`.
  - L79 `update(GameDTO)` — same shape; also `selectById` precondition to throw `GAME_NOT_FOUND`.
  - L104 `listEnabled(String entrance)` — `where status=1` + `FIND_IN_SET({0}, entrances)` for entrance filter, ordered by id asc. **This is the single source for both `/app/game/list` and the daily-note random pick.**
  - L123 `fillGameTypeName(list)` — fills `gameTypeName` from dict `GAME_TYPE`, falls back to raw code if missing.
  - L161 `validate(GameDTO)` — manual checks in Java code (no `@Valid` here). Order: dict lookup → entrances non-blank & each code in `GameEntranceEnum.codes()` → `earnStars/consumeStars/dailyLimit >= 0` → earn/consume mutually exclusive (both >0 → `GAME_EARN_CONSUME_CONFLICT`). Validation annotations on the DTO (`@NotNull`, `@Min`) are applied separately at the controller via `ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class)`.

### Controllers
- Admin: `main/manager-api/src/main/java/xiaozhi/modules/game/controller/GameController.java` — `@RequestMapping("/admin/game")`, `@RequiresPermissions("sys:role:superAdmin")` on every method. Endpoints:
  - `GET /page` (L43) — params via `@RequestParam Map<String,Object>`; calls `ValidatorUtils.validateEntity(params)` then `gameService.page(params)`.
  - `GET /{id}` (L55) — `gameService.get(id)`.
  - `POST /save` (L63) — `validateEntity(dto, AddGroup.class, DefaultGroup.class)` then `save`.
  - `PUT /update` (L72) — `validateEntity(dto, UpdateGroup.class, DefaultGroup.class)` then `update`.
  - `DELETE /delete/{id}` (L81).
- App-facing: `main/manager-api/src/main/java/xiaozhi/modules/game/controller/GameAppController.java` — `@RequestMapping("/app/game")`, `@RequiresPermissions("sys:role:normal")`. Only endpoint: `GET /list?entrance=...` at L31 → `gameService.listEnabled(entrance)`. **Returns `List<GameVO>` directly — same VO as admin.**

### DTO / VO
- `main/manager-api/src/main/java/xiaozhi/modules/game/dto/GameDTO.java` — used for both save & update. Bean-Validation groups: `id` is `@Null` on Add, `@NotNull` on Update; `gameType` `@NotBlank`; `earnStars`/`consumeStars`/`dailyLimit` `@NotNull + @Min(0)`; `entrances` `@NotBlank`. `icon`/`description` optional. **Add `gameCategory` (with optional default-to-INTERNAL behaviour in service) and `jumpUrl` here.**
- `main/manager-api/src/main/java/xiaozhi/modules/game/vo/GameVO.java` — admin/app-facing VO. Same VO is reused by `DailyNoteTodayVO.game`. **Add `gameCategory`, `jumpUrl` here.** Note: `gameTypeName` is only filled in `listEnabled`; `get(id)`/`page()` do not fill it today (callers do their own dict lookup).
- `main/manager-api/src/main/java/xiaozhi/modules/game/enums/GameEntranceEnum.java` — `NOTE` / `ACTIVITY`. `codes()` returns `Set<String>` used by validator.

### GAME_TYPE dict
- Loaded via `SysDictDataService.getDictDataByType(Constant.DictType.GAME_TYPE.getValue())` (`Constant.DictType.GAME_TYPE = "GAME_TYPE"`, `main/manager-api/src/main/java/xiaozhi/common/constant/Constant.java:385`).
- Seed rows in `202610161000.sql:30-35`.
- Validation entry point: `GameServiceImpl.validate` L162-168.

### Daily note today (random pick + nested game)
- `main/manager-api/src/main/java/xiaozhi/modules/dailynote/service/impl/DailyNoteTodayServiceImpl.java`:
  - L83 — `List<GameVO> games = gameService.listEnabled(GameEntranceEnum.NOTE.name());` — **reuses `GameService.listEnabled`, so the nested `game` model is the same `GameVO` as `/app/game/list`.**
  - L95 — `vo.setGame(games.get(ThreadLocalRandom.current().nextInt(games.size())));` — random on every call, not persisted.
- `main/manager-api/src/main/java/xiaozhi/modules/dailynote/vo/DailyNoteTodayVO.java:31` — `private GameVO game;` — **shares the game response model; no separate VO.**
- Controller: `main/manager-api/src/main/java/xiaozhi/modules/dailynote/controller/DailyNoteAppController.java` — `GET /app/daily-note/today` at L34, `POST /app/daily-note/opened` at L42 (both `sys:role:normal`).

### ErrorCode conventions
- All game codes in `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java:310-324`. Existing game block uses `10350-10358`; settlement uses `10368+`. **Next free in the `1035x` block is `10359` (single slot — could be exceeded); consider jumping to a fresh sub-block (e.g., reuse pattern from listen at `10370+` / `10380+`).**
- i18n keys live in `main/manager-api/src/main/resources/i18n/messages*.properties` (e.g., `10350=游戏不存在` in `messages.properties:230`). Multiple locale files exist — Chinese is primary; check `messages_en_US.properties` etc. when adding keys.

### Validation conventions used
- Bean-Validation annotations on DTO with groups (`AddGroup`, `UpdateGroup`, `DefaultGroup`); applied at controller via `ValidatorUtils.validateEntity(...)`.
- Cross-field / business rules in service (`GameServiceImpl.validate`) throwing `RenException(ErrorCode.XXX)`.
- `SysDictDataService.getDictDataByType` used as a runtime "enum from DB" for `GAME_TYPE` (with Redis cache via `sysDictDataService.save/delete` invalidation — see `GameServiceTest.java:106`).

---

## 2. Liquibase

- Master changelog: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`. Top comment (L1-3) spells the convention:
  - `id` = current date-time in `yyyyMMddHHmm` (e.g., `202611051000`).
  - File name = same id + `.sql` under `db/changelog/`.
  - Never edit an existing changeset — add a new one.
- `author` values vary by contributor (`minwang`, `hrz`, …). Use your git user.
- Each changeset is a single `sqlFile` with `encoding: utf8` and `path: classpath:db/changelog/<id>.sql`.
- Latest changeset id at time of writing: `202611051000`. New id should be later (today's `yyyyMMddHHmm`).
- Game-related changesets: `202610161000.sql` (creates `ai_game`), `202610171000.sql` (adds `uk_ai_game_game_type`). No `game_category`/`jump_url` column exists yet — ticket 01 must add both via a new changeset, with `game_category` `NOT NULL DEFAULT 'INTERNAL'` so existing rows backfill automatically.

### Migration testing convention
- `main/manager-api/src/test/java/xiaozhi/modules/listen/ListenMigrationIntegrationTest.java` — pattern for Liquibase-migration tests:
  - Plain JUnit 5 (no Spring context).
  - Uses `JdbcTemplate` against a dedicated test schema (`egg_listen_test`), never the dev DB.
  - `TEST_DATABASE = "egg_listen_test"` at L34; JDBC URL with `DB_HOST` env override (default `127.0.0.1:2881`, OceanBase MySQL-compatible, `root`/`123456` — read from `application-dev.yml`).
  - `Path.of("src/main/resources/db/changelog/<id>.sql")` and executes it directly.
  - `tableExists` / `tableColumns` helpers.
- For the game-category migration, mirror this with a `GameMigrationIntegrationTest` that pre-creates `ai_game` with the old schema, runs the new changeset, and asserts the column shape + that existing rows now have `game_category='INTERNAL'`.

---

## 3. manager-api tests

### Style / framework
- **JUnit 5** (`org.junit.jupiter.api.Test`, `@DisplayName`, `@BeforeEach/@AfterEach`, `@BeforeAll/@AfterAll`, `@TestInstance(PER_CLASS)` when needed).
- **Spring Boot integration tests** with `@SpringBootTest` + `@ActiveProfiles("dev")` + `@Import(TestArkServiceConfig.class)`. Real dev DB (OceanBase at `127.0.0.1:2881`), not H2.
- **AssertJ** (`assertThat(...)`, `assertThatThrownBy(...)`).
- **No Mockito in service-level game tests** — the tests hit the real DB and inject controlled test data (segregated by `TEST_GAME_TYPES`, dedicated user IDs like `9_999_000_001L`, or date range `2160-*`).

### Test data isolation conventions
- `main/manager-api/src/test/java/xiaozhi/modules/game/GameServiceTest.java` — see file header (L44-58) and `ensureTestGameTypes`/`removeTestGameTypes`:
  - Injects dedicated `TEST_GAME_A/B/C` dict entries into `GAME_TYPE` at `@BeforeAll` (uses `sysDictDataService.save` so Redis cache invalidates) and removes them at `@AfterAll`.
  - Tracks per-test IDs in `touchedIds` list and physically deletes them in `@AfterEach`.
  - **Never uses real `DRAW_TOGETHER` / `LISTEN_TO_YOU` in tests** — would collide with the unique index.
- `main/manager-api/src/test/java/xiaozhi/modules/dailynote/DailyNoteTodayServiceTest.java`:
  - Uses a fixed Asia/Shanghai `Clock` injected via `((DailyNoteTodayServiceImpl) dailyNoteTodayService).setClock(...)` and a far-future date (e.g., `2160-01-10`) to avoid touching real data.
  - `sidelinePreExistingNoteGames` (L124) temporarily disables enabled NOTE games and restores them in `cleanup` — required because random pick draws from real DB.
  - Uses `MEET_YOU`/`TRAVEL`/`WORK` dict codes (unoccupied real types) — see `insertGame` L154.
  - Dedicated user IDs `USER_A=9_999_000_001L`, `USER_B=9_999_000_002L`.

### Run commands (from `main/manager-api/CLAUDE.md`)
- Full: `mvn test -DskipTests=false`
- Single class: `mvn test -Dtest=GameServiceTest -DskipTests=false`
- Single method: `mvn test -Dtest=GameServiceTest#save_normal_createsRecord -DskipTests=false`
- **Requires MySQL/Redis running** (dev profile reads `application-dev.yml`).

### Existing test classes relevant to tickets 01–03
- `main/manager-api/src/test/java/xiaozhi/modules/game/GameServiceTest.java` — full CRUD + validation + `listEnabled` coverage.
- `main/manager-api/src/test/java/xiaozhi/modules/game/GameStarRewardServiceTest.java`, `GameStarChargeServiceTest.java`, `GamePlayLimitServiceTest.java` — settlement flows that read `ai_game` via `selectByGameType`. Should stay green without change if external games are never settled (all-zero config).
- `main/manager-api/src/test/java/xiaozhi/modules/dailynote/DailyNoteTodayServiceTest.java` — covers `today()` (random game pick, edge cases).
- `main/manager-api/src/test/java/xiaozhi/modules/dailynote/DailyNoteOpenedServiceTest.java` + `DailyNoteOpenedRewardTest.java` — opened / reward flow.
- `main/manager-api/src/test/java/xiaozhi/modules/dailynote/DailyNoteAppControllerTest.java` — controller wiring.
- `main/manager-api/src/test/java/xiaozhi/modules/listen/ListenMigrationIntegrationTest.java` + `ListenSchemaContractTest.java` — **template for the new game migration tests**.

---

## 4. manager-web (Vue 2) 智控台

### Game management page
- `main/manager-web/src/views/GameManagement.vue`:
  - Table columns set in `created()` at L163-172 (`gameType`, `icon`, `earnStars`, `consumeStars`, `dailyLimit`, `entrances`, `status`). **No `gameCategory` column yet.**
  - `gameTypeOptions` loaded in `fetchGameTypeOptions` (L192) via `Api.dict.getDictDataByType('GAME_TYPE')`.
  - `entrances` edit uses `<el-checkbox-group v-model="editForm.entranceList">` with hard-coded options `NOTE` / `ACTIVITY` (L83-87). Display label mapping in `entrancesLabel` (L207).
  - `handleEdit` (L250) — fetches `Api.game.getGameDetail(row.id, ...)` and re-hydrates `editForm` (note: `entranceList` derived from `d.entrances.split(',')`).
  - `handleToggleStatus` (L272) — sends a full update payload using row's current values + flipped status. **This is the path that must round-trip `gameCategory` + `jumpUrl` to avoid wiping them on toggle.**
  - `handleSave` (L338) — manual checks (`gameType` non-empty, `entranceList.length > 0`) via `this.$message.warning`. **No element-ui `rules` / `prop` validation is used here** — manual `if` + toast. New URL validation should follow the same pattern.
  - Save/update payload built at L349-358; `entrances` joined from `entranceList`.

### API module
- `main/manager-web/src/apis/module/game.js` — wraps `RequestService.sendRequest()` for `/admin/game/page`, `/admin/game/{id}`, `/admin/game/save`, `/admin/game/update`, `/admin/game/delete/{id}`, `/upload/image?scene=game-icon`. **Add no new endpoints — `gameCategory` and `jumpUrl` flow through the existing `data` payload.**
- Registered in `main/manager-web/src/apis/api.js:18` (`import game from './module/game.js'`) and L59 (`game,` in default export).

### Tests
- **No existing test file for `GameManagement.vue`** (verified by listing `main/manager-web/tests/`). Pattern from sibling admin pages: tests live in `main/manager-web/tests/*.test.mjs` and use `node --test` + `assert.match` against raw source to enforce wiring (see `tests/dailyNoteManagement.test.mjs` — pure source regression, no DOM render).
- Run: `npm run test:unit` → `node --test tests/*.test.mjs`. There is also `npm run test:snapshot` for a focused pair.
- Build: `npm run build` (vue-cli-service). Lint: `npm run check:i18n`, `npm run check:template -- src/views/foo.vue` (PostToolUse hook runs `check-vue-template.js` automatically).

---

## 5. egg-miniprogram (微信小程序)

### 百宝箱活动 page (activity list)
- **Path**: `main/egg-miniprogram/miniprogram/pages/iaa-activities/iaa-activities.{js,wxml,wxss,json}`. Registered in `app.json:29` as `pages/iaa-activities/iaa-activities`.
- `iaa-activities.js`:
  - `loadGames` (L56) → `gameApi.listActivityGames()` → `GET /app/game/list?entrance=ACTIVITY` (`utils/game-api.js:5`).
  - `gameRow` mapper (L7-16) computes `ctaText`: `earn>0 → '赚 N'`, `cost>0 → '花 N'`, else `'去玩'`. **External games (all-zero) already fall into the "去玩" branch — keep this.**
  - `onTapRow` (L87-104):
    ```js
    if (game.gameType === 'DRAW_TOGETHER') { openDrawing(petStore.getPetSnapshot().pet, 'activity'); return; }
    if (game.gameType !== 'LISTEN_TO_YOU') { wx.showToast({ title: '暂未开放', icon: 'none' }); return; }
    wx.navigateTo({ url: '/pages/listen/listen', ... });
    ```
    **This is the branching point to insert `gameCategory === 'EXTERNAL'` → `wx.navigateTo({ url: '/pages/webview/webview?url=...' })`.** For backward compat (older responses without `gameCategory`), keep the current `gameType`-driven fallthrough unchanged.
  - Hatching gating: `onShow` (L28-39) bounces out with `wx.switchTab` if pet not `HATCHED`. This gate must stay; ticket says "沿用现有破壳与手机号约束". `withPhoneGate` wraps the whole `Page({...})` at L18.
- `iaa-activities.wxml:25` — row tap binding `bindtap="onTapRow"`.

### 首页小纸条 (daily note on home)
- `main/egg-miniprogram/miniprogram/pages/home/home.js`:
  - L11 — `const dailyNoteApi = require('../../utils/daily-note-api');`
  - L12 — `const { openDrawing } = require('../../utils/drawing-entry');`
  - `loadDailyNote` at L416 — calls `dailyNoteApi.getToday()` when `shouldLoad` (hatched + !phoneRequired); stores in `this.data.dailyNote`.
  - `onDailyNoteTap` at L482 — opens overlay, then `markOpened()` for reward. Snapshot stored in `dailyNoteSnapshot` (L486) so closing the overlay can clear `dailyNote` independently.
  - `onDailyNoteGameTap` at L509-521 — **the second branching point**:
    ```js
    if (game.gameType === 'DRAW_TOGETHER') { openDrawing(pet, 'letter', () => this.onDailyNoteClose()); return; }
    if (game.gameType === 'LISTEN_TO_YOU') { this.onDailyNoteClose(); wx.navigateTo({ url: '/pages/listen/listen' }); return; }
    wx.showToast({ title: '敬请期待', icon: 'none' });
    ```
    Insert the `gameCategory === 'EXTERNAL'` branch here. Note: the source `'letter'` is the magic string `drawing-entry.js` maps to the drawing page query.
- `main/egg-miniprogram/miniprogram/pages/home/home.wxml:158` — the game CTA row binding `bindtap="onDailyNoteGameTap"`; only renders when `dailyNoteSnapshot.game` exists.
- `main/egg-miniprogram/miniprogram/utils/daily-note-api.js` — wraps `GET /app/daily-note/today` and `POST /app/daily-note/opened`.
- `main/egg-miniprogram/miniprogram/utils/drawing-entry.js` — `openDrawing(pet, source, beforeNavigate)`. Source is either `'letter'` or `'activity'`; URL: `/drawing/pages/draw/draw?source=${source === 'letter' ? 'letter' : 'activity'}` (L13).

### Navigation / routing conventions
- Pages registered in `main/egg-miniprogram/miniprogram/app.json` `pages[]` array (L2-30); subPackages supported (drawing at L31-33). **The new `web-view` page must be added here, e.g., `pages/webview/webview`.**
- `wx.navigateTo({ url })` is the standard. `fail` callback commonly shows `wx.showToast({ title: '暂时打不开，请稍后再试', icon: 'none' })` (see `iaa-activities.js:101`).
- All pages use `navigationStyle: custom` (`app.json:46`), so they need the `nav-bar` component (`pages/iaa-activities/iaa-activities.json:4` shows the standard `usingComponents: { "nav-bar": ... }`).

### web-view usage
- **No existing `web-view` usage in the project.** Verified by `grep -l "web-view" miniprogram/**/*.wxml` (no matches). The new `pages/webview/webview` page will be the first.
- WeChat `web-view` requires the target domain to be in the miniprogram's "业务域名" allowlist; the spec calls this out as Out of Scope for automation (manual one-time setup in WeChat MP console). Dev tool can bypass via `setting.urlCheck: false` (already set in `project.config.json:7`) — but **真机验收必须开启正常域名校验** (ticket 04 acceptance).

### Tests / 工程完整性校验
- **Test runner**: plain Node with `assert`, no Jest. Tests live next to the code as `*.test.js` and are run with `node path/to/file.test.js`. See existing tests for both target pages:
  - `main/egg-miniprogram/miniprogram/pages/iaa-activities/iaa-activities.test.js` — stubs `Page`, `wx`, and `Module._load` to inject mocks for `phone-gate`, `pet-store`, and `./request`. Pattern to mirror for the external-game branch.
  - `main/egg-miniprogram/miniprogram/pages/home/home.test.js` — same Module-stubbing pattern; has daily-note mocks at `dailyNoteTodayResult` / `dailyNoteOpenedResult` (L74-79), already exercises the overlay game CTA.
- **工程完整性校验**: `node main/egg-miniprogram/scripts/verify-project.js` — checks every page in `app.json` has all 4 files (`.js/.json/.wxml/.wxss`), validates `usingComponents` references, and rejects forbidden HTML tags in WXML. New webview page must pass.
- Syntax check: `find main/egg-miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check`.

### Project config
- `main/egg-miniprogram/project.config.json` — `appid: wx661d20e88437af73`, `miniprogramRoot: "miniprogram/"`, `libVersion: "3.15.2"`. `setting.urlCheck: false` (dev only — does not affect production domain allowlist).
- No existing `permission` or `networkTimeout` overrides; default behavior applies.

---

## 6. Daily note reward flow (must stay untouched)

- `main/manager-api/src/main/java/xiaozhi/modules/dailynote/service/impl/DailyNoteOpenedServiceImpl.java`:
  - `markOpened(Long userId)` at L62-101. Idempotent via `(user_id, note_date)` unique key.
  - L100 — `starDailyRewardService.claimDailyReward(userId, DAILY_NOTE_SOURCE, DAILY_NOTE_SOURCE);` — same-transaction reward grant (first-day 11, consecutive +1, cap 17, break resets to 11).
  - **Reward is independent of the drawn game's category.** Trigger is "user opened today's note", period. No code change needed; just don't break the contract that `today()` returns one game and `opened()` grants the streak reward.
- Test class to keep green: `main/manager-api/src/test/java/xiaozhi/modules/dailynote/DailyNoteOpenedRewardTest.java`.

---

## 7. web-view domain restrictions / business domains config

- **No business-domain config in this repo** — WeChat business domains are configured in the WeChat MP console (mp.weixin.qq.com), not in `project.config.json` or any project file. Spec L72 marks "自动配置微信公众平台业务域名" as out of scope.
- Practical implication for code:
  - The webview page should not try to "validate" the URL against an allowlist client-side; backend already validates HTTPS shape (spec L42).
  - On failure, WeChat fires the `binderror` event on `<web-view>`; handlers should surface a user-friendly toast and offer a back path (spec L56 / ticket 02 acceptance).
- Reference in spec L77: 腾讯云小程序业务域名配置指南 (https://cloud.tencent.com/document/product/1301/100434).

---

## Suggested implementation map

### Ticket 01 — 内部游戏分类与存量兼容

**Backend (manager-api)**:
- New changeset `db/changelog/<today>.sql` + register in `db.changelog-master.yaml`. Adds `game_category VARCHAR(16) NOT NULL DEFAULT 'INTERNAL'` and `jump_url VARCHAR(1024) NULL` to `ai_game`.
- New `GameCategoryEnum` (`INTERNAL`, `EXTERNAL`) under `xiaozhi/modules/game/enums/`.
- `GameEntity.java` — add `gameCategory`, `jumpUrl` fields.
- `GameDTO.java` — add `gameCategory` (no `@NotBlank`; service defaults missing → INTERNAL for backward compat) and `jumpUrl` (optional).
- `GameVO.java` — add `gameCategory`, `jumpUrl`.
- `GameServiceImpl.validate` — accept the resolved category; enforce:
  - unknown category → new error code
  - `EXTERNAL` requires non-blank `jumpUrl`, valid HTTPS URL, no userinfo
  - `EXTERNAL` forces `earnStars = consumeStars = dailyLimit = 0` (reject otherwise)
  - `DRAW_TOGETHER` / `LISTEN_TO_YOU` must be `INTERNAL`
  - saving as `INTERNAL` clears `jumpUrl` (set null before persist)
- `GameServiceImpl.save/update` — apply default + category-specific rules.
- `ErrorCode.java` + `i18n/messages*.properties` — new codes (recommend a fresh sub-block, e.g., `10396+` or extend `1035x`).

**Tests**:
- New `GameMigrationIntegrationTest` mirroring `ListenMigrationIntegrationTest` for the schema change.
- Extend `GameServiceTest` with cases: default category on missing field, INTERNAL preservation on legacy update, EXTERNAL validation failures, INTERNAL→link cleared, EXTERNAL→INTERNAL→EXTERNAL round-trip, DRAW_TOGETHER/LISTEN_TO_YOU forced INTERNAL.
- Extend `DailyNoteTodayServiceTest` to assert nested `game.gameCategory` / `jumpUrl` round-trip.

**Frontend (manager-web)**:
- `views/GameManagement.vue`:
  - New table column `gameCategory` (显示"内部游戏"/"外部游戏").
  - `emptyForm()` defaults `gameCategory: 'INTERNAL'`.
  - `handleEdit` rehydrates `gameCategory` + `jumpUrl`.
  - Form: new `游戏分类` radio; conditional `跳转链接` input shown when `EXTERNAL`; hide stars/dailyLimit inputs for EXTERNAL (or pin to 0 — pick the same UX the spec lands on); prevent `EXTERNAL` when `gameType` is `DRAW_TOGETHER`/`LISTEN_TO_YOU`.
  - `handleToggleStatus` must include `gameCategory` + `jumpUrl` in payload (otherwise toggle wipes them).
  - `handleSave` includes new fields; manual URL format check before submit.
- New test `tests/gameManagement.test.mjs` (source-regression style, like `dailyNoteManagement.test.mjs`).

**Frontend (egg-miniprogram)**:
- `pages/iaa-activities/iaa-activities.js` `onTapRow` — branch on `game.gameCategory === 'EXTERNAL'` first; fall through to existing gameType dispatch otherwise.
- `pages/home/home.js` `onDailyNoteGameTap` — same.
- Tests: extend `iaa-activities.test.js` and `home.test.js` to cover INTERNAL-only behaviour unchanged + missing category treated as INTERNAL.

### Ticket 02 — 外部游戏从百宝箱活动打开

**Backend** (extends 01):
- HTTPS URL validation helper — likely a small private static method or a new `GameJumpUrlValidator` class under `xiaozhi/modules/game/service/impl/`. Spec says: trim → must be valid HTTPS URL → valid hostname → no userinfo; preserve query/fragment.
- Error codes: external-missing-link, external-invalid-link, external-non-zero-stars, internal-reserved-type. Reuse `ErrorCode` block.
- Persist `jumpUrl` after trim.

**Frontend (manager-web)**:
- EXTERNAL form state validation + display. Conditional "跳转链接" input with placeholder + manual check.
- Toggle-status path round-trips the new fields.

**Frontend (egg-miniprogram)**:
- New page `pages/webview/webview.{js,wxml,wxss,json}` — single `<web-view src="{{url}}" binderror="onError" bindload="onLoad">`. Page reads `options.url`, decodes via `decodeURIComponent`, sets `data.url`. On error: `wx.showToast` + offer back navigation. No credentials in URL.
- `app.json` — register the new page (verify-project will enforce four files).
- `iaa-activities.js` `onTapRow` — `EXTERNAL` branch navigates to `/pages/webview/webview?url=${encodeURIComponent(game.jumpUrl)}`.
- Tests: extend `iaa-activities.test.js` to assert `navigateTo` called with the encoded webview URL when `gameCategory === 'EXTERNAL'`; assert invalid URL surfaces toast; assert INTERNAL path unchanged.

### Ticket 03 — 外部游戏从小纸条打开

**Backend**: nothing new — `DailyNoteTodayServiceImpl` already reuses `gameService.listEnabled(NOTE)` and `GameVO`. Test addition in `DailyNoteTodayServiceTest` covering an EXTERNAL game round-trip.

**Frontend (egg-miniprogram)**:
- `pages/home/home.js` `onDailyNoteGameTap` — add EXTERNAL branch (same navigation as activities); keep `'letter'` source passing for DRAW_TOGETHER unchanged.
- Reward path untouched — `onDailyNoteTap` / `markOpened` already independent of game.
- Tests: extend `home.test.js` — EXTERNAL branch navigates, INTERNAL dispatches unchanged, missing category treated as INTERNAL.

### Ticket 04 — 微信真机联调验收

No code. Manual acceptance. **Record results directly in the ticket file** `.scratch/game-category-and-external-launch/issues/04-wechat-device-acceptance.md` — convert each `- [ ]` acceptance bullet to `- [x]` with a short note (test version, H5 sample used, pass/fail). The ticket itself is the record; no separate doc.
