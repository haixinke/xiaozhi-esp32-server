# NFC 写卡任务取消后批次仍显示“写卡中”是否合理

> **已修复**（2026-08-29，commit 1b3db927）：`PdcNfcBatchStateMachine` 增加 `WRITING -> READY_FOR_WRITE` 转移，`PdcNfcWriteJobServiceImpl.cancel` 在释放资产租约后原子回退批次状态，失败即整体回滚。下文为修复前的调研记录。

## 结论先行

**不合理，属于遗漏。**

- 取消写卡任务只更新 `pdc_nfc_write_job` 的状态为 `CANCELLED`，不更新对应 `pdc_nfc_batch`。
- 批次进入 `WRITING` 后，状态机只允许 `WRITING -> READY_FOR_STOCK / CANCELLED`，没有回到 `READY_FOR_WRITE` 的过渡。
- 前端“创建写卡任务”按钮只在批次状态为 `READY_FOR_WRITE` 时显示；因此取消任务后，该批次既无法继续当前任务，也无法创建新任务，会处于事实上的卡住状态。

---

## 1. 状态与数据模型

### 1.1 批次状态

`PdcNfcBatchStatus.java:6-15` 定义了批次状态：

```java
public enum PdcNfcBatchStatus {
    DRAFT,
    SCHEME_GENERATING,
    READY_FOR_WRITE,
    WRITING,
    READY_FOR_STOCK,
    COMPLETED,
    CLOSED,
    CANCELLED
}
```

数据库中 `pdc_nfc_batch.status` 为 `VARCHAR(32) NOT NULL`：
`main/manager-api/src/main/resources/db/changelog/202607291000.sql:34`

实体字段见：
`main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcBatchEntity.java:41-42`

### 1.2 写卡任务状态

`PdcNfcWriteJobStatus.java:6-12` 定义了写卡任务状态：

```java
public enum PdcNfcWriteJobStatus {
    CREATED,
    EXPORTED,
    RESULT_IMPORTED,
    COMPLETED,
    CANCELLED
}
```

数据库中 `pdc_nfc_write_job.status` 为 `VARCHAR(32) NOT NULL`，另有 `cancelled_at` 字段：
`main/manager-api/src/main/resources/db/changelog/202607291000.sql:142`、`202607291000.sql:156`

实体字段见：
`main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcWriteJobEntity.java:38-39`、`PdcNfcWriteJobEntity.java:81`

---

## 2. 取消写卡任务的接口与实现

### 2.1 接口入口

`PdcNfcWriteJobAdminController.java:85-91` 暴露取消写卡任务接口：

```java
@PostMapping("/cancel/{jobId}")
@Operation(summary = "取消写卡任务")
public Result<Void> cancel(@PathVariable Long jobId) {
    Long operatorId = SecurityUser.getUserId();
    writeJobService.cancel(jobId, operatorId);
    return new Result<Void>().ok(null);
}
```

### 2.2 Service 实现

`PdcNfcWriteJobServiceImpl.java:241-277` 的 `cancel` 方法做了以下事情：

1. 加载并校验任务存在。
2. 仅允许 `CREATED/EXPORTED` 状态的任务取消，且没有已导入结果（`resultResponseJson == null`）。
3. 通过 `writeJobStateMachine` 校验到 `CANCELLED` 的状态转移。
4. 更新 `pdc_nfc_write_job` 的 `status`、`cancelled_at`、`updater`、`update_date`。
5. 对任务下所有资产调用 `assetDao.releaseWriteLease(...)`，释放 `active_write_job_id`。
6. 记录日志。

**该方法没有查询或更新 `pdc_nfc_batch`。** 这是关键事实。

---

## 3. 批次“写卡中”如何进入与离开

### 3.1 进入 WRITING

`PdcNfcWriteJobServiceImpl.java:82-131` 在创建写卡任务时：

- 要求批次当前状态为 `READY_FOR_WRITE`。
- 检查没有处于 `CREATED/EXPORTED` 的活跃写卡任务。
- 通过 `batchStateMachine.requireTransition(READY_FOR_WRITE, WRITING)` 校验，并调用 `batchDao.transitionStatus(...)` 原子地把批次状态从 `READY_FOR_WRITE` 翻转为 `WRITING`。

