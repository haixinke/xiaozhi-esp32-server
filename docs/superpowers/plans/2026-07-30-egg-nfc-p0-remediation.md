# Egg NFC P0 Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复蛋宝宝 NFC 审查中确认的 8 个 P0 阻断，使 Scheme 生成、工厂写卡、库存流转和用户领取具备完整联调条件。

**Architecture:** 后端继续沿用现有 `pdc.nfc` 领域分层，但把写卡结果事务落库拆成独立 Spring Bean，并坚持 Scheme 明文仅在导出时短暂存在于内存。数据库、CSV、状态机和小程序导航以已确认的修复设计为唯一契约，每个行为修改使用 RED-GREEN-REFACTOR。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、Liquibase SQL、JUnit 5、Mockito、Node.js、微信小程序原生框架。

## Global Constraints

- 所有修改只发生在 `/Users/minwang/codes/github/xiaozhi-esp32-server/.worktrees/egg-nfc` 的 `feature/egg-nfc` 分支。
- 不覆盖或还原已有未提交文件：`main/egg-miniprogram/miniprogram/pages/nfc-claim/nfc-claim.js`、`main/manager-web/package-lock.json`、`main/docs/egg-nfc-lifecycle-flow.md`。
- Scheme URL 明文不得持久化到数据库、日志或管理端响应。
- 写卡 CSV 使用 `PDC_NFC_WRITE_V1`；结果 CSV 使用 `PDC_NFC_RESULT_V1`，字段顺序严格匹配 Spec 第 13 节。
- 管理端核心生产和库存接口继续要求 `superAdmin`；小程序领取接口不增加超管权限。
- Java 命令固定使用 `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`，Maven 测试显式传 `-DskipTests=false`。
- 每项生产代码修改前必须先新增并运行一个因目标缺陷而失败的测试。
- 每个任务完成后独立提交，使用 Conventional Commits。

---

### Task 1: Scheme 启动就绪门与发布证据

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcReadinessService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcAuditService.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcAuditServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcReleaseEvidenceDTO.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/vo/ReleaseEvidence.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcReadinessServiceTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcAuditServiceTest.java`

**Interfaces:**
- Consumes: `PdcNfcProperties#getModelId()`、`PdcNfcProperties#isReleaseReady()`。
- Produces: `PdcNfcAuditService#hasCurrentReleaseEvidence()`，供 `PdcNfcReadinessService#requireSchemeGenerationReady()` 调用。
- Failure: 空白或 `<...>` model ID 抛 `PDC_NFC_MODEL_ID_NOT_CONFIGURED`；缺少证据抛 `PDC_NFC_RELEASE_NOT_READY`。

- [ ] **Step 1: 写空 model ID 和缺发布证据的失败测试**

```java
@Test
void rejectsBlankModelIdBeforeSchemeJobCreation() {
    properties.setEnabled(true);
    properties.setSchemeGenerationEnabled(true);
    properties.setReleaseReady(true);
    properties.setModelId(" ");
    assertThatThrownBy(readinessService::requireSchemeGenerationReady)
            .isInstanceOf(RenException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.PDC_NFC_MODEL_ID_NOT_CONFIGURED);
}

@Test
void rejectsPlaceholderModelId() {
    properties.setModelId("<WECHAT_MODEL_ID>");
    assertThatThrownBy(readinessService::requireSchemeGenerationReady)
            .isInstanceOf(RenException.class);
}

@Test
void rejectsMissingCurrentReleaseEvidence() {
    properties.setModelId("MODEL_001");
    when(auditService.hasCurrentReleaseEvidence()).thenReturn(false);
    assertThatThrownBy(readinessService::requireSchemeGenerationReady)
            .isInstanceOf(RenException.class);
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```bash
cd main/manager-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn test -DskipTests=false -Dtest=PdcNfcReadinessServiceTest,PdcNfcAuditServiceTest
```

Expected: FAIL，当前就绪服务允许空 `model_id`，审计服务也没有当前发布证据查询。

- [ ] **Step 3: 实现最小就绪门和合法证据插入**

```java
String modelId = properties.getModelId();
if (modelId == null || modelId.isBlank()
        || (modelId.trim().startsWith("<") && modelId.trim().endsWith(">"))) {
    throw new RenException(ErrorCode.PDC_NFC_MODEL_ID_NOT_CONFIGURED);
}
if (!auditService.hasCurrentReleaseEvidence()) {
    throw new RenException(ErrorCode.PDC_NFC_RELEASE_NOT_READY);
}
```

发布证据插入必须设置：

```java
entry.setSource("ADMIN_API");
entry.setResult("SUCCESS");
entry.setCreateDate(new Date());
```

DTO 固定为 `releaseVersion`、`publishedAt`、`smokeEvidence`，证据查询只接受当前配置版本的最新成功记录。

- [ ] **Step 4: 运行定向测试并确认 GREEN**

Run Task 1 的 Maven 命令。

Expected: PASS，且 `PdcNfcSchemeJobServiceImpl#start` 的既有测试确认就绪异常发生时没有批次和任务写入。

