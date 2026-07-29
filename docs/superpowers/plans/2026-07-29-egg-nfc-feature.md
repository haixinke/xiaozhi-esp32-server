# 蛋宝宝 NFC 实物版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变邀请码领养与孵化闭环的前提下，交付一机一码 NFC 实物的生产、Scheme 生成、写卡、库存激活、微信小程序领取和完整审计能力。

**Architecture:** 在 `manager-api` 新增独立的 `xiaozhi.modules.pdc.nfc` 生产域，使用 Liquibase、MyBatis-Plus、数据库租约和事务状态机承载资产全生命周期；只有领取成功时才通过 pet 领域的纯数据库 `createEgg(userId, prototype)` 能力创建 `ai_pet`。`manager-web` 仅向 superAdmin 暴露生产页面，`egg-miniprogram` 通过 30 分钟启动意图恢复 NFC 冷/暖启动链路，工厂继续使用通用 RFC 4180 CSV 与现有写卡设备解耦。

**Tech Stack:** Java 21、Spring Boot 3.4.3、MyBatis-Plus 3.5.17、MySQL 8、Liquibase 4.20、Shiro 2.0.2、Redis、JUnit 5、Mockito、Testcontainers、JaCoCo、Vue 2.6、Element UI、Flyio、微信原生小程序 JS/WXML/WXSS、Node.js 25.2.1 测试覆盖率。

## Global Constraints

- 所有实现和提交只能在 `/Users/minwang/codes/github/xiaozhi-esp32-server/.worktrees/egg-nfc` 的 `feature/egg-nfc` 分支完成；不得修改主工作区的 `egg-dev`。
- 需求基线是 `main/docs/egg-nfc-feature-spec.md`；发生冲突时先修订 Spec 并重新评审，再修改实现计划。
- 生产、写卡、库存和领取核销表全部使用 `pdc_` 前缀；不得复用邀请码表或向 `ai_pet` 增加生产字段。
- 管理核心 Controller 必须在类级标注 `@RequiresPermissions("sys:role:superAdmin")`；领取 Controller 必须在类级标注 `@RequiresPermissions("sys:role:normal")`。
- 领取服务必须显式确认当前用户存在微信映射且手机号非空；不能只依赖 `Oauth2Filter`，因为现有 `WechatPhoneGate.canAccess()` 会放行没有微信映射的后台账号。
- `model_id` 只从 `PDC_NFC_MODEL_ID` 注入，默认空；空值或尖括号形式的说明值必须阻止 Scheme 生成，不得把任何说明值写进数据库或请求微信。
- `pdc.nfc.release-ready`、`enabled`、`scheme-generation-enabled`、`activation-enabled`、`claim-enabled` 默认均为 `false`；开关不能替代权限、状态和发布证据校验。
- `claimRef` 使用至少 128 位 CSPRNG、无填充 Base64URL；查询使用独立 HMAC-SHA-256 密钥，密文使用 AES-256-GCM、随机 nonce、`asset_id` AAD 和密钥版本。
- HMAC 与 AES 密钥不得共享、不得有可工作的默认值、不得出现在日志、异常、管理 VO、CSV 或前端状态中；轮换期必须支持 active/previous 双读。
- 所有实物共用一个微信设备类型，Scheme 固定跳转 `/pages/nfc-claim/nfc-claim`，query 固定为 `v=1&ref=` 加 22 字符 Base64URL，环境固定为 `release`，每件资产使用唯一 `wechat_sn`。
- NFC 写卡内容固定为 URI Record（TNF `0x01`、Type `U`、Payload 为 `openlink`）和 AAR（TNF `0x04`、Type `android.com:pkg`、Payload `com.tencent.mm`）；小程序不得使用 `wx.getNFCAdapter()`。
- 新领取仅允许 `ACTIVE` 资产；preview 永不核销；同一资产只成功领取一次；本人重试返回原宠物；不同用户并发只允许一个成功。
- `createEgg` 只能写 `ai_pet(EGG)`，不得创建 `ai_device`、`ai_agent` 或破壳档案，也不得在 NFC 资产行锁事务中触发 LLM、OSS 或微信外呼。
- 批量入库、激活等管理操作单次最多 500 件，拒绝重复输入；必须稳定排序后加锁、先全量校验、再单事务更新。
- 第一版批次计划数量上限固定为 10,000，通过 `pdc.nfc.max-batch-quantity=10000` 配置；DAO 每次批量插入不超过 500 行，但整个批次创建保持一个事务。
- 写卡结果文件最大 10 MiB、最多 10,000 条数据行、单字段最大 4,096 字符；行数必须与不可变任务快照完全一致。
- 自动化测试对本次新增的后端 `xiaozhi.modules.pdc.nfc`、管理端 NFC 纯逻辑模块和小程序 NFC 模块实行 line/branch 不低于 80% 的门禁。
- Java 命令使用 `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`；Maven 测试必须显式传 `-DskipTests=false`。
- 代码、测试、日志和文档不使用 emoji；提交使用 Conventional Commits，并在每个任务通过验证后做一个小而独立的提交。

---

## 已锁定的补充契约

这些契约补齐 Spec 中隐含但没有单列接口的部分，执行时不再自行改变：

1. 发布证据通过 `POST /pdc/nfc/admin/release-readiness/evidence` 登记，正文为 `releaseVersion`、`publishedAt`、`smokeEvidence`。证据追加写入 `pdc_nfc_operation_log.detail_json`，不修改固定商品类型；Scheme 有效门槛为：运行配置 `release-ready=true` 且存在当前页面版本的最新证据。
2. 全局审计页使用 `GET /pdc/nfc/admin/operation-logs`，资产详情继续使用 `GET /pdc/nfc/admin/assets/{id}/operation-logs`。
3. 写卡结果上传采用 multipart 字段 `file` 和 `requestId`；`requestId` 必须是 UUID。相同 job、requestId 和文件 SHA-256 重放原响应，任一不同返回幂等冲突。
4. 批次取消使用 `POST /pdc/nfc/admin/batches/{id}/cancel`，Scheme job 取消使用 `POST /pdc/nfc/admin/scheme-jobs/{id}/cancel`，写卡任务取消使用 `POST /pdc/nfc/admin/write-jobs/{id}/cancel`；均遵守 Spec 状态约束。
5. `pdc_nfc_claim_record` 只保存成功或本人重放的确定结果，因此 `asset_id` 使用普通唯一键即可保证“成功领取唯一”，不制造 MySQL 不支持的伪部分索引。
6. Scheme/write 活跃任务排他通过 `pdc_nfc_asset.active_scheme_job_id`、`active_write_job_id` 和条件更新获取租约；任务结束或失败释放租约，数据库不使用伪部分唯一索引。
7. 工厂 CSV 的完整导出事实写入 `pdc_nfc_write_job_item` 不可变快照：除 Spec 字段外，同时保存 `item_no`、`asset_no`、`batch_no`、`sku_code`、`prototype`、`uri_sha256`，确保重复下载不依赖可变主表字段。
8. 管理 mutation 不使用前端自动网络重放；只有带固定 `requestId` 且服务端已实现正文指纹幂等的操作可以由用户显式重试。

## Locked File Map

### Backend ownership

```text
main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/
├── config/          PdcNfcProperties、专用 executor/scheduler
├── constant/        Prototype 与 asset/batch/job 状态枚举
├── controller/      按 product/batch/scheme/write/asset/claim 拆分
├── crypto/          claimRef HMAC、AES-GCM、请求指纹
├── dao/             11 张 pdc_ 表的 Mapper
├── dto/             入参和分页查询
├── entity/          11 张 pdc_ 表实体
├── service/         领域接口
├── service/impl/    事务、状态机、任务租约、CSV
├── task/            Scheme dispatcher/worker/recovery
├── wechat/          generatenfcscheme 客户端和错误策略
└── vo/              管理端和小程序响应
```

`pet` 领域只修改 `PetService.java`、`PetServiceImpl.java` 和其现有测试；微信 token 只从 `wechat` 领域抽取共享 provider。复杂聚合/锁查询放在 `src/main/resources/mapper/pdc/nfc/`，数据库变更只新增 `202607291000.sql` 并登记到 master。

### Manager-web ownership

```text
main/manager-web/src/apis/module/pdcNfc.js
main/manager-web/src/router/access.mjs
main/manager-web/src/utils/pdcNfcState.mjs
main/manager-web/src/views/nfc/*.vue
main/manager-web/src/components/nfc/*.vue
main/manager-web/tests/pdcNfc*.test.mjs
```

### Egg miniprogram ownership

```text
main/egg-miniprogram/miniprogram/utils/nfc-claim-intent.js
main/egg-miniprogram/miniprogram/utils/nfc-claim-api.js
main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.{js,json,wxml,wxss}
```

## Delivery Dependency Graph

```text
测试门禁
  → 数据库骨架 → 持久化/状态机 → 加密标识 → 批次分配
  → pet createEgg → 领取事务
  → 微信 token → Scheme client → 可恢复 Scheme job
  → 写卡导出 → 写卡导入 → 库存/审计
  → manager-web 权限壳 → 生产页 → 库存页
  → 小程序意图 → 领取页 → 登录恢复
  → 发布候选验证 → 外部量产门
```

---

### Task 1: 建立 NFC 可信测试与覆盖率门禁