### 3.2 离开 WRITING

当前代码中只有两条路径能让 `WRITING` 离开：

| 目标状态 | 触发位置 | 条件 |
|---|---|---|
| `READY_FOR_STOCK` | `PdcNfcWriteResultTransactionServiceImpl.java:347-365` | 工厂 CSV 结果导入且全部资产验证通过 |
| `READY_FOR_STOCK` | `PdcNfcManualWriteServiceImpl.java:217-239` | 手动写卡模式全部资产验证通过 |
| `CANCELLED` | `PdcNfcBatchServiceImpl.java:172-186` | 调用独立的“取消批次”接口 |

批次状态机 `PdcNfcBatchStateMachine.java:19-26` 的允许转移：

```java
private static final Map<PdcNfcBatchStatus, Set<PdcNfcBatchStatus>> ALLOWED = Map.of(
    DRAFT, Set.of(SCHEME_GENERATING, CANCELLED),
    SCHEME_GENERATING, Set.of(READY_FOR_WRITE, CANCELLED),
    READY_FOR_WRITE, Set.of(WRITING, CANCELLED),
    WRITING, Set.of(READY_FOR_STOCK, CANCELLED),
    READY_FOR_STOCK, Set.of(COMPLETED, CANCELLED),
    COMPLETED, Set.of(CLOSED)
);
```

**注意：`WRITING` 只能去 `READY_FOR_STOCK` 或 `CANCELLED`，不能回到 `READY_FOR_WRITE`。**

---

## 4. 前端如何展示这两个状态

状态中文映射在 `main/manager-web/src/utils/pdcNfcState.mjs:68-100`：

```javascript
'WRITING': '写卡中',
'CANCELLED': '已取消',
```

`NfcWriteJobManagement.vue` 表格中：

- `56-60` 行展示“批次状态”列。
- `61-92` 行展示“写卡任务”列，包括任务状态标签、进度条、成功/失败数。

操作按钮可见性：

- `canCreate(row)`：`NfcWriteJobManagement.vue:299-301`，仅在 `row.status === 'READY_FOR_WRITE' && !row._writeJob` 时显示“创建任务”。
- `canCancel(row)`：`NfcWriteJobManagement.vue:319-322`，仅在任务状态为 `CREATED/EXPORTED` 时显示“取消”。

因此取消任务后，写卡任务列会变成“已取消”，但批次状态列仍显示“写卡中”，并且“创建任务”按钮不会再出现。

---

## 5. 基于代码事实：设计如此还是遗漏？

### 5.1 支持“设计如此”的证据

1. **写卡任务与批次是两个独立聚合**：
   - `PdcNfcWriteJobEntity.java:15` 的生命周期注释为 `CREATED → EXPORTED → RESULT_IMPORTED → COMPLETED / CANCELLED`，只描述任务本身。
   - 取消任务时只释放资产租约、不碰批次，符合“任务级撤销”的语义。

2. **前端把两个状态分开展示**：
   - 批次状态列和写卡任务状态列是独立的。
   - 另有独立的“取消批次”入口：`PdcNfcBatchAdminController.java:41-47`。

3. **批次状态机是单向推进模型**：
   - `PdcNfcBatchStateMachine.java:23` 只允许 `WRITING -> READY_FOR_STOCK / CANCELLED`，没有回退到 `READY_FOR_WRITE` 的转移，说明设计初衷是把 `WRITING` 视为不可逆阶段。

### 5.2 支持“遗漏”的证据

1. **取消任务后资产已可重新写卡，但批次被锁住**：
   - `PdcNfcWriteJobServiceImpl.java:270` 调用 `assetDao.releaseWriteLease(...)`，仅清空 `active_write_job_id`，不改变资产状态（资产仍为 `SCHEME_GENERATED`）。
   - 这意味着资产已经可以被新的写卡任务处理，但批次状态仍停留在 `WRITING`，无法创建新任务。

2. **创建写卡任务的前后端准入条件都基于 `READY_FOR_WRITE`**：
   - 后端：`PdcNfcWriteJobServiceImpl.java:82-85` 明确要求 `batch.status == READY_FOR_WRITE`。
   - 前端：`NfcWriteJobManagement.vue:299-301` 的 `canCreate` 同样基于 `READY_FOR_WRITE`。
   - 取消任务后批次仍为 `WRITING`，因此前后端都会拒绝再次创建任务。