- [ ] **Step 5: 提交**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc \
        main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc
git commit -m "fix: enforce NFC scheme readiness"
```

---

### Task 2: 首次 NFC 领取记录必填时间

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimServiceTest.java`

**Interfaces:**
- Consumes: `PdcNfcClaimRecordDao#insert(PdcNfcClaimRecordEntity)`。
- Produces: 首次成功领取记录的 `createDate` 始终非空。

- [ ] **Step 1: 写失败测试**

```java
@Test
void firstClaimPersistsCreateDate() {
    service.confirm(USER_ID, CLAIM_REF, REQUEST_ID);

    ArgumentCaptor<PdcNfcClaimRecordEntity> captor =
            ArgumentCaptor.forClass(PdcNfcClaimRecordEntity.class);
    verify(claimRecordDao).insert(captor.capture());
    assertThat(captor.getValue().getCreateDate()).isNotNull();
}
```

- [ ] **Step 2: 运行测试并确认 RED**

```bash
cd main/manager-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn test -DskipTests=false -Dtest=PdcNfcClaimServiceTest
```

Expected: FAIL，captured entity 的 `createDate` 为 null。

- [ ] **Step 3: 最小实现**

在领取记录插入前显式执行：

```java
record.setCreateDate(new Date());
```

- [ ] **Step 4: 运行测试并确认 GREEN**

Run Task 2 的 Maven 命令。

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java \
        main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcClaimServiceTest.java
git commit -m "fix: persist NFC claim creation time"
```

---

### Task 3: 安全写卡快照与规范写卡 CSV

**Files:**
- Modify: `main/manager-api/src/main/resources/db/changelog/202607291000.sql`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcWriteJobItemEntity.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteJobServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteCsvExporter.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteJobServiceTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteCsvExporterTest.java`
- Create: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/PdcNfcLiquibaseContractTest.java`
- Modify: `main/manager-api/src/test/resources/pdc/nfc/PDC_NFC_WRITE_V1.golden.csv`

**Interfaces:**
- Produces: `pdc_nfc_write_job_item.uri_sha256 CHAR(64) NOT NULL`。
- Removes: 持久化字段 `PdcNfcWriteJobItemEntity#uriPayload`。
- Produces: 写卡文件固定14列、`PDC_NFC_WRITE_V1`、UTF-8 BOM。

- [ ] **Step 1: 写数据库和 CSV 行为失败测试**

测试必须验证：

```java
assertThat(savedItem.getUriSha256()).isEqualTo(asset.getSchemeSha256());
assertThat(writeJobItemTableColumns).contains("uri_sha256");
assertThat(writeJobItemTableColumns).doesNotContain("uri_payload");
assertThat(csv).contains("PDC_NFC_WRITE_V1");
assertThat(csv).contains(",0x01,U,");
assertThat(csv).contains(",0x04,android.com:pkg,com.tencent.mm");
```