**Files:**
- Modify: `main/manager-api/pom.xml`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/support/MySqlContainerSupport.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcCoverageConfigurationTest.java`
- Create: `main/manager-api/src/test/resources/application-nfc-test.yml`
- Modify: `main/manager-web/package.json`
- Create: `main/manager-web/tests/pdcNfcCoverageSmoke.test.mjs`

**Interfaces:**
- Consumes: Java 21、本机 Docker、Node.js 25.2.1。
- Produces: `MySqlContainerSupport` 真实 MySQL 测试基类；`verify:nfc-coverage` 前端命令；后端 JaCoCo 对 `xiaozhi.modules.pdc.nfc.*` 的 80% line/branch check。

- [ ] **Step 1: 写后端门禁失败测试**

```java
class PdcNfcCoverageConfigurationTest {
    @Test
    void pomEnforcesPdcLineAndBranchCoverage() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<minimum>0.80</minimum>")
                .contains("xiaozhi.modules.pdc.nfc.*")
                .contains("spring-boot-testcontainers")
                .contains("<artifactId>mysql</artifactId>");
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run from `main/manager-api`:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcCoverageConfigurationTest
```

Expected: FAIL，因为 POM 尚未包含 NFC 覆盖率规则和 Testcontainers 依赖。

- [ ] **Step 3: 在 POM 加入真实 MySQL 测试依赖与 JaCoCo check**

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>mysql</artifactId>
  <scope>test</scope>
</dependency>
```

在现有 JaCoCo executions 中增加 `check`，规则只约束本次新增包，避免历史未覆盖代码造成无关阻塞：

```xml
<execution>
  <id>nfc-coverage-check</id>
  <phase>verify</phase>
  <goals><goal>check</goal></goals>
  <configuration>
    <rules>
      <rule>
        <element>BUNDLE</element>
        <includes><include>xiaozhi.modules.pdc.nfc.*</include></includes>
        <limits>
          <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
          <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</execution>
```

- [ ] **Step 4: 创建共享 MySQL 测试基类**

```java
@Testcontainers
@ActiveProfiles("nfc-test")
public abstract class MySqlContainerSupport {
    @Container
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("xiaozhi_nfc_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.liquibase.enabled", () -> true);
    }
}
```

`application-nfc-test.yml` 必须禁用 Redis 外部连接和所有 NFC 功能开关，并使用测试专用 Base64 密钥；测试密钥只能存在于 `src/test/resources`。

- [ ] **Step 5: 增加前端覆盖率命令**

`main/manager-web/package.json` 新增：

```json
"verify:nfc-coverage": "node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-branches=80 --test-coverage-include=src/router/access.mjs --test-coverage-include=src/utils/pdcNfcState.mjs tests/pdcNfc*.test.mjs"
```

`pdcNfcCoverageSmoke.test.mjs` 只验证 package script 已配置，不导入尚未创建的业务模块：

```js
test('package exposes strict NFC coverage command', async () => {
  const pkg = JSON.parse(await readFile(new URL('../package.json', import.meta.url)));
  assert.match(pkg.scripts['verify:nfc-coverage'], /test-coverage-lines=80/);
  assert.match(pkg.scripts['verify:nfc-coverage'], /test-coverage-branches=80/);
});
```

小程序不新增 npm 构建链；NFC 测试最终使用：

```bash
node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-branches=80 \
  --test-coverage-include=miniprogram/utils/nfc-claim-intent.js \
  --test-coverage-include=miniprogram/utils/nfc-claim-api.js \
  miniprogram/utils/nfc-claim-intent.test.js \
  miniprogram/utils/nfc-claim-api.test.js \
  miniprogram/pages/nfc-claim/nfc-claim.test.js
```

- [ ] **Step 6: 验证门禁并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcCoverageConfigurationTest
git add pom.xml src/test ../manager-web/package.json ../manager-web/tests/pdcNfcCoverageSmoke.test.mjs
git commit -m "test: add nfc test and coverage gates"
```

Expected: 后端配置测试 PASS；真正的 80% check 在 NFC 类出现后由 Task 20 执行。

---

### Task 2: 创建完整 `pdc_` 数据库骨架和固定商品类型

**Files:**
- Create: `main/manager-api/src/main/resources/db/changelog/202607291000.sql`
- Modify: `main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcSchemaContractTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcLiquibaseIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 `MySqlContainerSupport`。
- Produces: 11 张 `pdc_` 表、固定商品类型 `EGG_BABY_NFC`、所有唯一键/查询索引/任务租约字段。

- [ ] **Step 1: 写 Schema 契约失败测试**

```java
class PdcNfcSchemaContractTest {
    private static final List<String> TABLES = List.of(
            "pdc_nfc_product_type", "pdc_nfc_batch", "pdc_nfc_asset",
            "pdc_nfc_scheme_job", "pdc_nfc_scheme_attempt",
            "pdc_nfc_write_job", "pdc_nfc_write_job_item",
            "pdc_nfc_write_record", "pdc_nfc_claim_record",
            "pdc_nfc_admin_request", "pdc_nfc_operation_log");

    @Test
    void migrationDefinesEveryPdcTableAndSeed() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/changelog/202607291000.sql"));
        TABLES.forEach(table -> assertThat(sql).contains("CREATE TABLE " + table));
        assertThat(sql).contains("EGG_BABY_NFC")
                .contains("uk_pdc_nfc_asset_wechat_sn")
                .contains("uk_pdc_nfc_claim_asset")
                .doesNotContain("model_id");
    }
}
```

- [ ] **Step 2: 运行契约测试并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcSchemaContractTest
```

Expected: FAIL，迁移文件不存在。

- [ ] **Step 3: 编写迁移中的主数据与资产表**

使用 `BIGINT` 应用侧分配 ID、`DATETIME(3)` 时间、`utf8mb4`。主数据、批次和资产表使用以下完整结构：

```sql
CREATE TABLE pdc_nfc_product_type (
  id BIGINT NOT NULL,
  type_code VARCHAR(32) NOT NULL,
  type_name VARCHAR(64) NOT NULL,
  claim_page_path VARCHAR(128) NOT NULL,
  capability_mode VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_product_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO pdc_nfc_product_type
  (id, type_code, type_name, claim_page_path, capability_mode, status, create_date)
VALUES
  (1, 'EGG_BABY_NFC', '蛋宝宝 NFC 实物',
   '/pages/nfc-claim/nfc-claim', 'ONE_DEVICE_ONE_CODE', 'ENABLED', NOW(3));

CREATE TABLE pdc_nfc_batch (
  id BIGINT NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  product_type_id BIGINT NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  prototype VARCHAR(16) NOT NULL,
  planned_quantity INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  remark VARCHAR(512) NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_batch_no (batch_no),
  KEY idx_pdc_nfc_batch_product (product_type_id),
  KEY idx_pdc_nfc_batch_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_asset (
  id BIGINT NOT NULL,
  asset_no VARCHAR(64) NOT NULL,
  batch_id BIGINT NOT NULL,
  item_no VARCHAR(16) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  prototype VARCHAR(16) NOT NULL,
  wechat_sn VARCHAR(64) NOT NULL,
  claim_ref_hash CHAR(64) NOT NULL,
  claim_ref_hash_version VARCHAR(16) NOT NULL,
  claim_ref_key_version VARCHAR(16) NOT NULL,
  claim_ref_nonce VARBINARY(12) NOT NULL,
  claim_ref_ciphertext VARBINARY(128) NOT NULL,
  scheme_key_version VARCHAR(16) NULL,
  scheme_nonce VARBINARY(12) NULL,
  scheme_ciphertext MEDIUMBLOB NULL,
  scheme_sha256 CHAR(64) NULL,
  tag_uid VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  active_scheme_job_id BIGINT NULL,
  active_write_job_id BIGINT NULL,
  scheme_generated_at DATETIME(3) NULL,
  written_at DATETIME(3) NULL,
  verified_at DATETIME(3) NULL,
  stocked_at DATETIME(3) NULL,
  activated_at DATETIME(3) NULL,
  claimed_at DATETIME(3) NULL,
  disabled_at DATETIME(3) NULL,
  scrapped_at DATETIME(3) NULL,
  claimed_user_id BIGINT NULL,
  pet_id VARCHAR(64) NULL,
  stock_business_no VARCHAR(64) NULL,
  activation_business_no VARCHAR(64) NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_asset_no (asset_no),
  UNIQUE KEY uk_pdc_nfc_asset_batch_item (batch_id, item_no),
  UNIQUE KEY uk_pdc_nfc_asset_wechat_sn (wechat_sn),
  UNIQUE KEY uk_pdc_nfc_asset_claim_hash (claim_ref_hash),
  KEY idx_pdc_nfc_asset_status (status),
  KEY idx_pdc_nfc_asset_scheme_lease (active_scheme_job_id),
  KEY idx_pdc_nfc_asset_write_lease (active_write_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 4: 补齐任务、写卡、幂等和审计表**

使用以下完整结构。所有外部用户/宠物 ID 只建普通索引，不建跨域级联外键：

```sql
CREATE TABLE pdc_nfc_scheme_job (
  id BIGINT NOT NULL,
  job_no VARCHAR(64) NOT NULL,
  batch_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  requested_by BIGINT NOT NULL,
  total_count INT NOT NULL,
  success_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  cursor_asset_id BIGINT NULL,
  lease_owner VARCHAR(128) NULL,
  lease_until DATETIME(3) NULL,
  heartbeat_at DATETIME(3) NULL,
  next_retry_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  create_date DATETIME(3) NOT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_scheme_job_no (job_no),
  KEY idx_pdc_nfc_scheme_job_batch (batch_id),
  KEY idx_pdc_nfc_scheme_job_lease (status, next_retry_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_scheme_attempt (
  id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  wechat_error_code INT NULL,
  error_message VARCHAR(512) NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_scheme_attempt (job_id, asset_id, attempt_no),
  KEY idx_pdc_nfc_scheme_attempt_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_write_job (
  id BIGINT NOT NULL,
  job_no VARCHAR(64) NOT NULL,
  batch_id BIGINT NOT NULL,
  format_version VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  total_count INT NOT NULL,
  success_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  file_sha256 CHAR(64) NULL,
  row_count INT NULL,
  export_user_id BIGINT NULL,
  exported_at DATETIME(3) NULL,
  result_file_sha256 CHAR(64) NULL,
  import_request_id CHAR(36) NULL,
  result_response_json JSON NULL,
  import_user_id BIGINT NULL,
  imported_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_write_job_no (job_no),
  KEY idx_pdc_nfc_write_job_batch (batch_id),
  KEY idx_pdc_nfc_write_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_write_job_item (
  id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  sequence_no INT NOT NULL,
  asset_no VARCHAR(64) NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  wechat_sn VARCHAR(64) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  prototype VARCHAR(16) NOT NULL,
  scheme_sha256 CHAR(64) NOT NULL,
  uri_tnf VARCHAR(8) NOT NULL,
  uri_type VARCHAR(8) NOT NULL,
  aar_tnf VARCHAR(8) NOT NULL,
  aar_type VARCHAR(32) NOT NULL,
  aar_payload VARCHAR(128) NOT NULL,
  create_date DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_write_item_asset (job_id, asset_id),
  UNIQUE KEY uk_pdc_nfc_write_item_seq (job_id, sequence_no),
  KEY idx_pdc_nfc_write_item_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_write_record (
  id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  write_result VARCHAR(16) NOT NULL,
  verify_result VARCHAR(16) NOT NULL,
  tag_uid VARCHAR(128) NULL,
  ndef_record_count INT NULL,
  uri_sha256 CHAR(64) NULL,
  aar_package VARCHAR(128) NULL,
  is_read_only TINYINT(1) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(512) NULL,
  written_at DATETIME(3) NULL,
  imported_at DATETIME(3) NOT NULL,
  import_user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_write_record_attempt (job_id, asset_id, attempt_no),
  KEY idx_pdc_nfc_write_record_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_claim_record (
  id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  request_id CHAR(36) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  pet_id VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  create_date DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_claim_asset (asset_id),
  UNIQUE KEY uk_pdc_nfc_claim_user_request (user_id, request_id),
  KEY idx_pdc_nfc_claim_pet (pet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_admin_request (
  id BIGINT NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  request_id CHAR(36) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NULL,
  status VARCHAR(16) NOT NULL,
  operator_user_id BIGINT NOT NULL,
  create_date DATETIME(3) NOT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_admin_request (operation_type, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pdc_nfc_operation_log (
  id BIGINT NOT NULL,
  operator_user_id BIGINT NULL,
  request_id CHAR(36) NULL,
  source VARCHAR(32) NOT NULL,
  object_type VARCHAR(32) NOT NULL,
  object_id BIGINT NULL,
  operation_type VARCHAR(64) NOT NULL,
  before_status VARCHAR(32) NULL,
  after_status VARCHAR(32) NULL,
  quantity INT NULL,
  business_no VARCHAR(64) NULL,
  result VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NULL,
  detail_json JSON NULL,
  create_date DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pdc_nfc_operation_object (object_type, object_id, create_date),
  KEY idx_pdc_nfc_operation_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`pdc_nfc_operation_log.detail_json` 只能保存对应操作的 allowlist 字段，不能保存 claimRef、Scheme、access token、AppSecret 或密钥。任务与资产之间的“单个有效任务”约束通过 `pdc_nfc_asset.active_scheme_job_id/active_write_job_id` 在同一加锁事务中租用和释放，不额外创建第 12 张表。

- [ ] **Step 5: 登记 changeset 并写真实 MySQL 集成测试**

```java
@SpringBootTest
class PdcNfcLiquibaseIntegrationTest extends MySqlContainerSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationCreatesSeedAndUniqueConstraints() {
        Integer count = jdbc.queryForObject(
                "select count(*) from pdc_nfc_product_type where type_code='EGG_BABY_NFC'",
                Integer.class);
        assertThat(count).isEqualTo(1);
        assertThatThrownBy(() -> {
            jdbc.update("insert into pdc_nfc_asset " +
                    "(id,asset_no,batch_id,item_no,sku_code,prototype,wechat_sn," +
                    "claim_ref_hash,claim_ref_hash_version,claim_ref_key_version," +
                    "claim_ref_nonce,claim_ref_ciphertext,status,create_date) " +
                    "select 2,'A2',batch_id,'000002',sku_code,prototype,wechat_sn," +
                    "repeat('b',64),'v1','v1',claim_ref_nonce,claim_ref_ciphertext,'CREATED',now(3) " +
                    "from pdc_nfc_asset where id=1");
        }).hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }
}
```

- [ ] **Step 6: 运行迁移测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcSchemaContractTest,PdcNfcLiquibaseIntegrationTest
git add src/main/resources/db/changelog src/test/java/xiaozhi/modules/pdc
git commit -m "feat: add nfc production schema"
```

Expected: 两个测试类 PASS；MySQL 中只有一个固定商品类型，重复 sn/claim/幂等键被数据库拒绝。

---

### Task 3: 建立持久化模型和唯一资产状态机

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcPrototype.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcAssetStatus.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcBatchStatus.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcSchemeJobStatus.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcWriteJobStatus.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcProductTypeEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcBatchEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcAssetEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcSchemeJobEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcSchemeAttemptEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcWriteJobEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcWriteJobItemEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcWriteRecordEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcClaimRecordEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcAdminRequestEntity.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcOperationLogEntity.java`
- Create: corresponding 11 files under `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dao/`
- Create: `main/manager-api/src/main/resources/mapper/pdc/nfc/PdcNfcAssetDao.xml`
- Create: `main/manager-api/src/main/resources/mapper/pdc/nfc/PdcNfcBatchDao.xml`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcAssetStateMachine.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcBatchStateMachine.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcSchemeJobStateMachine.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteJobStateMachine.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcAssetStateMachineTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcJobStateMachineTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcPersistenceContractTest.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java`
- Modify: `main/manager-api/src/main/resources/i18n/messages.properties`
- Modify: `main/manager-api/src/main/resources/i18n/messages_zh_CN.properties`

**Interfaces:**
- Consumes: Task 2 表结构。
- Produces: 四个状态机的 `requireTransition(from, to)`；`PdcNfcAssetDao.selectByClaimHashesForUpdate(Collection<String>)`；`selectByIdsForUpdate(List<Long>)`；10500-10520 PDC 错误码。

- [ ] **Step 1: 写完整状态矩阵失败测试**

```java
@ParameterizedTest
@CsvSource({
    "CREATED,SCHEME_GENERATED", "SCHEME_GENERATED,WRITTEN",
    "WRITTEN,VERIFIED", "VERIFIED,IN_STOCK",
    "IN_STOCK,ACTIVE", "ACTIVE,CLAIMED",
    "CREATED,SCRAPPED", "SCHEME_GENERATED,SCRAPPED",
    "WRITTEN,SCRAPPED", "VERIFIED,SCRAPPED",
    "IN_STOCK,DISABLED", "ACTIVE,DISABLED", "CLAIMED,DISABLED"
})
void permitsOnlyDeclaredTransitions(PdcNfcAssetStatus from, PdcNfcAssetStatus to) {
    assertThatCode(() -> stateMachine.requireTransition(from, to)).doesNotThrowAnyException();
}

@Test
void scrappedAndDisabledCannotRecover() {
    for (PdcNfcAssetStatus target : PdcNfcAssetStatus.values()) {
        assertThatThrownBy(() -> stateMachine.requireTransition(SCRAPPED, target))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition(DISABLED, target))
                .isInstanceOf(RenException.class);
    }
}
```

- [ ] **Step 2: 运行并确认编译失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcAssetStateMachineTest
```

Expected: FAIL，状态枚举和状态机尚不存在。

- [ ] **Step 3: 创建枚举和状态机**

```java
@Component
public final class PdcNfcAssetStateMachine {
    private static final Map<PdcNfcAssetStatus, Set<PdcNfcAssetStatus>> ALLOWED = Map.of(
        CREATED, Set.of(SCHEME_GENERATED, SCRAPPED),
        SCHEME_GENERATED, Set.of(WRITTEN, SCRAPPED),
        WRITTEN, Set.of(VERIFIED, SCRAPPED),
        VERIFIED, Set.of(IN_STOCK, SCRAPPED),
        IN_STOCK, Set.of(ACTIVE, DISABLED),
        ACTIVE, Set.of(CLAIMED, DISABLED),
        CLAIMED, Set.of(DISABLED)
    );

    public void requireTransition(PdcNfcAssetStatus from, PdcNfcAssetStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
```

另外三个枚举和状态机使用以下确定矩阵；所有 `requireTransition` 都使用
`ALLOWED.getOrDefault(from, Set.of())` 校验并抛出
`ErrorCode.PDC_NFC_INVALID_STATE`：

```java
public enum PdcNfcBatchStatus {
    DRAFT, SCHEME_GENERATING, READY_FOR_WRITE, WRITING,
    READY_FOR_STOCK, COMPLETED, CLOSED, CANCELLED
}

@Component
public final class PdcNfcBatchStateMachine {
    private static final Map<PdcNfcBatchStatus, Set<PdcNfcBatchStatus>> ALLOWED = Map.of(
        DRAFT, Set.of(SCHEME_GENERATING, CANCELLED),
        SCHEME_GENERATING, Set.of(READY_FOR_WRITE, CANCELLED),
        READY_FOR_WRITE, Set.of(WRITING, CANCELLED),
        WRITING, Set.of(READY_FOR_STOCK, CANCELLED),
        READY_FOR_STOCK, Set.of(COMPLETED, CANCELLED),
        COMPLETED, Set.of(CLOSED)
    );

    public void requireTransition(PdcNfcBatchStatus from, PdcNfcBatchStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}

public enum PdcNfcSchemeJobStatus {
    PENDING, RUNNING, PARTIAL_SUCCESS, SUCCEEDED, FAILED, CANCELLED
}

@Component
public final class PdcNfcSchemeJobStateMachine {
    private static final Map<PdcNfcSchemeJobStatus, Set<PdcNfcSchemeJobStatus>> ALLOWED =
        Map.of(
            PENDING, Set.of(RUNNING, CANCELLED),
            RUNNING, Set.of(PARTIAL_SUCCESS, SUCCEEDED, FAILED, CANCELLED)
        );

    public void requireTransition(PdcNfcSchemeJobStatus from, PdcNfcSchemeJobStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}

public enum PdcNfcWriteJobStatus {
    CREATED, EXPORTED, RESULT_IMPORTED, COMPLETED, CANCELLED
}

@Component
public final class PdcNfcWriteJobStateMachine {
    private static final Map<PdcNfcWriteJobStatus, Set<PdcNfcWriteJobStatus>> ALLOWED =
        Map.of(
            CREATED, Set.of(EXPORTED, CANCELLED),
            EXPORTED, Set.of(RESULT_IMPORTED, CANCELLED),
            RESULT_IMPORTED, Set.of(COMPLETED)
        );

    public void requireTransition(PdcNfcWriteJobStatus from, PdcNfcWriteJobStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
```

批次取消还必须由 Task 6 的服务先确认不存在有效 Scheme/write job 且没有
`CLAIMED` 资产；状态机本身只负责转换合法性。Task 3 同时把以下
10500-10520 常量和基础 i18n 文案加入中央 `ErrorCode`，确保后续任务不会引用尚未定义的错误码：

```java
PDC_NFC_FEATURE_DISABLED(10500),
PDC_NFC_MODEL_ID_NOT_CONFIGURED(10501),
PDC_NFC_RELEASE_NOT_READY(10502),
PDC_NFC_CRYPTO_NOT_CONFIGURED(10503),
PDC_NFC_INVALID_STATE(10504),
PDC_NFC_ASSET_NOT_FOUND(10505),
PDC_NFC_ASSET_UNAVAILABLE(10506),
PDC_NFC_ASSET_ALREADY_CLAIMED(10507),
PDC_NFC_IDEMPOTENCY_CONFLICT(10508),
PDC_NFC_BATCH_NOT_FOUND(10509),
PDC_NFC_JOB_NOT_FOUND(10510),
PDC_NFC_JOB_CONFLICT(10511),
PDC_NFC_WECHAT_NFC_ERROR(10512),
PDC_NFC_CSV_FORMAT_ERROR(10513),
PDC_NFC_CSV_CONTENT_MISMATCH(10514),
PDC_NFC_BULK_LIMIT_EXCEEDED(10515),
PDC_NFC_PHONE_REQUIRED(10516),
PDC_NFC_RATE_LIMITED(10517),
PDC_NFC_INVALID_PROTOTYPE(10518),
PDC_NFC_INVALID_MODEL_ID(10519),
PDC_NFC_WRITE_RESULT_CONFLICT(10520);
```

- [ ] **Step 4: 创建实体和 Mapper**

每个实体用准确 `@TableName`，资产 `version` 用 `@Version`，ID 用 `@TableId(type = IdType.ASSIGN_ID)`。锁查询必须稳定排序：

```java
List<PdcNfcAssetEntity> selectByClaimHashesForUpdate(
        @Param("hashes") Collection<String> hashes);

List<PdcNfcAssetEntity> selectByIdsForUpdate(
        @Param("ids") List<Long> sortedIds);
```

```xml
<select id="selectByIdsForUpdate"
        resultType="xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity">
  SELECT * FROM pdc_nfc_asset
  WHERE id IN
  <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
  ORDER BY id
  FOR UPDATE
</select>
```

- [ ] **Step 5: 写并运行持久化契约测试**

测试反射检查 11 个 `@TableName` 都以 `pdc_` 开头、资产含 `@Version`、Mapper XML 同时含 `ORDER BY id` 和 `FOR UPDATE`。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcAssetStateMachineTest,PdcNfcJobStateMachineTest,PdcNfcPersistenceContractTest
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/xiaozhi/modules/pdc \
  src/main/java/xiaozhi/common/exception/ErrorCode.java \
  src/main/resources/mapper/pdc \
  src/main/resources/i18n/messages.properties \
  src/main/resources/i18n/messages_zh_CN.properties \
  src/test/java/xiaozhi/modules/pdc
git commit -m "feat: add nfc asset state machine"
```

---

### Task 4: 实现领取标识、字段加密和请求指纹

**Files:**
- Modify: `main/manager-api/src/main/resources/application.yml`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/config/PdcNfcProperties.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/crypto/EncryptedField.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/crypto/ClaimRefProtection.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/crypto/PdcNfcIdentifierGenerator.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/crypto/RequestFingerprint.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/crypto/ClaimRefProtectionTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/crypto/PdcNfcIdentifierGeneratorTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/crypto/RequestFingerprintTest.java`

**Interfaces:**
- Consumes: 应用侧已分配的 `assetId`。
- Produces: `String newClaimRef()`、`String newWechatSn()`、`ProtectedClaimRef protect(Long assetId, String claimRef)`、`List<String> lookupHashes(String claimRef)`、`String decrypt(Long assetId, EncryptedField field)`、`String sha256Canonical(Object value)`。

- [ ] **Step 1: 写密码学失败测试**

```java
@Test
void encryptsWithAssetAadAndReadsPreviousKey() {
    String ref = generator.newClaimRef();
    ProtectedClaimRef protectedRef = protection.protect(101L, ref);

    assertThat(ref).matches("[A-Za-z0-9_-]{22}");
    assertThat(protection.decrypt(101L, protectedRef.encrypted())).isEqualTo(ref);
    assertThatThrownBy(() -> protection.decrypt(102L, protectedRef.encrypted()))
            .isInstanceOf(RenException.class);
    assertThat(protection.lookupHashes(ref)).contains(protectedRef.lookupHash());
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=ClaimRefProtectionTest,PdcNfcIdentifierGeneratorTest,RequestFingerprintTest
```

Expected: FAIL，目标类型不存在。

- [ ] **Step 3: 增加 fail-closed 配置**

```yaml
pdc:
  nfc:
    enabled: ${PDC_NFC_ENABLED:false}
    model-id: ${PDC_NFC_MODEL_ID:}
    release-ready: ${PDC_NFC_RELEASE_READY:false}
    scheme-generation-enabled: ${PDC_NFC_SCHEME_GENERATION_ENABLED:false}
    activation-enabled: ${PDC_NFC_ACTIVATION_ENABLED:false}
    claim-enabled: ${PDC_NFC_CLAIM_ENABLED:false}
    max-batch-quantity: ${PDC_NFC_MAX_BATCH_QUANTITY:10000}
    claim-ref:
      active-version: ${PDC_NFC_CLAIM_ACTIVE_VERSION:}
      active-hmac-key-base64: ${PDC_NFC_ACTIVE_HMAC_KEY_BASE64:}
      active-aes-key-base64: ${PDC_NFC_ACTIVE_AES_KEY_BASE64:}
      previous-version: ${PDC_NFC_CLAIM_PREVIOUS_VERSION:}
      previous-hmac-key-base64: ${PDC_NFC_PREVIOUS_HMAC_KEY_BASE64:}
      previous-aes-key-base64: ${PDC_NFC_PREVIOUS_AES_KEY_BASE64:}
```

任何生成/解密操作遇到空 version、非 32 字节 AES key、空 HMAC key 或 HMAC/AES 字节相同都抛 `PDC_NFC_CRYPTO_NOT_CONFIGURED`，但应用启动和商品类型只读查询仍可工作。

- [ ] **Step 4: 实现 CSPRNG、HMAC 和 AES-GCM**

```java
public ProtectedClaimRef protect(Long assetId, String claimRef) {
    byte[] nonce = new byte[12];
    secureRandom.nextBytes(nonce);
    byte[] aad = Long.toUnsignedString(assetId).getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext = aesGcm.encrypt(activeAesKey(), nonce, aad,
            claimRef.getBytes(StandardCharsets.UTF_8));
    return new ProtectedClaimRef(
            hmacHex(activeHmacKey(), claimRef),
            activeVersion(),
            new EncryptedField(activeVersion(), nonce, ciphertext));
}
```

`newClaimRef()` 固定生成 16 随机字节并无填充 Base64URL 编码；`newWechatSn()` 生成 `"EB"` 加 26 位 Crockford Base32。`RequestFingerprint` 使用 Jackson 排序属性和 map key 后做 SHA-256，不保存原始正文。

- [ ] **Step 5: 补齐篡改、旧 key 和碰撞测试**

测试必须覆盖：nonce 每次不同、密文篡改失败、错误 AAD 失败、active/previous 两个 lookup hash、previous AES 可解密、HMAC/AES 同 key 被拒绝、10,000 次生成无重复、canonical map 顺序不影响指纹。

- [ ] **Step 6: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=ClaimRefProtectionTest,PdcNfcIdentifierGeneratorTest,RequestFingerprintTest
git add src/main/resources/application.yml src/main/java/xiaozhi/modules/pdc/nfc/config src/main/java/xiaozhi/modules/pdc/nfc/crypto src/test/java/xiaozhi/modules/pdc/nfc/crypto
git commit -m "feat: protect nfc claim references"
```

Expected: 所有定向测试 PASS，测试输出不出现 claimRef、密钥或密文。

---

### Task 5: 抽取 pet 领域纯数据库 `createEgg`

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pet/service/PetService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pet/service/impl/PetServiceImpl.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetServiceImplCreateEggTest.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/pet/service/impl/PetServiceImplAdoptTest.java`

**Interfaces:**
- Consumes: `prototype` 只能是 `"锦鲤"` 或 `"玉兔"`。
- Produces: `PetVO PetService.createEgg(Long userId, String prototype)`；只写一个 `ai_pet(EGG)` 并加入调用方事务。

- [ ] **Step 1: 写固定原型和纯数据库失败测试**

```java
@Test
void createEggUsesRequestedPrototypeWithoutExternalWork() {
    PetVO result = petService.createEgg(1001L, "锦鲤");

    ArgumentCaptor<PetEntity> pet = ArgumentCaptor.forClass(PetEntity.class);
    verify(petDao).insert(pet.capture());
    assertThat(pet.getValue().getPrototype()).isEqualTo("锦鲤");
    assertThat(pet.getValue().getHatchStatus()).isEqualTo("EGG");
    assertThat(pet.getValue().getDeviceId()).isNull();
    verifyNoInteractions(llmService, agentService, deviceDao, eventPublisher);
    assertThat(result.getId()).isEqualTo(pet.getValue().getId());
}
```

- [ ] **Step 2: 运行并确认编译失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PetServiceImplCreateEggTest
```

Expected: FAIL，`PetService.createEgg` 尚不存在。

- [ ] **Step 3: 实现最小共享能力**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public PetVO createEgg(Long userId, String prototype) {
    requireValidPrototype(prototype);
    Date now = new Date();
    PetEntity pet = new PetEntity();
    pet.setUserId(userId);
    pet.setPrototype(prototype);
    pet.setHatchStatus(HATCH_STATUS_EGG);
    pet.setHatchStartTime(now);
    pet.setExpectedHatchTime(new Date(now.getTime() + SEVEN_DAYS_MS));
    pet.setAcceleratedMinutes(0);
    pet.setCreator(userId);
    petDao.insert(pet);
    return toVO(pet);
}
```

该方法不得调用 `refreshTodayMood()`。`adopt()` 仍随机选择原型、调用 `createEgg()`、处理快速破壳码和邀请码核销；仅邀请码链路在原有位置刷新今日心情，NFC 调用不会触发该外呼。

- [ ] **Step 4: 增加回归用例**

覆盖两个合法原型、非法原型拒绝、null user 拒绝、7 天基线、无 device/agent/档案；保留 `PetServiceImplAdoptTest` 的邀请码 trim、核销、快速破壳、随机原型和回滚断言。

- [ ] **Step 5: 运行 pet 全部定向测试**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PetServiceImplCreateEggTest,PetServiceImplAdoptTest,PetServiceImplHatchTest
```

Expected: PASS；邀请领养继续随机，固定创建不外呼。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/xiaozhi/modules/pet src/test/java/xiaozhi/modules/pet
git commit -m "refactor: share egg creation domain service"
```

---

### Task 6: 实现商品类型、发布证据和批次资产分配

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/CreatePdcNfcBatchDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcBatchQueryDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcReleaseEvidenceDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcProductTypeVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcBatchVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcProductTypeService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcBatchService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcAuditService.java`
- Create: corresponding implementations under `service/impl/`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcProductTypeAdminController.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcBatchAdminController.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcBatchServiceTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcProductTypeAdminControllerTest.java`

**Interfaces:**
- Consumes: Task 2 repositories、Task 4 `ClaimRefProtection/PdcNfcIdentifierGenerator`、Task 3 状态枚举。
- Produces: `PdcNfcBatchVO create(CreatePdcNfcBatchDTO, Long operatorId)`；`List<PdcNfcProductTypeVO> list()`；`void registerReleaseEvidence(PdcNfcReleaseEvidenceDTO, Long operatorId)`；批次和资产均为 `CREATED`。

- [ ] **Step 1: 写批次原子分配失败测试**

```java
@Test
void createAllocatesEveryAssetWithFixedPrototypeInOneTransaction() {
    CreatePdcNfcBatchDTO dto =
            new CreatePdcNfcBatchDTO("B20260729001", 1L, "SKU-KOI", "锦鲤", 3, "试产");

    PdcNfcBatchVO result = service.create(dto, 7L);

    verify(batchDao).insert(argThat(batch -> batch.getPlannedQuantity() == 3));
    verify(assetDao).insertBatch(argThat(assets ->
            assets.size() == 3
            && assets.stream().allMatch(a -> "锦鲤".equals(a.getPrototype()))
            && assets.stream().map(PdcNfcAssetEntity::getWechatSn).distinct().count() == 3));
    assertThat(result.getAssetCount()).isEqualTo(3);
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcBatchServiceTest,PdcNfcProductTypeAdminControllerTest
```

Expected: FAIL，服务和 Controller 尚不存在。

- [ ] **Step 3: 实现只读商品类型组合视图**

```java
public PdcNfcProductTypeVO toVO(PdcNfcProductTypeEntity entity) {
    String modelId = properties.getModelId();
    ReleaseEvidence evidence = auditService.latestReleaseEvidence(entity.getId());
    return new PdcNfcProductTypeVO(
            entity.getId(), entity.getTypeCode(), entity.getTypeName(),
            entity.getClaimPagePath(), entity.getCapabilityMode(), entity.getStatus(),
            StringUtils.isBlank(modelId) ? null : modelId,
            StringUtils.isBlank(modelId) ? "待微信审核配置" : "已配置",
            properties.isReleaseReady(), evidence);
}
```

Controller 类级标注 superAdmin，只提供 GET；发布证据 POST 写 append-only operation log，固定商品类型的业务字段没有新增、编辑、删除入口。

- [ ] **Step 4: 实现批次创建和 500 行 DAO 分块**

先验证 master `enabled`、商品类型、批次号唯一、SKU、原型、数量 `1..10000`，再使用 `IdWorker.getId()` 预分配 asset ID，并以该 ID 做 AES-GCM AAD。`asset_no` 固定为 `batchNo + "-" + 六位itemNo`，所有资产写入一个外层事务；任一碰撞或 DAO 失败回滚批次与全部资产。

```java
for (int index = 1; index <= dto.getPlannedQuantity(); index++) {
    long assetId = IdWorker.getId();
    String claimRef = identifiers.newClaimRef();
    ProtectedClaimRef protectedRef = claimRefs.protect(assetId, claimRef);
    assets.add(assetFactory.created(assetId, batch, index,
            identifiers.newWechatSn(), protectedRef));
}
Lists.partition(assets, 500).forEach(assetDao::insertBatch);
```

- [ ] **Step 5: 增加边界和权限测试**

覆盖 0/10001 数量拒绝、重复 batch no、非法原型、功能关闭、加密未配置、第二个 chunk 失败全事务回滚、product type VO 不返回 AppSecret/token、Controller 类上存在 superAdmin 注解。实现 batch cancel：只有无有效 Scheme/write job 且无 CLAIMED 资产时允许取消，并通过 batch 状态机进入 `CANCELLED`。

- [ ] **Step 6: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcBatchServiceTest,PdcNfcProductTypeAdminControllerTest
git add src/main/java/xiaozhi/modules/pdc src/test/java/xiaozhi/modules/pdc
git commit -m "feat: create nfc batches and assets"
```

Expected: PASS，批次统计来自资产聚合，数据库和响应都不包含明文 claimRef。

---

### Task 7: 抽取共享微信 access token provider

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/WechatAccessTokenProvider.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/impl/WechatAccessTokenProviderImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/impl/WechatServiceImpl.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/wechat/service/impl/WechatServiceImplTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/wechat/service/impl/WechatAccessTokenProviderImplTest.java`

**Interfaces:**
- Consumes: `eggbaby.miniprogram.appid/secret`、`RedisKeys.getWechatAccessTokenKey()`、`RedisUtils`。
- Produces: `String WechatAccessTokenProvider.getAccessToken()`；phone bind 和 NFC Scheme client 共用。

- [ ] **Step 1: 固定现有 token 缓存行为**

```java
@Test
void returnsCachedTokenWithoutHttpCall() {
    when(redisUtils.get(RedisKeys.getWechatAccessTokenKey())).thenReturn("cached-token");
    assertThat(provider.getAccessToken()).isEqualTo("cached-token");
    verifyNoInteractions(httpTransport);
}
```

同时保留 `WechatServiceImplTest` 中缓存 miss、微信 errcode、空 token、TTL 提前 300 秒的断言。

- [ ] **Step 2: 运行新测试并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=WechatAccessTokenProviderImplTest,WechatServiceImplTest
```

Expected: FAIL，共享 provider 不存在。

- [ ] **Step 3: 移动而非复制 token 逻辑**

```java
public interface WechatAccessTokenProvider {
    String getAccessToken();
}
```

`WechatAccessTokenProviderImpl` 拥有 stable_token HTTP 调用和 Redis 缓存；`WechatServiceImpl.bindPhone()` 注入 provider 并调用它，删除原 private `getAccessToken()`。异常和日志只记录微信 errcode，不拼接 token、secret 或完整响应。

- [ ] **Step 4: 运行微信模块回归**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=WechatAccessTokenProviderImplTest,WechatServiceImplTest,WechatServiceImplLoginTest
```

Expected: PASS，手机号绑定行为未变化。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/xiaozhi/modules/wechat src/test/java/xiaozhi/modules/wechat
git commit -m "refactor: share wechat access token service"
```

---

### Task 8: 实现微信 `generatenfcscheme` 客户端和错误策略

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcSchemeClient.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcSchemeRequest.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcSchemeResult.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcErrorAction.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcErrorPolicy.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcHttpTransport.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcSchemeClientTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/wechat/WechatNfcErrorPolicyTest.java`

**Interfaces:**
- Consumes: `WechatAccessTokenProvider.getAccessToken()`、有效 model ID、`wechatSn`、22 字符 claimRef。
- Produces: `WechatNfcSchemeResult generate(String wechatSn, String claimRef)`；错误动作 `RETRYABLE/TASK_FATAL/QUOTA_DEFER`。

- [ ] **Step 1: 写精确 HTTP 请求失败测试**

```java
@Test
void sendsReleasePathQueryModelAndUniqueSnSeparately() {
    client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

    assertThat(transport.lastUrl()).endsWith(
            "/wxa/generatenfcscheme?access_token=access-token");
    JsonNode body = objectMapper.readTree(transport.lastBody());
    assertThat(body.at("/jump_wxa/path").asText())
            .isEqualTo("/pages/nfc-claim/nfc-claim");
    assertThat(body.at("/jump_wxa/query").asText())
            .isEqualTo("v=1&ref=AbCdEfGhIjKlMnOpQrStUv");
    assertThat(body.at("/jump_wxa/env_version").asText()).isEqualTo("release");
    assertThat(body.at("/model_id").asText()).isEqualTo("model-actual");
    assertThat(body.at("/sn").asText()).isEqualTo("EBSN001");
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=WechatNfcSchemeClientTest,WechatNfcErrorPolicyTest
```

Expected: FAIL，client 和策略不存在。

- [ ] **Step 3: 实现 fail-fast 请求校验**

model ID 仅校验非空、trim 后非空且不含 `<`/`>`；不臆造微信未声明的格式正则。claimRef 固定匹配 `[A-Za-z0-9_-]{22}`，query 长度必须小于 1024。空配置在取得 access token 之前失败。

```java
public WechatNfcSchemeResult generate(String wechatSn, String claimRef) {
    requireClientConfiguration(properties.getModelId(), wechatSn, claimRef);
    WechatNfcSchemeRequest request = WechatNfcSchemeRequest.release(
            properties.getModelId(), wechatSn, claimRef);
    return transport.post(accessTokens.getAccessToken(), request);
}
```

- [ ] **Step 4: 实现错误映射**

精确映射：

```text
44990、网络超时、HTTP 5xx → RETRYABLE
44993 → QUOTA_DEFER
40002、40165、40212、85079、9800003、9800007、9800008、9800009 → TASK_FATAL
其他非零微信 errcode → TASK_FATAL
```

异常对象只保留 errcode 和脱敏 errmsg；不得保存 URL query、access token 或请求 body。

- [ ] **Step 5: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=WechatNfcSchemeClientTest,WechatNfcErrorPolicyTest
git add src/main/java/xiaozhi/modules/pdc/nfc/wechat src/test/java/xiaozhi/modules/pdc/nfc/wechat
git commit -m "feat: add wechat nfc scheme client"
```

Expected: PASS；测试扫描捕获的日志不含 `access-token` 或完整 claimRef。

---

### Task 9: 实现可恢复、可限速的持久化 Scheme job

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/config/PdcNfcTaskConfig.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcReadinessService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcSchemeJobService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/task/PdcNfcSchemeJobDispatcher.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/task/PdcNfcSchemeJobWorker.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/task/PdcNfcSchemeRateLimiter.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcSchemeAdminController.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcSchemeProgressVO.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/task/PdcNfcSchemeJobWorkerTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/task/PdcNfcSchemeJobRecoveryIntegrationTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcReadinessServiceTest.java`

**Interfaces:**
- Consumes: Task 6 发布证据、Task 8 client、Task 4 claimRef decrypt、Redis `increment(key, expire)`。
- Produces: `Long start(batchId, operatorId)`、`Long retry(batchId, operatorId)`、`PdcNfcSchemeProgressVO progress(batchId)`、`void cancel(jobId, operatorId)`；HTTP 立即返回 job ID。

- [ ] **Step 1: 写就绪门和可恢复租约失败测试**

```java
@Test
void refusesJobUntilEveryGateAndEvidencePasses() {
    properties.setEnabled(true);
    properties.setSchemeGenerationEnabled(true);
    properties.setReleaseReady(false);
    assertThatThrownBy(() -> readiness.requireSchemeGenerationReady())
            .isInstanceOf(RenException.class);
    verifyNoInteractions(schemeClient);
}

@Test
void expiredLeaseIsRecoveredAfterWorkerLoss() {
    long jobId = fixture.runningJobWithExpiredLease();
    dispatcher.dispatchRecoverableJobs();
    await().untilAsserted(() -> assertThat(jobDao.selectById(jobId).getStatus())
            .isEqualTo("SUCCEEDED"));
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcReadinessServiceTest,PdcNfcSchemeJobWorkerTest,PdcNfcSchemeJobRecoveryIntegrationTest
```

Expected: FAIL，任务组件不存在。

- [ ] **Step 3: 创建专用 executor 和数据库租约**

专用线程池 core 1、max 2、queue 20、`AbortPolicy`；拒绝时 job 保持 `PENDING`，由 5 秒 dispatcher 再取，管理 HTTP 线程绝不执行 worker。dispatcher 用条件更新获取 60 秒租约，worker 每 20 秒 heartbeat；多实例只有一个 `lease_owner` 成功。

```java
int claimed = jobDao.claimLease(jobId, instanceId, now, now.plusSeconds(60));
if (claimed == 1) {
    executor.execute(() -> worker.run(jobId, instanceId));
}
```

- [ ] **Step 4: 实现游标、80/s 分布式限速和重试**

每次只取最多 200 个 `CREATED` 且 lease 可得的资产；每次微信调用前用 Redis 秒级 bucket `pdc:nfc:scheme:rate:{epochSecond}`，计数大于 80 时等待下一秒。retry 使用 1s、2s、4s、8s、16s 上限并加 0..250ms jitter，最多 5 次。

成功时同一小事务内加密 Scheme、写 SHA-256、推进资产为 `SCHEME_GENERATED`、记录 attempt 并更新 cursor。44993 保持 job `RUNNING` 且 `next_retry_at` 为 Asia/Shanghai 下一日 00:05；TASK_FATAL 标记 job `FAILED` 并释放未处理资产 lease；单件网络最终失败记录 attempt、释放该资产并继续。

- [ ] **Step 5: 实现 Controller 和任务取消**

Controller 类级 superAdmin；generate/retry 仅创建 job 后返回，progress 返回总数/成功/失败/游标/最近脱敏错误。cancel 只允许 `PENDING/RUNNING`，将 job 标为 `CANCELLED` 并释放其资产 lease。

- [ ] **Step 6: 覆盖恢复和错误测试**

测试 81 次同秒调用被延后、5 次指数退避、44993 延后、9800003 停止、单件失败继续、服务重启续游标、两个 dispatcher 只有一个 lease、首次成功 Scheme 不被后续超时覆盖、发起 operator ID 保留。

- [ ] **Step 7: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcReadinessServiceTest,PdcNfcSchemeJobWorkerTest,PdcNfcSchemeJobRecoveryIntegrationTest
git add src/main/java/xiaozhi/modules/pdc src/test/java/xiaozhi/modules/pdc
git commit -m "feat: run durable nfc scheme jobs"
```

Expected: PASS；停止 API 进程再恢复测试仍从 cursor 继续，不从头覆盖成功结果。

---

### Task 10: 导出不可变、可重复校验的工厂写卡 CSV

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteJobService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteCsvExporter.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcWriteFile.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcWriteJobVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcWriteJobAdminController.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteCsvExporterTest.java`
- Create: `main/manager-api/src/test/resources/pdc/nfc/PDC_NFC_WRITE_V1.golden.csv`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcWriteJobDownloadTest.java`

**Interfaces:**
- Consumes: `SCHEME_GENERATED` 资产、Scheme decrypt、不可变 snapshot。
- Produces: `PdcNfcWriteJobVO create(batchId, operatorId)`；`PdcNfcWriteFile export(jobId, operatorId)`，字节稳定且带 UTF-8 BOM。

- [ ] **Step 1: 写 golden bytes 失败测试**

```java
@Test
void exportMatchesVersionOneGoldenFileByteForByte() throws Exception {
    byte[] actual = exporter.export(fixture.snapshot()).bytes();
    byte[] expected = Files.readAllBytes(
            Path.of("src/test/resources/pdc/nfc/PDC_NFC_WRITE_V1.golden.csv"));
    assertThat(actual).isEqualTo(expected);
    assertThat(actual).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcWriteCsvExporterTest,PdcNfcWriteJobDownloadTest
```

Expected: FAIL，exporter 和 golden fixture 不存在。

- [ ] **Step 3: 创建不可变任务快照**

创建任务时对 `SCHEME_GENERATED` 资产稳定按 item no 排序、条件设置 `active_write_job_id`、解密 Scheme 仅计算 `scheme_sha256/uri_sha256`，并把补充契约的全部列写入 item snapshot。生成 snapshot 后业务字段不可更新；重复下载只从 snapshot + 解密后的 Scheme 生成相同字节。
write job cancel 只允许 `CREATED/EXPORTED` 且尚未导入结果，取消后释放全部 `active_write_job_id` 并保留 snapshot 与下载审计。

- [ ] **Step 4: 实现严格 V1 CSV**

固定 CRLF、UTF-8 BOM、RFC 4180 双引号转义、固定表头和 NDEF 常量。任何文本单元格 trim 后以 `= + - @` 开头时在导出值前加单引号，Scheme URI 不执行公式处理但必须作为引号字段。

```java
private static final List<String> HEADER = List.of(
    "format_version","job_no","batch_no","item_no","asset_no","wechat_sn",
    "sku_code","prototype","uri_tnf","uri_type","uri_payload",
    "aar_tnf","aar_type","aar_payload");
```

- [ ] **Step 5: 实现逐次鉴权下载响应**

```java
response.setHeader("Content-Disposition",
        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString());
response.setHeader("Cache-Control", "no-store, private");
response.setContentType("text/csv;charset=UTF-8");
response.getOutputStream().write(file.bytes());
```

下载 Controller 类级 superAdmin，每次写 operation log；不写静态目录、不打印响应体。临时文件并非必要，第一版直接流式响应内存 byte array，上限由 10,000 行限制。

- [ ] **Step 6: 测试重复下载和提交**

测试相同 job 两次 byte/SHA 相等、逗号/双引号/CRLF 转义、公式注入、非 SCHEME_GENERATED 不入任务、已有 active job 被拒绝、header 含 `no-store, private`、normal Controller 权限契约拒绝。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcWriteCsvExporterTest,PdcNfcWriteJobDownloadTest
git add src/main/java/xiaozhi/modules/pdc src/test/java/xiaozhi/modules/pdc src/test/resources/pdc
git commit -m "feat: export nfc factory write jobs"
```

---

### Task 11: 导入并原子校验工厂写卡结果

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcWriteResultRow.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcWriteImportVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcAdminOperationType.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcAdminIdempotencyService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteResultImporter.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteResultImporterImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcWriteJobAdminController.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcAdminIdempotencyServiceTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteResultImporterTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteResultImportIntegrationTest.java`
- Create: `main/manager-api/src/test/resources/pdc/nfc/PDC_NFC_RESULT_V1.valid.csv`

**Interfaces:**
- Consumes: multipart `file` + UUID `requestId`、Task 10 snapshot。
- Produces: `PdcNfcWriteImportVO importResult(Long jobId, UUID requestId, MultipartFile file, Long operatorId)`；只有六项验证全满足才推进 `VERIFIED`。

- [ ] **Step 1: 写完整校验失败测试**

```java
@ParameterizedTest
@MethodSource("invalidFiles")
void rejectsWholeFileWithoutAdvancingAnyAsset(byte[] file, String expectedError) {
    assertThatThrownBy(() -> importer.importResult(
            10L, REQUEST_ID, csv(file), 7L))
            .isInstanceOf(RenException.class)
            .hasMessageContaining(expectedError);
    verify(assetDao, never()).updateBatchStatus(any(), any());
    verify(writeRecordDao, never()).insertBatch(any());
}
```

invalid fixtures 明确包含：重复行、缺行、额外行、错误 job_no、asset/sn 不配对、跨任务、错误表头、错误版本、超过 10 MiB、超过 4,096 字符和非 UTF-8。

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcAdminIdempotencyServiceTest,PdcNfcWriteResultImporterTest,PdcNfcWriteResultImportIntegrationTest
```

Expected: FAIL，importer 不存在。

- [ ] **Step 3: 实现两阶段流式解析**

第一阶段只解析到受限 row DTO、计算文件 SHA-256、检查 header/version/字段长度/重复 key，并与 snapshot 做集合完全相等比较；不更新数据库。URI 完整回读若由设备输出，只在内存计算 SHA-256 后立即丢弃，DTO 和日志不保留原 URI。

```java
boolean verified = row.writeSuccess()
        && row.verifySuccess()
        && row.uriSha256().equals(snapshot.getUriSha256())
        && row.ndefRecordCount() == 2
        && "com.tencent.mm".equals(row.aarPackage())
        && row.readOnly();
```

- [ ] **Step 4: 实现通用管理幂等和事务提交**

先实现 `PdcNfcAdminIdempotencyService.execute(operationType, requestId, canonicalRequest, responseType, action)`，使用 `pdc_nfc_admin_request` 的 `(operation_type, request_id)` 唯一键、请求指纹和原响应 JSON。第二阶段按 asset ID 排序 `FOR UPDATE`，再次确认 job/item/asset 状态；同一事务插入 `pdc_nfc_write_record`、推进 `WRITTEN/VERIFIED`、释放 active write lease、写 result file SHA/response JSON 和 operation log。相同 requestId + SHA 返回存储的原响应；相同 requestId 不同 SHA 返回幂等冲突；不同 requestId 上传同一已完成文件时按 write job 中的 SHA 和 response JSON 重放原结果，不再写记录。

`PdcNfcAdminOperationType` 一次定义 `WRITE_RESULT_IMPORT`、`STOCK_IN`、`ACTIVATE`、`DISABLE`、`SCRAP`，后续库存任务直接复用。

写入成功但回读/只读失败：资产停在 `WRITTEN`；写入失败：资产保持 `SCHEME_GENERATED`；设备明确报告已锁错：资产推进 `SCRAPPED`。

- [ ] **Step 5: 增加真实 MySQL 原子性测试**

在导入最后一行制造 sn mismatch，断言前面所有资产仍为原状态且无 write record；再次导入有效文件，断言 VERIFIED 数量、file SHA、request ID 和任务状态 `COMPLETED`。

- [ ] **Step 6: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcAdminIdempotencyServiceTest,PdcNfcWriteResultImporterTest,PdcNfcWriteResultImportIntegrationTest
git add src/main/java/xiaozhi/modules/pdc src/test/java/xiaozhi/modules/pdc src/test/resources/pdc
git commit -m "feat: import nfc write results"
```

Expected: PASS；任何整文件错误都不产生部分状态推进。

---

### Task 12: 实现管理幂等、库存流转、作废和审计查询

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcBulkAssetOperationDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcAssetDispositionDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcAssetQueryDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcOperationLogQueryDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcBulkOperationVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcAssetVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcOperationLogVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcInventoryService.java`
- Create: corresponding implementations under `service/impl/`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcAssetAdminController.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcOperationLogAdminController.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcInventoryServiceIntegrationTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcAdminPermissionContractTest.java`

**Interfaces:**
- Consumes: Task 11 `PdcNfcAdminIdempotencyService`、稳定排序的最多 500 个 asset ID、businessNo、UUID requestId、Task 3 状态机。
- Produces: `stockIn/activate/disable/scrap`；资产/日志分页；原响应幂等重放。

- [ ] **Step 1: 写全有或全无失败测试**

```java
@Test
void activationRejectsWholeBatchWhenOneAssetIsNotInStock() {
    fixture.asset(1L, IN_STOCK);
    fixture.asset(2L, VERIFIED);
    PdcNfcBulkAssetOperationDTO request =
            request(List.of(1L, 2L), "OUT-001", UUID.randomUUID());

    assertThatThrownBy(() -> service.activate(request, 7L))
            .isInstanceOf(RenException.class);
    assertThat(fixture.status(1L)).isEqualTo(IN_STOCK);
    assertThat(fixture.status(2L)).isEqualTo(VERIFIED);
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcAdminIdempotencyServiceTest,PdcNfcInventoryServiceIntegrationTest,PdcNfcAdminPermissionContractTest
```

Expected: FAIL，服务不存在。

- [ ] **Step 3: 把库存 mutation 接入通用幂等模板**

```java
@Transactional(rollbackFor = Exception.class)
public PdcNfcBulkOperationVO activate(
        PdcNfcBulkAssetOperationDTO request, Long operatorId) {
    return idempotency.execute(
            PdcNfcAdminOperationType.ACTIVATE,
            request.getRequestId(),
            canonicalBulkRequest(request),
            PdcNfcBulkOperationVO.class,
            () -> doActivate(request, operatorId));
}
```

`canonicalBulkRequest` 保留 businessNo 并把 asset IDs 排序，但不静默删除重复值；重复输入在进入模板前直接拒绝。Task 11 已测试并发唯一键冲突和 fingerprint 比较，本任务增加库存状态与日志不重复断言。

- [ ] **Step 4: 实现库存和处置事务**

批量 DTO 拒绝空、超过 500、重复 asset ID、空 businessNo、非 UUID。按 ID 排序锁全部资产，stock-in 要求全部 VERIFIED，activate 要求全部 IN_STOCK 且 `enabled/activation-enabled/release-ready` 通过；全部预检完成后批量更新并逐对象追加 operation log。

disable 允许 IN_STOCK/ACTIVE/CLAIMED，CLAIMED 时保留 claimed user、pet 和 claim record；scrap 只允许 CREATED/SCHEME_GENERATED/WRITTEN/VERIFIED。不可恢复状态由 Task 3 状态机保证。

- [ ] **Step 5: 实现安全查询**

资产列表支持 Spec 全部筛选字段，默认不查/不返回 claimRef、claim hash、ciphertext、完整 Scheme、tag UID；用户字段走现有脱敏规则。详情返回状态时间线和审计，Scheme 只返回 `schemeSha256`。全局和资产日志均分页，`detail_json` 经过 allowlist VO 映射。

- [ ] **Step 6: 验证权限和幂等**

反射测试所有 `/pdc/nfc/admin/**` Controller 类具有 superAdmin 注解；相同 requestId/正文重放原 JSON，不重复写状态或日志；相同 requestId/不同 assets/businessNo 冲突；两个并发相同请求只执行一次。

- [ ] **Step 7: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcAdminIdempotencyServiceTest,PdcNfcInventoryServiceIntegrationTest,PdcNfcAdminPermissionContractTest
git add src/main/java/xiaozhi/modules/pdc src/test/java/xiaozhi/modules/pdc
git commit -m "feat: add idempotent nfc inventory operations"
```

---

### Task 13: 实现严格手机号校验、领取限流和 preview

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/wechat/service/WechatPhoneGate.java`
- Modify: `main/manager-api/src/test/java/xiaozhi/modules/wechat/service/WechatPhoneGateTest.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/common/redis/RedisKeys.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcClaimPreviewDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcClaimPreviewVO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimRateLimiter.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimPreviewServiceTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimRateLimiterTest.java`

**Interfaces:**
- Consumes: `claimRef` regex `[A-Za-z0-9_-]{22}`、登录 user ID。
- Produces: `boolean WechatPhoneGate.hasBoundWechatPhone(userId)`；`PdcNfcClaimPreviewVO preview(userId, claimRef)`，状态 `CLAIMABLE/CLAIMED_BY_SELF/CLAIMED_BY_OTHER/UNAVAILABLE`。

- [ ] **Step 1: 写严格手机号和 preview 非核销失败测试**

```java
@Test
void noWechatMappingIsNotEligibleForClaim() {
    when(wechatUserDao.selectList(any())).thenReturn(List.of());
    assertThat(phoneGate.hasBoundWechatPhone(7L)).isFalse();
}

@Test
void previewNeverCreatesPetOrClaimRecord() {
    PdcNfcClaimPreviewVO preview = service.preview(7L, VALID_REF);
    assertThat(preview.getClaimStatus()).isEqualTo("CLAIMABLE");
    verifyNoInteractions(petService, claimRecordDao);
    verify(assetDao, never()).updateById(any());
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=WechatPhoneGateTest,PdcNfcClaimPreviewServiceTest,PdcNfcClaimRateLimiterTest
```

Expected: FAIL，严格 gate 和 preview 不存在。

- [ ] **Step 3: 保留后台兼容并增加严格方法**

现有 `canAccess()` 语义不改，避免后台账号回归；新增：

```java
public boolean hasBoundWechatPhone(Long userId) {
    if (userId == null) return false;
    List<WechatUserEntity> mappings = findMappings(userId);
    return mappings != null && mappings.stream()
            .filter(Objects::nonNull)
            .anyMatch(mapping -> StringUtils.isNotBlank(mapping.getPhone()));
}
```

claim service 每次 preview/confirm 都显式调用严格方法。

- [ ] **Step 4: 实现 Redis 限流和预激活告警**

固定窗口：

```text
preview: 每用户 30/分钟、每资产 20/分钟
confirm: 每用户 10/分钟、每资产 5/分钟
无效 claimRef: 每用户 10/10分钟
同一资产 10分钟内出现第 2 个不同 userId: 写安全审计并计数告警
```

Redis key 只使用 user ID、asset ID 或 claim hash 前 16 位，不含明文 claimRef。未激活资产 preview 返回统一 `UNAVAILABLE`，同时写 `PRE_ACTIVATION_ACCESS` 审计；不泄露真实状态或库存。

- [ ] **Step 5: 实现 preview 响应收敛**

响应固定：

```java
public record PdcNfcClaimPreviewVO(
    String productName,
    String prototype,
    String claimStatus,
    PetVO pet) {}
```

只有本人已领取时返回 pet；他人已领取、禁用、报废、未激活、无效 ref 均不返回用户/批次/sn/tag 信息。`enabled/claim-enabled/release-ready` 任一关闭统一 fail-closed。

- [ ] **Step 6: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=WechatPhoneGateTest,PdcNfcClaimPreviewServiceTest,PdcNfcClaimRateLimiterTest
git add src/main/java/xiaozhi/modules/wechat src/main/java/xiaozhi/common/redis src/main/java/xiaozhi/modules/pdc src/test/java/xiaozhi/modules/wechat src/test/java/xiaozhi/modules/pdc
git commit -m "feat: protect nfc claim preview"
```

---

### Task 14: 实现并发安全、幂等的领取确认事务

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcClaimConfirmDTO.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/PdcNfcClaimResultVO.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcClaimController.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimServiceTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimConcurrencyIntegrationTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcClaimControllerTest.java`

**Interfaces:**
- Consumes: Task 5 `PetService.createEgg`、Task 13 strict phone/rate limit、UUID requestId。
- Produces: `PdcNfcClaimResultVO confirm(Long userId, String claimRef, UUID requestId)`，状态 `CLAIMED/CLAIMED_BY_SELF`。

- [ ] **Step 1: 写事务顺序和回滚失败测试**

```java
@Test
void petFailureRollsBackClaimAndLeavesAssetActive() {
    fixture.activeAsset(ASSET_ID, "锦鲤", VALID_REF);
    when(petService.createEgg(USER_ID, "锦鲤"))
            .thenThrow(new RenException("pet insert failed"));

    assertThatThrownBy(() -> service.confirm(USER_ID, VALID_REF, REQUEST_ID))
            .isInstanceOf(RenException.class);
    assertThat(fixture.assetStatus(ASSET_ID)).isEqualTo(ACTIVE);
    assertThat(fixture.claimCount(ASSET_ID)).isZero();
}
```

- [ ] **Step 2: 运行并确认失败**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcClaimServiceTest,PdcNfcClaimConcurrencyIntegrationTest,PdcNfcClaimControllerTest
```

Expected: FAIL，confirm 和 Controller 不存在。

- [ ] **Step 3: 按固定顺序实现事务**

```java
@Transactional(rollbackFor = Exception.class)
public PdcNfcClaimResultVO confirm(Long userId, String claimRef, UUID requestId) {
    requireClaimEnabledAndPhone(userId);
    List<PdcNfcAssetEntity> candidates =
            assetDao.selectByClaimHashesForUpdate(claimRefs.lookupHashes(claimRef));
    PdcNfcAssetEntity asset = requireExactlyOne(candidates);

    String fingerprint = fingerprints.sha256Canonical(
            Map.of("assetId", asset.getId(), "requestId", requestId.toString()));
    Optional<PdcNfcClaimRecordEntity> replay =
            claimDao.findByUserAndRequest(userId, requestId.toString());
    if (replay.isPresent()) return replay(replay.get(), fingerprint);
    if (asset.isClaimed()) return replayOwnerOrReject(asset, userId);

    stateMachine.requireTransition(asset.statusEnum(), CLAIMED);
    PetVO pet = petService.createEgg(userId, asset.getPrototype());
    claimDao.insert(success(asset, userId, requestId, fingerprint, pet.getId()));
    int changed = assetDao.markClaimed(
            asset.getId(), asset.getVersion(), userId, pet.getId());
    if (changed != 1) throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
    auditService.claimed(asset, userId, pet.getId());
    return new PdcNfcClaimResultVO("CLAIMED", pet);
}
```

顺序必须是：auth/phone → HMAC lookup + `FOR UPDATE` → requestId 历史与 fingerprint → 已领取分支 → ACTIVE 状态 → createEgg/claim/asset/audit 同事务。

- [ ] **Step 4: 实现本人/他人和请求幂等**

相同 user/requestId/fingerprint 返回原 pet；同 requestId 不同 asset 返回 `PDC_NFC_IDEMPOTENCY_CONFLICT`；资产 CLAIMED 且 user 相同，即使新 requestId 也返回原 pet；其他 user 返回统一已领取错误。

- [ ] **Step 5: 写真实 MySQL 两用户并发测试**

使用两个线程、两个独立事务同时 confirm；以 CountDownLatch 对齐启动。断言恰好一个 `CLAIMED`、一个已领取错误，最终只有一个 claim record、一个新 pet，资产 user/pet 与成功结果一致。

- [ ] **Step 6: 实现 normal Controller**

Controller 类级 `@RequiresPermissions("sys:role:normal")`，DTO 使用 `@Pattern(regexp="[A-Za-z0-9_-]{22}")` 和 UUID 字符串 validator；preview/confirm 均从 `SecurityUser.getUserId()` 获取用户，不接受 body userId。

- [ ] **Step 7: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -DskipTests=false -Dtest=PdcNfcClaimServiceTest,PdcNfcClaimConcurrencyIntegrationTest,PdcNfcClaimControllerTest
git add src/main/java/xiaozhi/modules/pdc src/test/java/xiaozhi/modules/pdc
git commit -m "feat: add transactional nfc claim"
```

Expected: PASS，领取事务不创建 device/agent，不在锁期间外呼。

---

### Task 15: 核验 PDC 错误文案、指标、敏感日志和后端验收

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/common/exception/ErrorCode.java`
- Modify: `main/manager-api/src/main/resources/i18n/messages.properties`
- Modify: `main/manager-api/src/main/resources/i18n/messages_zh_CN.properties`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcMetrics.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcSensitiveLoggingContractTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcControllerPermissionContractTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcBackendAcceptanceTest.java`

**Interfaces:**
- Consumes: Tasks 2-14 全部后端能力。
- Produces: 已在 Task 3 分配的 10500-10520 PDC 错误码完整文案；Micrometer counters/timers；后端发布候选门。

- [ ] **Step 1: 写错误和权限契约失败测试**

```java
@Test
void everyAdminControllerIsSuperAdminAndClaimIsNormal() {
    assertPermission(PdcNfcAssetAdminController.class, "sys:role:superAdmin");
    assertPermission(PdcNfcBatchAdminController.class, "sys:role:superAdmin");
    assertPermission(PdcNfcProductTypeAdminController.class, "sys:role:superAdmin");
    assertPermission(PdcNfcSchemeAdminController.class, "sys:role:superAdmin");
    assertPermission(PdcNfcWriteJobAdminController.class, "sys:role:superAdmin");
    assertPermission(PdcNfcOperationLogAdminController.class, "sys:role:superAdmin");
    assertPermission(PdcNfcClaimController.class, "sys:role:normal");
}
```

- [ ] **Step 2: 核验稳定错误码和安全文案**

使用：

```text
10500 PDC_NFC_FEATURE_DISABLED
10501 PDC_NFC_MODEL_ID_NOT_CONFIGURED
10502 PDC_NFC_RELEASE_NOT_READY
10503 PDC_NFC_CRYPTO_NOT_CONFIGURED
10504 PDC_NFC_INVALID_STATE
10505 PDC_NFC_ASSET_NOT_FOUND
10506 PDC_NFC_ASSET_UNAVAILABLE
10507 PDC_NFC_ASSET_ALREADY_CLAIMED
10508 PDC_NFC_IDEMPOTENCY_CONFLICT
10509 PDC_NFC_BATCH_NOT_FOUND
10510 PDC_NFC_JOB_NOT_FOUND
10511 PDC_NFC_JOB_CONFLICT
10512 PDC_NFC_WECHAT_NFC_ERROR
10513 PDC_NFC_CSV_FORMAT_ERROR
10514 PDC_NFC_CSV_CONTENT_MISMATCH
10515 PDC_NFC_BULK_LIMIT_EXCEEDED
10516 PDC_NFC_PHONE_REQUIRED
10517 PDC_NFC_RATE_LIMITED
10518 PDC_NFC_INVALID_PROTOTYPE
10519 PDC_NFC_INVALID_MODEL_ID
10520 PDC_NFC_WRITE_RESULT_CONFLICT
```

每个 code 在两个 properties 文件有用户安全文案；微信内部 errmsg、数据库异常和 secret 不返回客户端。

- [ ] **Step 3: 增加指标**

固定 metric names：

```text
pdc.nfc.scheme.requests / successes / failures / deferred
pdc.nfc.write.imports / verify_failures / scrapped
pdc.nfc.inventory.stocked / activated / disabled
pdc.nfc.claim.preview / success / conflict / invalid_ref / multi_user_alert
pdc.nfc.permission.denied
```

tag 只能是低基数字段如 result/errorCode/prototype，不得 tag assetId、userId、sn、claim hash。

- [ ] **Step 4: 增加敏感日志扫描**

测试扫描 `xiaozhi/modules/pdc/nfc` 源码，拒绝 `log.*` 参数直接引用 `claimRef`、`schemeCiphertext`、`accessToken`、`appSecret`；运行期 Logback 捕获测试确保业务异常文本不含测试 ref/token。

- [ ] **Step 5: 运行后端全量验收**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn verify -DskipTests=false
```

Expected: 全部测试 PASS；JaCoCo 对 `xiaozhi.modules.pdc.nfc.*` line 和 branch 都不低于 80%；`target/site/jacoco/index.html` 已生成。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/xiaozhi/common/exception src/main/java/xiaozhi/modules/pdc src/main/resources/i18n src/test/java/xiaozhi/modules/pdc
git commit -m "feat: add nfc audit and metrics"
```

---

### Task 16: 建立 manager-web NFC API、路由权限和菜单壳

**Files:**
- Create: `main/manager-web/src/apis/module/pdcNfc.js`
- Modify: `main/manager-web/src/apis/api.js`
- Create: `main/manager-web/src/router/access.mjs`
- Modify: `main/manager-web/src/router/index.js`
- Modify: `main/manager-web/src/components/HeaderBar.vue`
- Modify: `main/manager-web/src/i18n/zh_CN.js`
- Modify: `main/manager-web/src/i18n/zh_TW.js`
- Modify: `main/manager-web/src/i18n/en.js`
- Modify: `main/manager-web/src/i18n/de.js`
- Modify: `main/manager-web/src/i18n/vi.js`
- Modify: `main/manager-web/src/i18n/pt_BR.js`
- Create: `main/manager-web/tests/pdcNfcAccess.test.mjs`
- Create: `main/manager-web/tests/pdcNfcApi.test.mjs`

**Interfaces:**
- Consumes: Tasks 6-15 REST endpoints、Vuex/localStorage `userInfo.superAdmin`。
- Produces: `Api.pdcNfc.*`；路由 meta `requiresAuth/requiresSuperAdmin`；只有 superAdmin 可见和可直达的生产管理菜单。

- [ ] **Step 1: 写路由授权失败测试**

```js
test('NFC route denies direct navigation to normal user', () => {
  const route = { meta: { requiresAuth: true, requiresSuperAdmin: true } };
  assert.deepEqual(canAccessRoute(route, 'token', { superAdmin: false }), {
    allowed: false,
    redirect: '/home'
  });
});

test('reads persisted user during refresh race', () => {
  const storage = { getItem: () => '{"superAdmin":true}' };
  assert.equal(readStoredUserInfo(storage).superAdmin, true);
});
```

- [ ] **Step 2: 运行并确认失败**

Run from `main/manager-web`:

```bash
node --test tests/pdcNfcAccess.test.mjs tests/pdcNfcApi.test.mjs
```

Expected: FAIL，模块不存在。

- [ ] **Step 3: 实现纯函数 route gate**

```js
export function canAccessRoute(route, token, userInfo) {
  if (route.meta?.requiresAuth && !token) {
    return { allowed: false, redirect: '/login' };
  }
  if (route.meta?.requiresSuperAdmin && userInfo?.superAdmin !== true) {
    return { allowed: false, redirect: '/home' };
  }
  return { allowed: true, redirect: null };
}
```

router guard 先读 Vuex `store.state.userInfo`，为空时安全解析 `localStorage.userInfo`；JSON 损坏按普通用户处理。现有 hardcoded `protectedRoutes` 改为 route meta，同时为已有保护路由补 meta，不能降低原有登录保护。

- [ ] **Step 4: 注册五个 NFC 路由和菜单**

```text
/nfc-product-types
/nfc-batches
/nfc-assets
/nfc-activation
/nfc-operation-logs
```

每条 meta 都是 `{ requiresAuth: true, requiresSuperAdmin: true }`。HeaderBar 新增 `v-if="userInfo.superAdmin"` 的“生产管理”下拉、active path 和 routerPaths。

六个 locale 都添加同一组 key：`header.productionManagement`、`nfc.productTypes`、`nfc.batches`、`nfc.assets`、`nfc.activation`、`nfc.operationLogs`。简体/繁体使用对应中文；en/de/vi/pt_BR 首版使用准确本地化文字，不复制未翻译说明文本。

- [ ] **Step 5: 实现 API 模块**

`pdcNfc.js` 固定导出下列方法，所有 mutation 都不调用 `reAjaxFun()`：

```js
import { getServiceUrl } from '../api'
import RequestService from '../httpRequest'

const base = () => `${getServiceUrl()}/pdc/nfc/admin`

function send(method, url, data, success, fail, responseType, headers) {
  const request = RequestService.sendRequest()
    .url(`${base()}${url}`)
    .method(method)
    .data(data || {})
    .success(success)
    .fail(fail)
  if (responseType) request.type(responseType)
  if (headers) request.header(headers)
  return request.networkFail(fail).send()
}

export default {
  listProductTypes: (params, ok, fail) =>
    send('GET', '/product-types', params, ok, fail),
  getProductType: (id, ok, fail) =>
    send('GET', `/product-types/${id}`, null, ok, fail),
  recordReleaseEvidence: (body, ok, fail) =>
    send('POST', '/release-readiness/evidence', body, ok, fail),

  createBatch: (body, ok, fail) =>
    send('POST', '/batches', body, ok, fail),
  listBatches: (params, ok, fail) =>
    send('GET', '/batches', params, ok, fail),
  getBatch: (id, ok, fail) =>
    send('GET', `/batches/${id}`, null, ok, fail),
  cancelBatch: (id, body, ok, fail) =>
    send('POST', `/batches/${id}/cancel`, body, ok, fail),

  generateSchemes: (batchId, body, ok, fail) =>
    send('POST', `/batches/${batchId}/schemes/generate`, body, ok, fail),
  getSchemeProgress: (batchId, ok, fail) =>
    send('GET', `/batches/${batchId}/schemes/progress`, null, ok, fail),
  retrySchemes: (batchId, body, ok, fail) =>
    send('POST', `/batches/${batchId}/schemes/retry`, body, ok, fail),
  cancelSchemeJob: (jobId, body, ok, fail) =>
    send('POST', `/scheme-jobs/${jobId}/cancel`, body, ok, fail),

  createWriteJob: (batchId, body, ok, fail) =>
    send('POST', `/batches/${batchId}/write-jobs`, body, ok, fail),
  getWriteJob: (jobId, ok, fail) =>
    send('GET', `/write-jobs/${jobId}`, null, ok, fail),
  downloadWriteJob: (jobId, ok, fail) =>
    send('GET', `/write-jobs/${jobId}/file`, null, ok, fail, 'blob'),
  importWriteResults: (jobId, formData, ok, fail) =>
    send('POST', `/write-jobs/${jobId}/results`, formData, ok, fail, null, {}),
  cancelWriteJob: (jobId, body, ok, fail) =>
    send('POST', `/write-jobs/${jobId}/cancel`, body, ok, fail),

  stockInAssets: (body, ok, fail) =>
    send('POST', '/assets/stock-in', body, ok, fail),
  activateAssets: (body, ok, fail) =>
    send('POST', '/assets/activate', body, ok, fail),
  disableAsset: (id, body, ok, fail) =>
    send('POST', `/assets/${id}/disable`, body, ok, fail),
  scrapAsset: (id, body, ok, fail) =>
    send('POST', `/assets/${id}/scrap`, body, ok, fail),
  listAssets: (params, ok, fail) =>
    send('GET', '/assets', params, ok, fail),
  getAsset: (id, ok, fail) =>
    send('GET', `/assets/${id}`, null, ok, fail),
  listAssetLogs: (id, params, ok, fail) =>
    send('GET', `/assets/${id}/operation-logs`, params, ok, fail),
  listOperationLogs: (params, ok, fail) =>
    send('GET', '/operation-logs', params, ok, fail)
}
```

结果上传传 `FormData` 且不手工设置 multipart Content-Type，让 Fly 自动生成 boundary。API 测试通过 mock `RequestService` 逐项断言 method、URL、body 和 responseType；还要断言上述 mutation 发生网络错误时只调用 `fail`，不进入自动重放。

- [ ] **Step 6: 运行管理端验证并提交**

```bash
npm run test:unit
npm run check:i18n
npm run build
git add src/apis src/router src/components/HeaderBar.vue src/i18n tests
git commit -m "feat: add nfc admin navigation"
```

Expected: unit、i18n、production build 全部 PASS；普通用户手输 NFC URL 被重定向到 home。

---

### Task 17: 实现商品、批次、Scheme 和写卡生产页面

**Files:**
- Create: `main/manager-web/src/utils/pdcNfcState.mjs`
- Create: `main/manager-web/src/views/nfc/NfcProductTypeManagement.vue`
- Create: `main/manager-web/src/views/nfc/NfcBatchManagement.vue`
- Create: `main/manager-web/src/components/nfc/NfcBatchDialog.vue`
- Create: `main/manager-web/src/components/nfc/NfcWriteJobDialog.vue`
- Create: `main/manager-web/src/components/nfc/NfcWriteResultImportDialog.vue`
- Create: `main/manager-web/tests/pdcNfcState.test.mjs`
- Create: `main/manager-web/tests/pdcNfcProductionWorkflow.test.mjs`

**Interfaces:**
- Consumes: `Api.pdcNfc`、后端 batch/job 状态。
- Produces: 固定商品类型只读、批次创建、Scheme 进度轮询、写卡任务创建/认证下载/结果导入 UI。

- [ ] **Step 1: 写按钮状态和 model 状态失败测试**

```js
test('buttons follow backend batch state', () => {
  assert.deepEqual(batchActions('DRAFT'), {
    generateScheme: true, createWriteJob: false, stockIn: false
  });
  assert.equal(batchActions('READY_FOR_WRITE').createWriteJob, true);
});

test('blank model id renders pending review', () => {
  assert.equal(modelIdLabel({ modelId: null }), '待微信审核配置');
});
```

- [ ] **Step 2: 运行并确认失败**

```bash
node --test tests/pdcNfcState.test.mjs tests/pdcNfcProductionWorkflow.test.mjs
```

Expected: FAIL，pure state module 和页面流程不存在。

- [ ] **Step 3: 实现只读商品类型页**

使用 `HeaderBar`、`CustomTable`；显示 type code/name、页面路径、能力模式、model 配置状态、release-ready 和最新 release evidence。没有新增、编辑、删除、启停按钮；“登记发布证据”是独立运维操作，要求版本、发布时间、smoke evidence，提交后只刷新视图。

- [ ] **Step 4: 实现批次页和创建对话框**

字段严格为 batch no、固定 product type、SKU、prototype（锦鲤/玉兔）、planned quantity 1..10000、remark。列表显示 Scheme、written、verified、in-stock、active、claimed、scrapped 聚合值；所有 action 由 `pdcNfcState.mjs` 基于后端状态决定。

- [ ] **Step 5: 实现 Scheme/write 流程**

Scheme generate 成功后每 2 秒轮询 progress；路由离开、job terminal 或组件销毁立即 clear timer。写卡下载通过带 token 的 blob 请求创建临时 `<a download>`，finally 调 `URL.revokeObjectURL()`；不得 `window.open()`。导入对话框只接收一个 `.csv`、生成 UUID requestId、使用 `FormData` 提交，同一次显式重试复用 requestId。

- [ ] **Step 6: 增加流程测试和 build**

纯流程测试覆盖按钮禁用、防重复 submit、轮询停止、下载 revoke、上传不设置 multipart header、网络失败不自动创建第二个 job。

```bash
npm run test:unit
npm run verify:nfc-coverage
npm run check:i18n
npm run build
```

Expected: NFC pure modules line/branch >=80%，其余命令 PASS。

- [ ] **Step 7: 提交**

```bash
git add src/views/nfc src/components/nfc src/utils/pdcNfcState.mjs tests
git commit -m "feat: add nfc production workflow pages"
```

---

### Task 18: 实现资产、审计和扫码出库激活页面

**Files:**
- Create: `main/manager-web/src/views/nfc/NfcAssetManagement.vue`
- Create: `main/manager-web/src/views/nfc/NfcActivationManagement.vue`
- Create: `main/manager-web/src/views/nfc/NfcOperationLogManagement.vue`
- Create: `main/manager-web/src/components/nfc/NfcAssetDetailDialog.vue`
- Create: `main/manager-web/src/components/nfc/NfcStockInDialog.vue`
- Create: `main/manager-web/src/components/nfc/NfcActivationScanner.vue`
- Modify: `main/manager-web/src/utils/pdcNfcState.mjs`
- Create: `main/manager-web/tests/pdcNfcActivation.test.mjs`
- Create: `main/manager-web/tests/pdcNfcSensitiveView.test.mjs`

**Interfaces:**
- Consumes: Task 12 资产/库存/日志 API。
- Produces: 全筛选资产查询、安全详情、批量入库、键盘扫码激活、全局/资产日志。

- [ ] **Step 1: 写扫码去重和敏感字段失败测试**

```js
test('scanner trims, de-duplicates and caps at 500', () => {
  let state = emptyScannerState();
  state = acceptScan(state, ' A001\n');
  state = acceptScan(state, 'A001');
  assert.deepEqual(state.codes, ['A001']);
});

test('asset presentation drops secrets', () => {
  const view = presentAsset({
    assetNo: 'A1', claimRef: 'secret', schemeCiphertext: 'cipher', schemeSha256: 'sha'
  });
  assert.deepEqual(view, { assetNo: 'A1', schemeSha256: 'sha' });
});
```

- [ ] **Step 2: 运行并确认失败**

```bash
node --test tests/pdcNfcActivation.test.mjs tests/pdcNfcSensitiveView.test.mjs
```

Expected: FAIL，scanner/presentation 函数不存在。

- [ ] **Step 3: 实现资产查询和详情**

落实 asset no、sn、batch、SKU、prototype、status、businessNo、petId/userId、时间范围筛选；详情仅渲染后端 allowlist VO。Vue data、DOM、console 中不得出现 claimRef、claim hash、ciphertext 或完整 Scheme。

- [ ] **Step 4: 实现入库和扫码激活**

扫码枪按键盘输入处理，Enter 完成一条；trim、客户端去重、最多 500。每个 code 先请求资产摘要并只把 `IN_STOCK` 放入待激活清单；提交前显示数量/businessNo 并二次确认，生成一个 UUID requestId。网络失败保留清单和 requestId，成功后清空；后端仍做最终全量校验。

- [ ] **Step 5: 实现日志页**

全局页调用 `/operation-logs` 分页，资产详情调用资产级日志；只显示 operator、operation、object、before/after、count、businessNo、result、errorCode、time 和安全 detail。用户信息使用脱敏 VO。

- [ ] **Step 6: 验证并提交**

```bash
npm run test:unit
npm run verify:nfc-coverage
npm run check:i18n
npm run build
git add src/views/nfc src/components/nfc src/utils/pdcNfcState.mjs tests
git commit -m "feat: add nfc inventory admin pages"
```

Expected: 全部 PASS；普通用户无菜单且不能访问路由，生产 CSV 下载仍逐次带 Bearer token。

---

### Task 19: 实现小程序 NFC 冷/暖启动意图

**Files:**
- Create: `main/egg-miniprogram/miniprogram/utils/nfc-claim-intent.js`
- Create: `main/egg-miniprogram/miniprogram/utils/nfc-claim-intent.test.js`
- Modify: `main/egg-miniprogram/miniprogram/app.js`
- Modify: `main/egg-miniprogram/miniprogram/app.test.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/account/account.js`

**Interfaces:**
- Consumes: WeChat `onLaunch/onShow` options `{ path, query: { v, ref } }`。
- Produces: `captureNfcClaimIntent(options, now)`、`getPendingNfcClaimIntent(now)`、`clearPendingNfcClaimIntent()`、`isNfcClaimPath(path)`；TTL 30 分钟。

- [ ] **Step 1: 写纯意图失败测试**

```js
test('captures cold and warm launch, replaces old ref and expires after 30 minutes', () => {
  captureNfcClaimIntent({ path: 'pages/nfc-claim/nfc-claim',
    query: { v: '1', ref: REF_ONE } }, 1_000);
  assert.equal(getPendingNfcClaimIntent(1_001).claimRef, REF_ONE);

  captureNfcClaimIntent({ path: '/pages/nfc-claim/nfc-claim',
    query: { v: '1', ref: REF_TWO } }, 2_000);
  assert.equal(getPendingNfcClaimIntent(2_001).claimRef, REF_TWO);
  assert.equal(getPendingNfcClaimIntent(2_000 + 30 * 60 * 1000 + 1), null);
});
```

- [ ] **Step 2: 运行并确认失败**

Run from `main/egg-miniprogram`:

```bash
node miniprogram/utils/nfc-claim-intent.test.js
node miniprogram/app.test.js
node miniprogram/pages/welcome/welcome.test.js
```

Expected: FAIL，intent module 不存在，app 暖启动不捕获 options。

- [ ] **Step 3: 实现安全存储模块**

```js
const STORAGE_KEY = 'eggbaby_nfc_claim_intent_v1';
const TTL_MS = 30 * 60 * 1000;

function captureNfcClaimIntent(options, now = Date.now()) {
  if (!isNfcClaimPath(options && options.path)) return getPendingNfcClaimIntent(now);
  const query = options.query || {};
  if (String(query.v) !== '1' || !/^[A-Za-z0-9_-]{22}$/.test(query.ref || '')) {
    clearPendingNfcClaimIntent();
    return null;
  }
  const intent = {
    type: 'NFC_CLAIM', version: 1, claimRef: query.ref,
    capturedAt: now, expiresAt: now + TTL_MS
  };
  wx.setStorageSync(STORAGE_KEY, intent);
  return intent;
}
```

不得 console/埋点输出 intent 或 ref；读取遇到损坏/过期立即清缓存。

- [ ] **Step 4: 改造 app 和欢迎页路由**

`onLaunch(options)` 和 `onShow(options)` 第一行先 capture，再进行登录/重定向。`redirectUnboundToWelcome()` 对 NFC claim route 不重定向自己；未登录/未绑定用户先保留意图走现有欢迎页，欢迎页点击进入或检测已绑定时，有有效意图使用 `wx.navigateTo('/pages/nfc-claim/nfc-claim')`，无意图保持 switchTab home。

账号退出时同时 clear NFC intent，防止换账号继承。

- [ ] **Step 5: 覆盖冷暖启动和清理回归**

测试冷启动、暖启动、非法 v/ref、旧意图替换、过期、新用户欢迎页、已绑定用户直接领取页、普通启动仍首页、logout 清理、`onShow` 不重复导航。

- [ ] **Step 6: 运行测试并提交**

```bash
node miniprogram/utils/nfc-claim-intent.test.js
node miniprogram/app.test.js
node miniprogram/pages/welcome/welcome.test.js
node miniprogram/pages/account/account.test.js
git add miniprogram/utils/nfc-claim-intent.js miniprogram/utils/nfc-claim-intent.test.js miniprogram/app.js miniprogram/app.test.js miniprogram/pages/welcome miniprogram/pages/account
git commit -m "feat: persist nfc launch intent"
```

---

### Task 20: 实现小程序领取 API 和页面状态机

**Files:**
- Create: `main/egg-miniprogram/miniprogram/utils/nfc-claim-api.js`
- Create: `main/egg-miniprogram/miniprogram/utils/nfc-claim-api.test.js`
- Create: `main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.js`
- Create: `main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.json`
- Create: `main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.wxml`
- Create: `main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.wxss`
- Create: `main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.test.js`
- Modify: `main/egg-miniprogram/miniprogram/app.json`
- Modify: `main/egg-miniprogram/scripts/verify-project.js`

**Interfaces:**
- Consumes: Task 19 intent、现有 `wechatApi.bindPhone/auth.markPhoneBound/app.applySession/petStore.savePetFromVO`。
- Produces: `previewClaim(claimRef)`、`confirmClaim(claimRef, requestId)`、`createRequestId()`；页面 10 态。

- [ ] **Step 1: 写 API 路径和 UUID 失败测试**

```js
test('preview and confirm use claim endpoints without NFC adapter', async () => {
  await previewClaim(REF);
  assert.deepEqual(calls[0], {
    method: 'POST', url: '/pdc/nfc/claim/preview', data: { claimRef: REF }
  });
  const requestId = createRequestId();
  assert.match(requestId,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  await confirmClaim(REF, requestId);
  assert.equal(sourceText.includes('getNFCAdapter'), false);
});
```

- [ ] **Step 2: 写页面状态机失败测试**

```js
test('network retry reuses requestId and duplicate taps submit once', async () => {
  const page = makePage({ preview: CLAIMABLE });
  await page.bootstrap();
  const first = page.onConfirm();
  const second = page.onConfirm();
  await Promise.allSettled([first, second]);
  assert.equal(confirmCalls.length, 1);
  const firstRequestId = confirmCalls[0].requestId;

  await page.onConfirm();
  assert.equal(confirmCalls[1].requestId, firstRequestId);
});
```

- [ ] **Step 3: 运行并确认失败**

```bash
node miniprogram/utils/nfc-claim-api.test.js
node miniprogram/pages/nfc-claim/nfc-claim.test.js
```

Expected: FAIL，API 和页面不存在。

- [ ] **Step 4: 实现 API 与安全 UUID**

```js
function createRequestId() {
  const bytes = new Uint8Array(16);
  if (typeof wx.getRandomValues !== 'function') {
    throw new Error('当前微信版本不支持安全随机数');
  }
  wx.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  return formatUuid(bytes);
}
```

API 只调用现有 `post`；不记录 claimRef/requestId。preview DTO 固定：

```js
{
  productName: String,
  prototype: '锦鲤' | '玉兔',
  claimStatus: 'CLAIMABLE' | 'CLAIMED_BY_SELF' | 'CLAIMED_BY_OTHER' | 'UNAVAILABLE',
  pet: Object | null
}
```

confirm DTO 固定 `{ claimStatus: 'CLAIMED'|'CLAIMED_BY_SELF', pet: PetVO }`。

- [ ] **Step 5: 实现页面四件套和 10 态**

状态：`BOOTSTRAPPING`、`NEED_PHONE`、`LOADING_PREVIEW`、`READY`、`SUBMITTING`、`SUCCESS`、`CLAIMED_BY_SELF`、`CLAIMED_BY_OTHER`、`UNAVAILABLE`、`NETWORK_ERROR`。

流程：

```text
读取有效 intent → ensureLogin → 无 phone 显示授权
→ bindPhone/markPhoneBound/app.applySession
→ preview → READY
→ 用户点击立即领取 → create/reuse requestId → confirm
→ petStore.savePetFromVO → clear intent → switchTab home
```

WXML 绑定字段必须命名 `petType`，不能使用保留属性 `prototype`。页面只显示商品名、原型和用户安全状态；不显示 ref/sn/tag/batch/inventory/user。加载和 preview 绝不调用 confirm。

- [ ] **Step 6: 实现授权拒绝、取消和重试**

手机号拒绝停留 `NEED_PHONE`，不清 intent、不请求 confirm；用户明确取消领取时清 intent 并回 home；network error 保留同一个 `_requestId`，业务冲突不自动重试。SUBMITTING 禁用按钮，成功后才清 requestId。

- [ ] **Step 7: 覆盖全部状态并验证工程**

```bash
node miniprogram/utils/nfc-claim-api.test.js
node miniprogram/pages/nfc-claim/nfc-claim.test.js
node scripts/verify-project.js
find miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
find miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
```

Expected: 全部 PASS，`app.json` 已注册 `pages/nfc-claim/nfc-claim`，页面有自定义 `nav-bar`。

- [ ] **Step 8: 提交**

```bash
git add miniprogram/utils/nfc-claim-api.js miniprogram/utils/nfc-claim-api.test.js miniprogram/pages/nfc-claim miniprogram/app.json scripts/verify-project.js
git commit -m "feat: add nfc claim miniprogram page"
```

---

### Task 21: 完成欢迎页、手机号恢复、多宠首页和小程序回归

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/home/home.test.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`
- Modify: `main/egg-miniprogram/miniprogram/app.test.js`
- Modify: `main/egg-miniprogram/miniprogram/pages/add-device/add-device.test.js`
- Modify: `main/egg-miniprogram/miniprogram/utils/pet-store.test.js`

**Interfaces:**
- Consumes: Tasks 19-20、新宠 `petStore.activePetId`。
- Produces: NFC 意图跨登录/欢迎/手机号恢复；普通邀请码流程和首页行为不回归。

- [ ] **Step 1: 写 active pet 恢复失败测试**

```js
test('server restore prefers active pet after NFC claim', async () => {
  petStore.getActivePetId = () => 'nfc-pet';
  requestGet.resolves([{ id: 'old-pet' }, { id: 'nfc-pet' }]);
  await page.loadPetFromServer();
  assert.equal(savedPet.id, 'nfc-pet');
});
```

- [ ] **Step 2: 写登录恢复矩阵失败测试**

覆盖：

```text
冷启动 + 无 session + NFC intent → 静默登录 → welcome → claim
冷启动 + 有 session/无 phone → welcome → claim 页授权
冷启动 + 有 session/有 phone → claim
暖启动 + 新标签 → 新 intent 替换旧 intent → claim
手机号拒绝 → 无核销 → 再次触碰仍可恢复
无 NFC intent → 原 welcome/home 流程
```

- [ ] **Step 3: 运行并确认失败**

```bash
node miniprogram/app.test.js
node miniprogram/pages/welcome/welcome.test.js
node miniprogram/pages/home/home.test.js
node miniprogram/pages/add-device/add-device.test.js
```

Expected: 至少 active pet 和完整恢复矩阵 FAIL。

- [ ] **Step 4: 修正首页多宠恢复**

```js
const activePetId = petStore.getActivePetId();
const selected = activePetId
  ? list.find((pet) => pet.id === activePetId) || list[0]
  : list[0];
if (selected) this.renderPet(petStore.savePetFromVO(selected));
```

不改变列表为空、网络失败和邀请码页行为。

- [ ] **Step 5: 完成导航竞态保护**

App/Welcome/NFC page 共用一次性导航锁；捕获意图早于任何 reLaunch；NFC 非 tab 页使用 `navigateTo`，home 使用 `switchTab`。登录 promise、onShow 和 welcome click 同时完成时只产生一次导航。

- [ ] **Step 6: 运行小程序全量测试和覆盖率**

```bash
find miniprogram -type f -name '*.test.js' -print0 | xargs -0 -n1 node
node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-branches=80 \
  --test-coverage-include=miniprogram/utils/nfc-claim-intent.js \
  --test-coverage-include=miniprogram/utils/nfc-claim-api.js \
  miniprogram/utils/nfc-claim-intent.test.js \
  miniprogram/utils/nfc-claim-api.test.js \
  miniprogram/pages/nfc-claim/nfc-claim.test.js
node scripts/verify-project.js
find miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
find miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
```

Expected: 所有现有与 NFC 测试 PASS，NFC line/branch >=80%，邀请码 adopt 回归通过。

- [ ] **Step 7: 提交**

```bash
git add miniprogram/app.js miniprogram/app.test.js miniprogram/pages/welcome miniprogram/pages/home miniprogram/pages/add-device miniprogram/utils/pet-store.test.js
git commit -m "feat: resume nfc claim across authentication"
```

---

### Task 22: 发布候选验证、运维文档和外部量产门

**Files:**
- Create: `main/manager-api/docs/egg-nfc-operations.md`
- Modify: `main/docs/egg-nfc-feature-spec.md`
- Modify: `docs/superpowers/plans/2026-07-29-egg-nfc-feature.md`
- Update generated graph: `graphify-out/`

**Interfaces:**
- Consumes: Tasks 1-21 全部成果。
- Produces: 可审计发布候选报告、配置/密钥轮换/任务恢复/工厂交接 runbook；代码完成与外部量产批准分离。

- [ ] **Step 1: 写运维 runbook 的可执行内容**

`egg-nfc-operations.md` 必须逐项给出：

```text
所有 PDC_NFC_* 环境变量和 fail-closed 默认值
active/previous HMAC 与 AES key 生成、轮换、回退、旧 key 退役步骤
model ID 注入和 release evidence 登记命令/API
Scheme job FAILED/44993/expired lease 的恢复步骤
写卡 CSV 下载审计、工厂交接、删除确认和结果导入步骤
批量入库/激活 requestId 重试规则
紧急关闭四个功能开关的顺序
已生成 Scheme 页面不可删除、已领取宠物不可回滚的约束
敏感日志/指标排查方式
```

- [ ] **Step 2: 运行后端发布候选门**

```bash
cd main/manager-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn verify -DskipTests=false
```

Expected: build PASS、全部测试 PASS、PDC line/branch >=80%、真实 MySQL migration/concurrency tests PASS。

- [ ] **Step 3: 运行 manager-web 发布候选门**

```bash
cd ../manager-web
npm ci
npm run test:snapshot
npm run test:unit
npm run verify:nfc-coverage
npm run check:i18n
npm run build
```

Expected: 全部 PASS，NFC pure modules line/branch >=80%。

- [ ] **Step 4: 运行小程序发布候选门**

```bash
cd ../egg-miniprogram
find miniprogram -type f -name '*.test.js' -print0 | xargs -0 -n1 node
node --test --experimental-test-coverage --test-coverage-lines=80 --test-coverage-branches=80 \
  --test-coverage-include=miniprogram/utils/nfc-claim-intent.js \
  --test-coverage-include=miniprogram/utils/nfc-claim-api.js \
  miniprogram/utils/nfc-claim-intent.test.js \
  miniprogram/utils/nfc-claim-api.test.js \
  miniprogram/pages/nfc-claim/nfc-claim.test.js
node scripts/verify-project.js
find miniprogram -type f -name '*.js' -print0 | xargs -0 -n1 node --check
find miniprogram -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
```

Expected: 全部 PASS。随后用微信开发者工具导入 `main/egg-miniprogram/`，完成编译、预览和真机 smoke；CLI 自动测试不能替代真机。

- [ ] **Step 5: 做安全和 diff 复核**

```bash
cd ../../
rg -n "claimRef|access[_-]?token|appSecret|schemeCiphertext" main/manager-api/src/main/java/xiaozhi/modules/pdc main/egg-miniprogram/miniprogram/pages/nfc-claim
git diff --check
git diff --stat origin/egg-dev...HEAD
git status --short
graphify update .
```

Expected: 敏感词只出现在 DTO/受控变量或禁止日志测试中，没有实际 secret/ref/Scheme；diff 仅存在于 NFC worktree；graphify 更新成功。

- [ ] **Step 6: 更新文档状态并提交**

Spec 状态更新为“实现完成，等待外部量产验收”，列出实际 API/迁移/测试报告，不填虚假的 model ID 或设备结果。计划只勾选已真实完成的 checkbox。

```bash
git add main/docs/egg-nfc-feature-spec.md main/manager-api/docs/egg-nfc-operations.md docs/superpowers/plans/2026-07-29-egg-nfc-feature.md graphify-out
git commit -m "docs: add nfc production runbook"
```

- [ ] **Step 7: 执行外部量产验收门**

只有下列证据全部真实取得后，运维人员才把 `PDC_NFC_RELEASE_READY` 和三个业务开关置为 true：

```text
微信后台正式 model_id 和一机一码 NFC 能力审核通过
发布版确实包含 /pages/nfc-claim/nfc-claim
superAdmin 已登记版本、发布时间和 smoke evidence
工厂设备用真实 CSV 完成逐卡 URI+AAR 写入、回读、追溯和锁卡
真实 NDEF 容量验证通过
Android 微信 8.0.14+、iPhone XS+ 冷暖启动通过
两台手机并发领取恰好一个成功
试产批次 Scheme/write/verified/in-stock/active/claimed/scrapped 数量完全对账
工厂确认删除含完整 Scheme 的交接文件
邀请码领养、孵化、破壳回归通过
```

任一证据缺失时保持开关关闭；代码测试完成不得表述为“量产验收完成”。

## Final Execution Order

按 Task 1 → 22 严格顺序执行。Task 1-15 完成前不得联调管理端；Task 14 并发事务门未通过不得开启 claim；Task 9 恢复门未通过不得批量生成 Scheme；Task 11 仅证明逻辑 CSV 契约，必须等 Task 22 的真实工厂设备门通过后才能量产。

## Spec Coverage Matrix

| Spec section | Implementation tasks |
|---|---|
| 1-4 背景、目标、并行保留邀请码 | Tasks 5, 14, 21 |
| 5 model ID、发布页和外部条件 | Tasks 4, 6, 8, 9, 22 |
| 6 微信配置、generatenfcscheme、NDEF | Tasks 8-10, 22 |
| 7 领域边界 | Tasks 2-6, 14 |
| 8 全部 11 张 pdc_ 表 | Tasks 2-3 |
| 9 资产/批次/job 状态和并发 | Tasks 3, 6, 9-14 |
| 10 管理/领取接口和权限 | Tasks 6, 9-15 |
| 11 小程序启动意图和页面 | Tasks 19-21 |
| 12 管理后台 | Tasks 16-18 |
| 13 工厂 CSV 和设备兼容 | Tasks 10-11, 17, 22 |
| 14 密钥、静态标签风险、限流和 CSV 安全 | Tasks 4, 10-15, 22 |
| 15 异常处理 | Tasks 8-15, 20 |
| 16 监控与审计 | Tasks 6, 9-15, 18 |
| 17 自动化/真机测试 | Tasks 1, 14-15, 17-22 |
| 18 发布、开关和回滚 | Tasks 4, 6, 9, 15, 22 |
| 19 上线验收门槛 | Task 22 |
| 20 官方参考 | Spec 保持为权威外部文档，Task 22 runbook 链接回 Spec |
