# pdc_nfc_asset.pet_id 生命周期

## 结论先行

`pdc_nfc_asset.pet_id` **只在 NFC 确认领取时写入一次，且目前没有代码路径会清空它**。

- 写入时机：用户通过小程序触碰 NFC 标签并调用 `POST /pdc/nfc/claim/confirm` 确认领取时，后端先创建宠物（`ai_pet`），再把生成的 `pet_id` 回写到资产表。
- 清空路径：不存在。停用（DISABLED）会保留 `pet_id`；报废（SCRAPPED）不允许从 `CLAIMED` 状态流转，因此不会清空已领取资产的宠物绑定。

---

## 1. 数据库定义

`pdc_nfc_asset.pet_id` 在初始化变更集里定义为可空字符串，无单独 COMMENT：

```sql
-- main/manager-api/src/main/resources/db/changelog/202607291000.sql:47-93
CREATE TABLE pdc_nfc_asset (
  ...
  claimed_user_id BIGINT NULL,
  pet_id VARCHAR(64) NULL,          -- 第 78 行
  ...
);
```

同一变更集里的 `pdc_nfc_claim_record.pet_id` 则不允许为空（`NOT NULL`），用于记录一次成功领取所创建的宠物：

```sql
-- main/manager-api/src/main/resources/db/changelog/202607291000.sql:214-228
CREATE TABLE pdc_nfc_claim_record (
  ...
  pet_id VARCHAR(64) NOT NULL,      -- 第 221 行
  ...
);
```

实体字段注释见：

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/entity/PdcNfcAssetEntity.java:124-125
/** 关联宠物 ID（领取时绑定） */
private String petId;
```

---

## 2. 唯一写入点：确认领取

### 2.1 接口入口

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/controller/PdcNfcClaimController.java:44-50
@PostMapping("/confirm")
@Operation(summary = "确认领取")
public Result<PdcNfcClaimResultVO> confirm(@Valid @RequestBody PdcNfcClaimConfirmDTO dto) {
    Long userId = SecurityUser.getUserId();
    UUID requestId = UUID.fromString(dto.getRequestId());
    return new Result<PdcNfcClaimResultVO>().ok(claimService.confirm(userId, dto.getClaimRef(), requestId));
}
```

### 2.2 业务实现

`PdcNfcClaimServiceImpl.confirm` 在事务内完成：

1. 校验 claimRef、限流、幂等、资产状态必须为 `ACTIVE`。
2. 调用 `petService.createEgg(userId, asset.getPrototype())` 创建宠物。
3. 把 `pet_id` 写入领取记录 `pdc_nfc_claim_record`。
4. 调用 `assetDao.markClaimed(...)` 把 `pdc_nfc_asset` 更新为 `CLAIMED` 并写入 `pet_id`。

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java:231-246
// 9. Create pet (same transaction)
PetVO pet = petService.createEgg(userId, asset.getPrototype());

// 10. Insert claim record
PdcNfcClaimRecordEntity record = new PdcNfcClaimRecordEntity();
record.setAssetId(asset.getId());
record.setUserId(userId);
record.setRequestId(requestId.toString());
record.setRequestFingerprint(fingerprint);
record.setPetId(pet.getId());                          // <-- 第 240 行
record.setResult("CLAIMED");
record.setCreateDate(new Date());
claimRecordDao.insert(record);

// 11. Mark asset as claimed (optimistic lock)
int changed = assetDao.markClaimed(asset.getId(), asset.getVersion(), userId, pet.getId());  // <-- 第 246 行
```

### 2.3 实际 SQL 写入

`PdcNfcAssetDao.markClaimed` 是回写 `pet_id` 的唯一 SQL 更新点：

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dao/PdcNfcAssetDao.java:74-78
@Update("UPDATE pdc_nfc_asset SET status = 'CLAIMED', claimed_user_id = #{userId}, " +
        "pet_id = #{petId}, claimed_at = NOW(), version = version + 1 " +
        "WHERE id = #{id} AND version = #{version} AND status = 'ACTIVE'")
int markClaimed(@Param("id") Long id, @Param("version") Integer version,
                @Param("userId") Long userId, @Param("petId") String petId);
```