同时验证实体不再向数据库插入 `uri_payload`，完整 Scheme 只作为 exporter 的内存参数。

- [ ] **Step 2: 运行测试并确认 RED**

```bash
cd main/manager-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn test -DskipTests=false \
  -Dtest=PdcNfcWriteJobServiceTest,PdcNfcWriteCsvExporterTest,PdcNfcLiquibaseContractTest
```

Expected: FAIL，当前表缺少 `uri_sha256`，实体存在 `uriPayload`，格式版本仍为 `V1`。

- [ ] **Step 3: 修改表、实体和任务创建**

SQL 使用：

```sql
uri_sha256 CHAR(64) NOT NULL,
```

删除 `uri_payload` 映射。任务创建只复制 `asset.getSchemeSha256()`；导出时按 item 关联资产，
解密 `scheme_ciphertext` 后传给 exporter，不写回数据库。

- [ ] **Step 4: 统一写卡 CSV**

```java
static final String FORMAT_VERSION = "PDC_NFC_WRITE_V1";
static final String URI_TNF = "0x01";
static final String URI_TYPE = "U";
static final String AAR_TNF = "0x04";
```

更新 golden fixture，使表头和14列顺序与 Spec 13.1 完全一致。

- [ ] **Step 5: 运行测试并确认 GREEN**

Run Task 3 的 Maven 命令。

Expected: PASS；生成文件 SHA-256 和重复下载正文保持一致。

- [ ] **Step 6: 提交**

```bash
git add main/manager-api/src/main/resources/db/changelog/202607291000.sql \
        main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc \
        main/manager-api/src/test
git commit -m "fix: secure NFC write task snapshots"
```

---

### Task 4: 规范结果 CSV 与原子导入

**Files:**
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteResultTransactionService.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteResultTransactionServiceImpl.java`
- Create: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/ValidatedWriteResult.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteResultImporterImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dto/PdcNfcWriteResultRow.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteResultImporterTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteResultImportIntegrationTest.java`
- Modify: `main/manager-api/src/test/resources/pdc/nfc/PDC_NFC_RESULT_V1.valid.csv`

**Interfaces:**
- `PdcNfcWriteResultTransactionService#apply(Long jobId, List<ValidatedWriteResult> rows, Long operatorId, UUID requestId)` 是唯一事务落库入口。
- `ValidatedWriteResult` 是校验后的不可变 record，字段为 `PdcNfcWriteResultRow row`、`PdcNfcWriteJobItemEntity item`、`PdcNfcAssetEntity asset`、`PdcNfcAssetStatus targetStatus`、`boolean fullyVerified`。
- Importer 负责读取、限制和完整校验；transaction service 负责资产、记录、任务、批次和审计原子写入。
- 结果文件固定14列，不含完整 URI。

- [ ] **Step 1: 写14列结果文件失败测试**

fixture 表头固定为：

```csv
format_version,job_no,asset_no,wechat_sn,write_result,verify_result,tag_uid,ndef_record_count,uri_sha256,aar_package,is_read_only,written_at,error_code,error_message
```

测试验证 `PDC_NFC_RESULT_V1` 被接受，而旧20列 `V1` 文件被拒绝。

- [ ] **Step 2: 写事务回滚失败测试**

使用 Spring 代理调用真实 transaction service；让第二行写卡记录插入触发数据库异常，然后断言：

```java
assertThat(assetDao.selectById(firstAssetId).getStatus())
        .isEqualTo(PdcNfcAssetStatus.SCHEME_GENERATED.name());
assertThat(writeRecordDao.countByJobId(jobId)).isZero();
assertThat(writeJobDao.selectById(jobId).getStatus())
        .isEqualTo(PdcNfcWriteJobStatus.EXPORTED.name());
```

- [ ] **Step 3: 运行测试并确认 RED**

```bash
cd main/manager-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn test -DskipTests=false \
  -Dtest=PdcNfcWriteResultImporterTest,PdcNfcWriteResultImportIntegrationTest
```

