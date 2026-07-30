# 蛋宝宝 NFC P0 阻断问题修复设计

## 1. 目标

基于 `main/docs/egg-nfc-feature-spec.md` 和
`docs/superpowers/plans/2026-07-29-egg-nfc-feature.md`，修复 NFC 完整性审查中确认的
P0 确定性阻断问题，使以下主流程具备可联调条件：

1. 创建生产批次并生成微信 NFC Scheme。
2. 创建、导出和导入工厂写卡任务。
3. 资产完成验证、入库和激活。
4. 用户通过 NFC 进入小程序，绑定手机号并领取蛋宝宝。

本轮只修复已确认的 P0，不顺带处理 P1 或进行无关重构。

## 2. 全局约束

- 所有修改只发生在 `feature/egg-nfc` worktree，不影响 `egg-dev`。
- Scheme URL 明文不得持久化到数据库、日志或管理端响应。
- 工厂 CSV 以 `main/docs/egg-nfc-feature-spec.md` 第 13 节为唯一契约。
- 管理端生产、库存核心接口继续要求 `superAdmin`；小程序领取接口不增加该权限。
- 所有行为修改遵循测试先行：先观察测试因缺失行为而失败，再做最小实现。
- 保留 worktree 中已有的未提交文件，不覆盖或还原用户修改。

## 3. 修复设计

### 3.1 写卡任务快照与 Scheme 安全

`pdc_nfc_write_job_item` 增加 `uri_sha256`，删除实体和服务中的持久化
`uri_payload`。任务创建时只保存不可变业务字段、NDEF 元数据和 URI 摘要。

导出 CSV 时，根据任务快照关联的资产读取 `scheme_ciphertext`、nonce 和密钥版本，
在内存中解密并立即写入受鉴权的下载响应。重复下载依赖不可变的任务快照和不可变的
资产 Scheme 密文，必须生成相同正文和摘要。

### 3.2 首次领取记录

首次成功领取插入 `pdc_nfc_claim_record` 时显式写入 `create_date`。领取记录、宠物创建
和资产 `ACTIVE → CLAIMED` 保持在同一个事务中，任何一步失败全部回滚。

### 3.3 空 model_id 快速失败

Scheme 就绪检查增加以下条件：

- `model_id` 非空白。
- 不接受 `<...>` 形式的占位值。

校验必须发生在批次状态更新、任务创建和资产租约分配之前。失败返回稳定的
`PDC_NFC_MODEL_ID_NOT_CONFIGURED`，不得创建任务或改变任何状态。

小程序领取和已生成 Scheme 的库存流转不依赖 `model_id`。

### 3.4 写卡结果原子导入

将“完整文件校验”和“事务落库”保持为两个阶段，但事务落库必须由独立 Spring Bean
的公开 `@Transactional` 方法执行，避免类内自调用导致事务失效。

事务阶段包含资产状态、写卡记录、任务状态、批次状态和审计记录。任意写入失败时，
整批回滚，不允许产生部分 `WRITTEN/VERIFIED` 资产。

### 3.5 工厂 CSV 唯一契约

写卡文件：

- `format_version=PDC_NFC_WRITE_V1`
- RFC 4180、UTF-8 BOM
- 14 列，字段顺序与 Spec 13.1 完全一致
- URI Record 使用 `0x01`、`U`
- AAR 使用 `0x04`、`android.com:pkg`、`com.tencent.mm`

结果文件：

- `format_version=PDC_NFC_RESULT_V1`
- 14 列，字段顺序与 Spec 13.2 完全一致
- 不要求工厂回传完整 Scheme URL
- 使用 `uri_sha256` 与不可变任务快照比对
- 严格校验任务行数、资产编号和 `wechat_sn`

### 3.6 发布证据与就绪门

发布证据 DTO 使用已锁定字段：

- `releaseVersion`
- `publishedAt`
- `smokeEvidence`

写入 `pdc_nfc_operation_log` 时补齐 `source`、`result`、`create_date` 等必填字段。
Scheme 就绪门同时要求：

1. `release-ready=true`。
2. 存在与当前领取页发布版本匹配的最新有效证据。

证据不存在时不得创建 Scheme 任务。

### 3.7 批次后半生命周期

批次状态按以下节点推进：

- Scheme 全部生成成功：`SCHEME_GENERATING → READY_FOR_WRITE`
- 创建有效写卡任务：`READY_FOR_WRITE → WRITING`
- 写卡结果完成导入且任务满足结束条件：`WRITING → READY_FOR_STOCK`
- 批次中所有可入库资产均已完成入库，且不存在待处理 `VERIFIED` 资产：
  `READY_FOR_STOCK → COMPLETED`

状态推进与对应业务写入处于同一事务；条件不满足时保持原状态，不做猜测性推进。

### 3.8 未绑定手机号的 NFC 领取链路

welcome 页面检测到有效 NFC intent 时，应进入
`/pages/nfc-claim/nfc-claim`，即使当前用户尚未绑定手机号。

领取页负责展示手机号授权入口。授权成功后重新加载 preview，并继续领取流程。普通非
NFC 用户的 welcome → home 行为保持不变。

## 4. 测试策略

每个修复批次执行 RED-GREEN-REFACTOR：

1. 数据库契约测试覆盖写卡快照列和领取记录必填字段。
2. 就绪服务测试覆盖空白、占位和有效 `model_id`，并验证失败时无任务副作用。
3. 写卡导入测试覆盖事务回滚和 Spec 结果文件。
4. CSV golden tests 覆盖版本、字段顺序、BOM、摘要校验和完整 URI 不回传。
5. 批次状态测试覆盖写卡、导入、入库三个推进节点。
6. 小程序测试覆盖 NFC intent + 无手机号进入领取页，以及普通 welcome 行为不变。

完成后运行后端 NFC 定向测试、管理端 NFC 测试与构建、小程序完整测试，并进行独立
代码审查。

## 5. 进度记录

实施阶段使用与本修复计划绑定的 SDD 进度台账，逐任务记录：

- `pending`
- `in_progress`
- `red_verified`
- `green_verified`
- `reviewed`
- `completed`
- `blocked`

台账存放在 git ignored 的 `.superpowers/sdd/` 工作目录，不污染产品代码提交；对用户
同步时使用相同状态名称。