资产初始创建时并不写入 `pet_id`。`PdcNfcAssetDao.insertBatch` 的字段列表未包含 `pet_id`：

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/dao/PdcNfcAssetDao.java:18-28
@Insert({"<script>",
        "INSERT INTO pdc_nfc_asset (id, asset_no, batch_id, item_no, sku_code, prototype, wechat_sn, ",
        "claim_ref_hash, claim_ref_hash_version, claim_ref_key_version, claim_ref_nonce, claim_ref_ciphertext, ",
        "status, version, creator, create_date) VALUES ",
        ...
        "</script>"})
```

---

## 3. 读取 `pet_id` 的场景

`pet_id` 仅在展示侧被读取，不触发再次写入：

- **领取预览（preview）**：当资产已是 `CLAIMED` 且当前用户就是领取人时，会按 `asset.petId` 查询宠物信息返回给前端。
  ```java
  // main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java:148-166
  if (PdcNfcAssetStatus.CLAIMED.name().equals(status)) {
      if (userId.equals(asset.getClaimedUserId())) {
          Object pet = null;
          if (asset.getPetId() != null) {
              PetVO petVO = petService.getById(userId, asset.getPetId());
              pet = petVO;
          }
          ...
      }
  }
  ```
- **幂等重放（replay）**：`confirm` 接口收到相同 `requestId` 的重放请求时，会从 `pdc_nfc_claim_record` 读取 `pet_id` 并返回宠物信息，不会再次更新资产表。
  ```java
  // main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcClaimServiceImpl.java:299-309
  private PdcNfcClaimResultVO replayResult(PdcNfcClaimRecordEntity record) {
      PetVO pet = null;
      if (record.getPetId() != null) {
          pet = petService.getById(record.getUserId(), record.getPetId());
      }
      return PdcNfcClaimResultVO.claimed(pet);
  }
  ```

---

## 4. 是否存在清空 `pet_id` 的路径？

**不存在代码自动清空 `pdc_nfc_asset.pet_id` 的路径。**

库存流转的批量操作（入库、激活、停用、报废）通用逻辑里只更新 `status`、对应时间戳、业务单号、操作人信息，不会修改 `pet_id`：

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcInventoryServiceImpl.java:240-248
for (PdcNfcAssetEntity asset : assets) {
    String beforeStatus = asset.getStatus();
    asset.setStatus(targetState.name());
    timestampSetter.accept(asset, now);
    businessNoSetter.accept(asset, request.getBusinessNo());
    asset.setUpdater(operatorId);
    asset.setUpdateDate(now);
    assetDao.updateById(asset);
    ...
}
```

其中停用允许从 `CLAIMED` 状态流转到 `DISABLED`，但接口注释明确说明保留关联信息：

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/PdcNfcInventoryService.java:28-33
/**
 * 批量停用：IN_STOCK / ACTIVE / CLAIMED → DISABLED
 * <p>
 * CLAIMED 状态的资产保留 claimedUserId、petId 等关联信息。
 */
PdcNfcBulkOperationVO disable(PdcNfcBulkAssetOperationDTO request, Long operatorId);
```

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcInventoryServiceImpl.java:180-184
private PdcNfcBulkOperationVO doDisable(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
    return doBulkOperation(request, operatorId, "DISABLE",
            Set.of(IN_STOCK, ACTIVE, CLAIMED), DISABLED,
            PdcNfcAssetEntity::setDisabledAt,
            (asset, bn) -> {});
}
```

报废只允许从 `CREATED / SCHEME_GENERATED / WRITTEN / VERIFIED` 流转，不允许从 `CLAIMED` 报废，因此已绑定 `pet_id` 的资产不会被报废流程触及：

```java
// main/manager-api/src/main/java/xiaozhi/modules/pdc/nfc/service/impl/PdcNfcInventoryServiceImpl.java:187-192
private PdcNfcBulkOperationVO doScrap(PdcNfcBulkAssetOperationDTO request, Long operatorId) {
    return doBulkOperation(request, operatorId, "SCRAP",
            Set.of(CREATED, SCHEME_GENERATED, WRITTEN, VERIFIED), SCRAPPED,
            PdcNfcAssetEntity::setScrappedAt,
            (asset, bn) -> {});
}
```

综上，`pdc_nfc_asset.pet_id` 一旦在领取确认时写入，除非直接操作数据库，否则不会被业务代码置空或覆盖。