3. **状态机与取消操作之间存在缺口**：
   - 即使服务层想在取消任务时把批次回退到 `READY_FOR_WRITE`，`PdcNfcBatchStateMachine.java:19-26` 也会抛出 `PDC_NFC_INVALID_STATE`。这不是“故意禁止回退”，更像是状态机没有覆盖“任务取消后允许重试”的完整场景。

---

## 6. 建议

**判定为遗漏。** 建议的语义是：取消一次写卡任务 = 放弃本次尝试，批次应回到“可写卡”状态，而不是卡在“写卡中”。

### 6.1 最小修复点

#### 修复点 1：允许批次从 WRITING 回退到 READY_FOR_WRITE

`main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcBatchStateMachine.java:19-26`

将

```java
WRITING, Set.of(READY_FOR_STOCK, CANCELLED),
```

改为

```java
WRITING, Set.of(READY_FOR_STOCK, READY_FOR_WRITE, CANCELLED),
```

#### 修复点 2：取消写卡任务时回退批次状态

`main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteJobServiceImpl.java:241-277`

在释放资产租约之后、记录日志之前，增加如下逻辑：

```java
PdcNfcBatchEntity batch = batchDao.selectById(job.getBatchId());
if (batch != null && PdcNfcBatchStatus.WRITING.name().equals(batch.getStatus())) {
    batchStateMachine.requireTransition(
            PdcNfcBatchStatus.WRITING, PdcNfcBatchStatus.READY_FOR_WRITE);
    int reverted = batchDao.transitionStatus(
            job.getBatchId(),
            PdcNfcBatchStatus.WRITING.name(),
            PdcNfcBatchStatus.READY_FOR_WRITE.name(),
            operatorId, now);
    if (reverted != 1) {
        throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
    }
}
```

使用 `batchDao.transitionStatus` 进行原子条件更新，避免并发竞态。

#### 修复点 3：前端无需改动

`NfcWriteJobManagement.vue:299-301` 的 `canCreate` 已经基于 `READY_FOR_WRITE`；只要后端正确回退状态，刷新列表后“创建任务”按钮会自动恢复。

### 6.2 替代方案（需产品确认）

如果业务上“取消写卡任务”的实际含义是“终止整个批次”，则可以在 `PdcNfcWriteJobServiceImpl.cancel` 中直接调用 `batchService.cancel(batchId, operatorId)`。该路径状态机已支持 `WRITING -> CANCELLED`，但批次将彻底结束，无法重新发起写卡任务。

---

## 7. 相关文件索引

| 文件 | 作用 |
|---|---|
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcBatchStatus.java:6-15` | 批次状态枚举 |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/constant/PdcNfcWriteJobStatus.java:6-12` | 写卡任务状态枚举 |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcBatchEntity.java:41-42` | 批次 `status` 字段 |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcWriteJobEntity.java:38-39`、`81` | 写卡任务 `status`、`cancelledAt` 字段 |
| `main/manager-api/src/main/resources/db/changelog/202607291000.sql:34`、`142`、`156` | 批次/写卡任务状态字段、取消时间字段定义 |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcWriteJobAdminController.java:85-91` | 取消写卡任务接口 |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcBatchAdminController.java:41-47` | 取消批次接口 |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteJobServiceImpl.java:82-131` | 创建写卡任务，推进批次到 `WRITING` |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteJobServiceImpl.java:241-277` | 取消写卡任务实现（未更新批次） |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcWriteResultTransactionServiceImpl.java:347-365` | 结果导入完成，推进批次到 `READY_FOR_STOCK` |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcManualWriteServiceImpl.java:217-239` | 手动写卡完成，推进批次到 `READY_FOR_STOCK` |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcBatchServiceImpl.java:172-186` | 取消批次实现 |
| `main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcBatchStateMachine.java:19-26` | 批次状态机 |
| `main/manager-web/src/utils/pdcNfcState.mjs:68-100` | 状态中文映射 |
| `main/manager-web/src/views/nfc/NfcWriteJobManagement.vue:56-92`、`299-322`、`509-526` | 写卡任务管理页展示与操作 |