Expected: 14列文件解析失败；事务回滚测试显示第一行已留下更新或无法通过真实代理验证。

- [ ] **Step 4: 实现14列解析和完整预检**

解析映射固定为：

```java
new PdcNfcWriteResultRow(
    formatVersion, jobNo, assetNo, wechatSn,
    writeResult, verifyResult, tagUid, ndefRecordCount,
    uriSha256, aarPackage, isReadOnly, writtenAt,
    errorCode, errorMessage
);
```

进入事务服务前完成重复、缺失、额外行、job/asset/sn、摘要、AAR、record 数量和只读标志校验。

- [ ] **Step 5: 实现独立事务 Bean**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public PdcNfcWriteImportVO apply(
        Long jobId,
        List<ValidatedWriteResult> rows,
        Long operatorId,
        UUID requestId) {
    // 全部数据库状态、记录、任务、批次和审计写入
}
```

Importer 通过构造器注入该接口，禁止再次使用同类 `this.doImport(...)`。

- [ ] **Step 6: 运行测试并确认 GREEN**

Run Task 4 的 Maven 命令。

Expected: PASS，失败导入无任何部分状态。

- [ ] **Step 7: 提交**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc \
        main/manager-api/src/test
git commit -m "fix: make NFC write result import atomic"
```

---

### Task 5: 批次写卡、入库生命周期推进

**Files:**
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteJobServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteResultTransactionServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcInventoryServiceImpl.java`
- Modify: `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dao/PdcNfcAssetDao.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteJobServiceTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcWriteResultImportIntegrationTest.java`
- Test: `main/manager-api/src/test/java/xiaozhi/modules/pdc/nfc/service/PdcNfcInventoryServiceTest.java`

**Interfaces:**
- 创建写卡任务：`READY_FOR_WRITE → WRITING`。
- 完成结果导入：`WRITING → READY_FOR_STOCK`。
- 最后一个待入库 `VERIFIED` 资产完成入库后：`READY_FOR_STOCK → COMPLETED`。

- [ ] **Step 1: 为三个转换分别写失败测试**

```java
assertThat(batchDao.selectById(batchId).getStatus()).isEqualTo("WRITING");
assertThat(batchDao.selectById(batchId).getStatus()).isEqualTo("READY_FOR_STOCK");
assertThat(batchDao.selectById(batchId).getStatus()).isEqualTo("COMPLETED");
```

另加一条测试：仍存在 `VERIFIED` 资产时，部分入库不得提前进入 `COMPLETED`。

- [ ] **Step 2: 运行测试并确认 RED**

```bash
cd main/manager-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn test -DskipTests=false \
  -Dtest=PdcNfcWriteJobServiceTest,PdcNfcWriteResultImportIntegrationTest,PdcNfcInventoryServiceTest
```

Expected: 批次保持 `READY_FOR_WRITE`，三个转换断言失败。

- [ ] **Step 3: 实现事务内状态推进**

创建任务、结果事务和入库事务分别调用：

```java
batchStateMachine.requireTransition(current, target);
batch.setStatus(target.name());
batch.setUpdateDate(new Date());
batchDao.updateById(batch);
```

新增 DAO 计数：

```java
int countByBatchIdAndStatus(Long batchId, String status);
```

只有 `VERIFIED` 数量为0时才允许入库事务推进 `COMPLETED`。

- [ ] **Step 4: 运行测试并确认 GREEN**

Run Task 5 的 Maven 命令。

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc \
        main/manager-api/src/test
git commit -m "fix: advance NFC batch lifecycle"
```

---

### Task 6: 无手机号 NFC intent 导航到领取页

