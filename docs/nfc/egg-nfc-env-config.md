# NFC 功能环境变量配置指南

> 配置定义位置：`application.yml` 的 `pdc.nfc` 段 + `PdcNfcProperties.java`
> 所有功能默认关闭（fail-closed 设计），需显式开启。

---

## 一、功能开关

| 环境变量 | YAML Key | 默认值 | 说明 |
|---|---|---|---|
| `PDC_NFC_ENABLED` | `pdc.nfc.enabled` | `false` | **总开关**，所有 NFC 功能的前置条件 |
| `PDC_NFC_RELEASE_READY` | `pdc.nfc.release-ready` | `false` | 发布就绪标志，Scheme 生成和领取都需要 |
| `PDC_NFC_SCHEME_GENERATION_ENABLED` | `pdc.nfc.scheme-generation-enabled` | `false` | Scheme 任务生成开关 |
| `PDC_NFC_ACTIVATION_ENABLED` | `pdc.nfc.activation-enabled` | `false` | 资产激活（扫码激活）开关 |
| `PDC_NFC_CLAIM_ENABLED` | `pdc.nfc.claim-enabled` | `false` | 小程序领取开关 |

## 二、商品类型与批次

| 环境变量 | YAML Key | 默认值 | 说明 |
|---|---|---|---|
| `PDC_NFC_MODEL_ID` | `pdc.nfc.model-id` | 空 | 微信 NFC 审核配置的 modelId，Scheme 生成必须 |
| `PDC_NFC_MAX_BATCH_QUANTITY` | `pdc.nfc.max-batch-quantity` | `10000` | 单批次最大计划数量 |

## 三、密码学密钥（领取功能必须）

| 环境变量 | YAML Key | 默认值 | 说明 |
|---|---|---|---|
| `PDC_NFC_CLAIM_ACTIVE_VERSION` | `pdc.nfc.claim-ref.active-version` | 空 | 当前活跃密钥版本号（如 `v1`） |
| `PDC_NFC_ACTIVE_HMAC_KEY_BASE64` | `pdc.nfc.claim-ref.active-hmac-key-base64` | 空 | 活跃 HMAC-SHA-256 密钥（Base64 编码） |
| `PDC_NFC_ACTIVE_AES_KEY_BASE64` | `pdc.nfc.claim-ref.active-aes-key-base64` | 空 | 活跃 AES-256-GCM 密钥（Base64 编码，必须 32 字节） |
| `PDC_NFC_CLAIM_PREVIOUS_VERSION` | `pdc.nfc.claim-ref.previous-version` | 空 | 密钥轮换时的旧版本号 |
| `PDC_NFC_PREVIOUS_HMAC_KEY_BASE64` | `pdc.nfc.claim-ref.previous-hmac-key-base64` | 空 | 旧 HMAC 密钥（密钥轮换期间仍可查找） |
| `PDC_NFC_PREVIOUS_AES_KEY_BASE64` | `pdc.nfc.claim-ref.previous-aes-key-base64` | 空 | 旧 AES 密钥（密钥轮换期间仍可解密） |

**密钥安全约束**（`ClaimRefProtection.validateCryptoConfig()`）：
- AES 密钥必须是 **32 字节**（256 位）
- HMAC 密钥不能为空
- **两个密钥不能相同**（HMAC 和 AES 必须使用不同的密钥）

**快速生成密钥：**

```bash
# 生成 32 字节 AES 密钥（Base64）
openssl rand -base64 32

# 生成 32 字节 HMAC 密钥（Base64）
openssl rand -base64 32
```

## 四、微信小程序凭证（Scheme 生成 + 领取依赖）

| 环境变量 | YAML Key | 默认值 | 说明 |
|---|---|---|---|
| `EGG_MINIPROGRAM_APPID` | `eggbaby.miniprogram.appid` | 空 | 微信小程序 AppID，获取 access_token |
| `EGG_MINIPROGRAM_SECRET` | `eggbaby.miniprogram.secret` | 空 | 微信小程序 AppSecret |

---

## 各功能所需的最小组合

### 生产管理（批次/写卡/资产/日志查看）

```bash
PDC_NFC_ENABLED=true
```

### Scheme 任务生成

```bash
PDC_NFC_ENABLED=true
PDC_NFC_SCHEME_GENERATION_ENABLED=true
PDC_NFC_RELEASE_READY=true
PDC_NFC_MODEL_ID=<微信审核分配的 modelId>
EGG_MINIPROGRAM_APPID=<小程序 appid>
EGG_MINIPROGRAM_SECRET=<小程序 secret>
```

### 资产激活（扫码入库激活）

```bash
PDC_NFC_ENABLED=true
PDC_NFC_ACTIVATION_ENABLED=true
PDC_NFC_RELEASE_READY=true
```

### 小程序领取（NFC 碰一碰完整流程）

```bash
PDC_NFC_ENABLED=true
PDC_NFC_CLAIM_ENABLED=true
PDC_NFC_RELEASE_READY=true
PDC_NFC_MODEL_ID=<modelId>
PDC_NFC_CLAIM_ACTIVE_VERSION=v1
PDC_NFC_ACTIVE_HMAC_KEY_BASE64=<32字节Base64>
PDC_NFC_ACTIVE_AES_KEY_BASE64=<32字节Base64>
EGG_MINIPROGRAM_APPID=<appid>
EGG_MINIPROGRAM_SECRET=<secret>
```

---

## 密钥轮换

当需要更换密钥时（如密钥泄露风险）：

1. 将当前的 `ACTIVE_*` 密钥移入 `PREVIOUS_*`
2. 生成新的 `ACTIVE_*` 密钥
3. 递增 `ACTIVE_VERSION`（如 `v1` → `v2`）
4. 保留 `PREVIOUS_VERSION` 为旧版本号

**轮换期间行为：**
- 新的 claimRef 加密使用 `ACTIVE` 密钥
- 查找资产时同时计算 `ACTIVE` 和 `PREVIOUS` 两个 HMAC 哈希
- 解密时根据密文中的 `keyVersion` 自动选择 `ACTIVE` 或 `PREVIOUS` AES 密钥

确认所有旧密文资产都已重新生成后，可清除 `PREVIOUS_*` 配置。
