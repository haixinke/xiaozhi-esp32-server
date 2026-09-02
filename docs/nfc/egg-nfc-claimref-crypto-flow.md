# NFC claimRef 加密存储与领取校验流程

> 本文档详细解释 NFC 实物从工厂生产到用户碰一碰领取的完整密码学和校验链路。
> 核心代码：`ClaimRefProtection.java`、`PdcNfcSchemeJobWorker.java`、`PdcNfcClaimServiceImpl.java`

---

## 全局数据流概览

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          NFC 实物生产 → 领取完整链路                          │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ① 批次创建 → 生成资产                                                       │
│     ├─ claimRef = 16随机字节 → Base64URL → 22字符                           │
│     ├─ claim_ref_hash = HMAC-SHA-256(HMAC密钥, claimRef)  → 存入数据库      │
│     └─ claim_ref_ciphertext = AES-256-GCM(AES密钥, nonce, assetId, claimRef)│
│                                                                              │
│  ② Scheme 生成 → 调用微信 API                                                │
│     ├─ AES 解密还原 claimRef                                                 │
│     ├─ 请求微信: model_id + wechat_sn + jump(path, query="v=1&ref=claimRef")│
│     ├─ 微信返回: scheme URL (weixin://platform/nfc?...)                      │
│     └─ AES-GCM 加密 scheme URL → 存入数据库                                 │
│                                                                              │
│  ③ 写卡导出 → 交给工厂                                                       │
│     ├─ AES 解密 scheme URL                                                   │
│     └─ 导出 CSV：uri_payload = scheme URL (明文，写入 NFC 标签)              │
│                                                                              │
│  ④ 工厂写卡 → NFC 标签写入 2 条 NDEF 记录                                   │
│     ├─ URI Record: scheme URL (碰触后微信跳转)                               │
│     └─ AAR Record: com.tencent.mm (指定微信打开)                            │
│                                                                              │
│  ⑤ 用户碰一碰 NFC → 微信启动小程序                                           │
│     └─ 打开 /pages/nfc-claim/nfc-claim?v=1&ref=claimRef                     │
│                                                                              │
│  ⑥ 小程序调用领取 API → 服务端校验                                           │
│     ├─ HMAC 哈希查资产                                                       │
│     ├─ 频率限制 + 手机绑定 + 功能开关                                        │
│     ├─ 幂等性 + 乐观锁                                                     │
│     └─ 创建宠物 + 标记 CLAIMED                                              │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 阶段一：资产创建（批次创建时）

批次创建后，系统为每个 NFC 标签生成一条 `pdc_nfc_asset` 记录。

### 核心字段

| 字段 | 内容示例 | 生成方式 | 用途 |
|---|---|---|---|
| `wechat_sn` | `EB3HFMKQ7X9...`（28字符） | "EB" + 26位 Crockford Base32（17随机字节） | 微信 NFC API 序列号 |
| `claimRef` | `abcdefghij1234567890_-`（22字符） | 16 随机字节 → Base64URL 无填充 | **领取凭证（明文不存库）** |
| `claim_ref_hash` | `a1b2c3...`（64位 hex） | `HMAC-SHA-256(HMAC密钥, claimRef)` | 数据库查找索引 |
| `claim_ref_ciphertext` | 二进制 | `AES-256-GCM(AES密钥, nonce, assetId, claimRef)` | 加密存储，可解密还原 |
| `claim_ref_nonce` | 12字节随机数 | 每次加密不同 | AES-GCM nonce |
| `claim_ref_key_version` | `v1` | 当前活跃版本号 | 密钥轮换时选择正确密钥 |

### 为什么 claimRef 不存明文

- **防数据库泄露**：拖库后攻击者只拿到 HMAC 哈希，无法反推出 claimRef
- **HMAC 哈希用于查找**：用户碰触 NFC 后，小程序拿到 claimRef，服务端计算 HMAC 哈希去 `WHERE claim_ref_hash = ?` 定位资产
- **AES 密文用于还原**：Scheme 生成阶段需要 claimRef 明文调微信 API，从密文解密还原

### 代码位置

- 生成器：`PdcNfcIdentifierGenerator.newClaimRef()` — 16 随机字节 → Base64URL
- 生成器：`PdcNfcIdentifierGenerator.newWechatSn()` — "EB" + 26位 Crockford Base32
- 加密保护：`ClaimRefProtection.protect(assetId, claimRef)` — AES-GCM 加密 + HMAC 哈希

---

## 阶段二：Scheme 生成（Scheme Job Worker）

`PdcNfcSchemeJobWorker.processAsset()` 对每个 `CREATED` 状态的资产执行：

```java
// 1. 从数据库读取加密字段
EncryptedField field = new EncryptedField(
    asset.getClaimRefKeyVersion(),
    asset.getClaimRefNonce(),
    asset.getClaimRefCiphertext()
);

// 2. AES-GCM 解密还原 claimRef（assetId 作为 AAD 防跨资产重放）
String claimRef = claimRefProtection.decrypt(asset.getId(), field);

// 3. 调用微信 NFC Scheme 生成 API
//    POST https://api.weixin.qq.com/wxa/generatenfcscheme
//    请求体:
//    {
//      model_id: "微信分配的 modelId",
//      sn: wechat_sn,
//      jump_wxa: {
//        path: "/pages/nfc-claim/nfc-claim",
//        query: "v=1&ref=abcdefghij1234567890_-",
//        env_version: "release"
//      }
//    }
WechatNfcSchemeResult result = schemeClient.generate(asset.getWechatSn(), claimRef);

// 4. AES-GCM 加密 scheme URL 并存入数据库
SchemeEncryption enc = claimRefProtection.encryptScheme(asset.getId(), result.scheme());
assetDao.markSchemeGenerated(
    asset.getId(),
    enc.encrypted().keyVersion(),
    enc.encrypted().nonce(),
    enc.encrypted().ciphertext(),
    enc.sha256(),
    jobId, new Date()
);
```

### 微信做了什么

微信将 `model_id + sn` 注册到 NFC 平台。当物理 NFC 标签被碰触时：
1. 微信通过 sn 识别这是哪个标签
2. 按注册的 `jump_wxa` 配置打开小程序
3. 自动携带 `v=1&ref=claimRef` 作为页面参数

---

## 阶段三：写卡 CSV 导出（交给工厂）

`PdcNfcWriteJobServiceImpl.create()` 创建写卡任务时，对每个资产：
1. AES 解密 `scheme_ciphertext` 还原 scheme URL 明文
2. 存入 `pdc_nfc_write_job_item` 快照表

`PdcNfcWriteCsvExporter.generate()` 导出 CSV 时，每行包含：

```csv
format_version, job_no, batch_no, item_no, asset_no, wechat_sn, sku_code,
prototype, uri_tnf, uri_type, uri_payload, aar_tnf, aar_type, aar_payload
```

关键列说明：

| 列 | 值 | 含义 |
|---|---|---|
| `uri_tnf` | `01` | NDEF Well-Known URI 类型 |
| `uri_type` | `55` | URI Record 标识 |
| **`uri_payload`** | `weixin://platform/nfc?scheme_id=xxx...` | **Scheme URL 明文 → 写入 NFC 标签** |
| `aar_tnf` | `04` | Android Application Record |
| `aar_type` | `android.com:pkg` | 安卓包名类型 |
| `aar_payload` | `com.tencent.mm` | **微信包名（确保微信打开）** |

---

## 阶段四：NFC 标签物理写入

工厂使用写卡设备将 CSV 中的 NDEF 数据写入每个 NFC 标签，每个标签写入 **2 条 NDEF 记录**：

| 记录 | 类型 | 内容 | 作用 |
|---|---|---|---|
| 第 1 条 | URI Record (TNF=01, Type=0x55) | scheme URL | 碰触后微信跳转链接 |
| 第 2 条 | AAR Record (TNF=04, Type=android.com:pkg) | `com.tencent.mm` | 告诉安卓手机用微信打开 |

**NFC 标签中存储的内容：**
- ✅ scheme URL（由微信平台生成的跳转链接）
- ❌ claimRef 明文不在标签中（claimRef 只在 URL 的 query 参数中）
- ❌ 任何敏感数据（密钥、哈希等）不在标签中

---

## 阶段五：用户碰一碰 NFC 标签

```
用户手机触碰 NFC 标签
      │
      ▼
手机 NFC 读取器读到 NDEF 记录
      │
      ▼
AAR 记录指示"com.tencent.mm" → 微信启动
      │
      ▼
URI 记录包含 scheme URL → 微信 NFC 平台识别
      │
      ▼
微信查找 model_id + sn 注册信息
      │
      ▼
匹配成功 → 打开小程序：
  /pages/nfc-claim/nfc-claim?v=1&ref=abcdefghij1234567890_-
      │
      ▼
小程序从 URL query 参数中提取 claimRef（22 字符）
```

---

## 阶段六：领取校验（Claim Verification）

### Preview 预览接口

`PdcNfcClaimServiceImpl.preview(userId, claimRef)` — 检查是否可领取：

```
1. 校验 claimRef 格式（正则: [A-Za-z0-9_-]{22}）
   └─ 不合法 → 记录频率违规，返回 UNAVAILABLE

2. 检查用户是否绑定手机号
   └─ 未绑定 → 返回 UNAVAILABLE

3. 检查功能开关链：enabled → claimEnabled → releaseReady
   └─ 任一关闭 → 返回 UNAVAILABLE

4. 用户级频率限制
   └─ 超限 → 抛异常

5. HMAC 哈希查找资产：
   hash = HMAC-SHA-256(HMAC密钥, claimRef)
   → SELECT * FROM pdc_nfc_asset WHERE claim_ref_hash = ? LIMIT 1
   （密钥轮换时同时计算 ACTIVE + PREVIOUS 两个哈希）
   └─ 找不到 → 返回 UNAVAILABLE（不暴露是否存在）

6. 资产级频率限制
   └─ 超限 → 抛异常

7. 按资产状态返回：
   ACTIVE             → CLAIMABLE（显示商品名、原型，可领取）
   CLAIMED + 自己     → CLAIMED_BY_SELF（显示已绑定的宠物信息）
   CLAIMED + 别人     → CLAIMED_BY_OTHER
   其他状态           → UNAVAILABLE
```

### Confirm 确认领取接口

`PdcNfcClaimServiceImpl.confirm(userId, claimRef, requestId)` — 执行领取：

```
1-3. 同 Preview 的鉴权和功能开关检查

4. HMAC 哈希查找 + SELECT FOR UPDATE
   → 行级锁，防止并发领取同一资产
   → 必须恰好找到 1 条记录

5. 资产级频率限制

6. 幂等性检查：
   fingerprint = SHA-256(assetId + ":" + requestId)
   → 查 claim_record 表是否有相同 userId + requestId
   → 存在且 fingerprint 匹配 → 返回之前的结果（幂等重放）
   → 存在但 fingerprint 不匹配 → 抛 IDEMPOTENCY_CONFLICT

7. 已领取检查：
   CLAIMED + 自己 → 返回宠物信息
   CLAIMED + 别人 → 抛 ASSET_ALREADY_CLAIMED

8. 状态必须是 ACTIVE

9. 创建宠物（petService.createEgg，同一数据库事务）

10. 插入领取记录（pdc_nfc_claim_record 表）

11. 乐观锁更新：
    UPDATE pdc_nfc_asset SET status='CLAIMED', claimed_user_id=?,
    pet_id=?, version=version+1 WHERE id=? AND version=?
    └─ 更新 0 行 → 抛 INVALID_STATE（被并发修改）

12. 审计日志
```

---

## 安全设计总结

### 数据库存储

```
┌──────────────────────────────────────┐
│         数据库中存储                   │
├──────────────────────────────────────┤
│ claim_ref_hash     (HMAC 哈希)       │ ← 查找用，不可逆推 claimRef
│ claim_ref_ciphertext (AES-GCM 密文)  │ ← 加密存储，需要密钥才能解密
│ claim_ref_nonce    (12字节随机数)    │ ← AES-GCM 每次不同
│ claim_ref_key_version (如 "v1")      │ ← 密钥轮换标识
│ scheme_ciphertext  (AES-GCM 密文)    │ ← scheme URL 加密
│ scheme_sha256      (SHA-256 摘要)    │ ← 完整性校验
│ wechat_sn          (非敏感序列号)     │ ← 微信 API 标识
│                                      │
│ ❌ claimRef 明文     从不存储          │
│ ❌ scheme URL 明文   从不存储          │
└──────────────────────────────────────┘
```

### AAD 绑定（防跨资产重放）

AES-GCM 加密时使用 `assetId` 作为 Additional Authenticated Data：
- 密文和资产 ID 绑定
- 即使攻击者复制了一个资产的密文，也无法在另一个资产上解密成功（AAD 不匹配导致认证失败）

### 双层密钥

| 密钥 | 算法 | 用途 |
|---|---|---|
| HMAC 密钥 | HMAC-SHA-256 | 生成查找哈希 → `WHERE claim_ref_hash = ?` |
| AES 密钥 | AES-256-GCM | 加密/解密原文 → Scheme 生成时还原 claimRef |

两把密钥**必须不同**，防止密码学攻击面重叠。

### 防攻击措施

| 攻击方式 | 防御机制 |
|---|---|
| 数据库拖库 | claimRef 不存明文，HMAC 不可逆 |
| 伪造 claimRef | 必须匹配 HMAC 哈希才能找到资产 |
| 并发领取 | SELECT FOR UPDATE + 乐观锁 |
| 重放攻击 | requestId + fingerprint 幂等性 |
| 暴力枚举 | 用户级 + 资产级频率限制 |
| 跨资产重放 | AAD 绑定 assetId，密文不可跨资产使用 |
| 密钥泄露 | 密钥轮换（ACTIVE/PREVIOUS 双密钥） |