**Files:**
- Modify: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.js`
- Test: `main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js`
- Test: `main/egg-miniprogram/miniprogram/app.test.js`

**Interfaces:**
- Consumes: `getPendingNfcClaimIntent()`。
- Produces: 有 NFC intent 时优先 `wx.redirectTo('/pages/nfc-claim/nfc-claim')`，不要求 `hasPhone=true`。
- 普通无 NFC intent 用户保持原 welcome/home 行为。

- [ ] **Step 1: 写失败测试**

```javascript
test('NFC intent without phone enters claim page', async () => {
  auth.getSession.mockReturnValue({ userId: 1, hasPhone: false })
  getPendingNfcClaimIntent.mockReturnValue({ claimRef: VALID_REF })

  await page.onLoad()

  assert.deepEqual(wx.redirectTo.calls[0].arguments[0], {
    url: '/pages/nfc-claim/nfc-claim'
  })
})
```

再验证普通无 intent、无手机号用户仍显示 welcome；`onEnterIsland` 遇到 NFC intent 时也进入领取页。

- [ ] **Step 2: 运行测试并确认 RED**

```bash
cd main/egg-miniprogram/miniprogram
node pages/welcome/welcome.test.js
```

Expected: FAIL，当前 `hasPhone=false` 时不会调用 `navigateNfcClaim()`。

- [ ] **Step 3: 最小导航修复**

在 welcome `onLoad` 中，登录态确定后先检查 intent：

```javascript
if (this.navigateNfcClaim()) return
if (session && session.hasPhone === true) {
  wx.switchTab({ url: '/pages/home/home' })
}
```

`onEnterIsland()` 同样先执行 `navigateNfcClaim()`，无 intent 时再进入首页。

- [ ] **Step 4: 运行测试并确认 GREEN**

```bash
node pages/welcome/welcome.test.js
find . -name '*.test.js' -print0 | xargs -0 -n1 node
```

Expected: 小程序完整测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add main/egg-miniprogram/miniprogram/pages/welcome/welcome.js \
        main/egg-miniprogram/miniprogram/pages/welcome/welcome.test.js \
        main/egg-miniprogram/miniprogram/app.test.js
git commit -m "fix: resume NFC claim before phone binding"
```

---

### Task 7: P0 集成验证与最终审查

**Files:**
- Modify only if verification exposes a regression in Tasks 1-6.
- Verify: all NFC backend, manager-web and miniprogram test suites.

**Interfaces:**
- Consumes: Tasks 1-6 complete commits。
- Produces: P0 修复验证报告和最终独立代码审查结论。

- [ ] **Step 1: 运行后端 NFC 定向测试**

```bash
cd main/manager-api
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn test -DskipTests=false \
  -Dtest='PdcNfc*Test,WechatNfc*Test,WechatAccessTokenProviderImplTest,WechatPhoneGateTest,PetServiceImplCreateEggTest'
```

Expected: 除非外部数据库集成测试明确通过 Testcontainers 启动，否则不得以硬编码本机数据库作为成功条件；所有可移植测试必须通过。

- [ ] **Step 2: 运行管理端测试和构建**

```bash
cd main/manager-web
npm run verify:nfc-coverage
npm run build
```

Expected: 测试全部通过；构建成功，允许记录既有资源体积警告。

- [ ] **Step 3: 运行小程序完整测试**

```bash
cd main/egg-miniprogram/miniprogram
find . -name '*.test.js' -print0 | xargs -0 -n1 node
```

Expected: 全部 PASS。

- [ ] **Step 4: 检查工作树和差异**

```bash
git status --short
git diff --check HEAD~6..HEAD
git diff --stat HEAD~6..HEAD
```

确认未覆盖三个预存未提交文件，且没有 Scheme、claimRef、密钥或 token 明文进入日志和测试输出。

- [ ] **Step 5: 独立代码审查**

审查范围只包含本计划提交，必须验证：

- 设计契约全部满足。
- 没有新增 P0/P1 回归。
- 事务由 Spring 代理生效。
- CSV 和 SQL 字段完全一致。
- 用户领取链路不依赖 `model_id`。

- [ ] **Step 6: 完成进度台账**

将 Tasks 1-7 标记为 `completed`，记录每项提交范围、测试命令、测试数量以及剩余非 P0 问题。
