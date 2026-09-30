# 0005. NFC 工厂 CSV 免检放行通道：抽检 + 锁卡人工声明替代结果文件证据

- 状态：已接受
- 日期：2026-09-30

## 背景

工厂 CSV 通道（ADR 0003 之前已存在）的硬校验依赖代工厂回传结果 CSV：`uri_sha256` 匹配快照、`is_read_only=true`、`ndef_record_count=2`（`PdcNfcWriteResultImporterImpl`）。实际量产对接中代工厂可能只有写卡设备、无回读回传能力——只写卡、把卡寄回。此时资产永远停在 `SCHEME_GENERATED`：状态机无旁路，且 EXPORTED 任务会挡住同批次新建任何任务（10511），无法入库激活。

同时量产启用后发现前端遗漏：智控台两处创建写卡任务弹窗（写卡任务页、批次页）的 FACTORY_CSV 单选均被写死禁用，后端完整能力走不通。

## 决策

1. **双轨制，硬校验通道一行不动**。工厂能回传结果 CSV 时走现有导入硬校验（默认引导路径，UI 主按钮）；不能回传时走免检放行（次级入口，危险色）。

2. **免检放行以"抽检 + 锁卡人工声明"替代文件证据**：操作员收货后用手机触碰抽检若干张卡（复用 preview 链路，证明 URI 写对、卡可读），在智控台勾选"确认代工厂已完成锁卡"声明后，调 `POST /pdc/nfc/write/{jobId}/trust-release` 将 EXPORTED 任务内全部 SCHEME_GENERATED 资产批量推进 VERIFIED（`verify_source=FACTORY_TRUST` 新枚举值），任务置 COMPLETED，批次走现有 WRITING → READY_FOR_STOCK 推进。全有或全无：任一资产不满足前置整体回滚。与导入互斥：已 RESULT_IMPORTED 的任务拒绝免检。幂等复用 `PdcNfcAdminIdempotencyService`（新操作类型 TRUST_RELEASE）。声明与操作人/批次/资产数落审计日志。

3. **入库门禁按 verify_source 区分证据形式**：TOUCH/MANUAL（手动模式）强制 lockedAt + lockVerifiedAt 不变；FACTORY_TRUST 与硬校验导入的工厂资产（verify_source 为空）一样不强制——前者的锁卡证据是 CSV 文件，免检的证据是人工声明。

4. **批次级两个独立操作入口**：批次页"批量入库"（圈选 VERIFIED）与"批量激活"（圈选 IN_STOCK）分开。入库是物流收货事实，激活是商业放行决策，时间点可不同，`stockedAt`/`activatedAt` 各自独立。前端分页拉全量 ID 后分批（≤500）调现有批量接口，各批独立幂等 requestId，失败中止并提示已成功批数。

5. **preview 对 IN_STOCK 返回新状态 NOT_ACTIVATED**：量产收货与放行之间卡会被真实用户碰到，给"卡片尚未激活"专属提示替代通用 UNAVAILABLE。

## 否决的替代方案

- **去掉导入硬校验**：`is_read_only=true` 与 `uri_sha256` 匹配是防工厂写错/不锁卡的唯一代码级防线，拆掉后写错 URI 只能等用户投诉发现，不锁卡的钓鱼改写风险无拦截。不接受。
- **完全信任直通（无抽检无声明）**：坏卡批量流入用户手中，无任何拦截层。不接受。
- **系统强制最低抽检张数**：小批次（几十张）比例无意义，改为不强制张数但强制锁卡声明确认 + 审计。
- **合并"收货并激活"为一个按钮**：入库与激活是不同时间点的业务事件，合并会让时间戳与审计失去区分度。

## 后果

- **免检通道不提供锁卡证据**：锁卡保障靠操作员人工声明 + 供应商管理，代码层无法验证工厂真的锁了卡。抽检只能证明 URI 正确、卡可读，证明不了只读。该风险已被产品接受；最终兜底是 confirm 强制 ACTIVE——即使明文 CSV 泄露或卡未锁，未激活资产无法领取（10506）。
- `pdc_nfc_write_job` 状态机新增 EXPORTED → COMPLETED 转换（免检收尾），与手动模式 CREATED → COMPLETED 并存。
- 抽检触碰对工厂任务资产不推进状态（`touchVerify` 只对手动任务 WRITTEN 资产生效），保持"野生触碰不动状态"语义。
- 硬校验导入通道回归优先：工厂设备能力升级能回传结果 CSV 时，UI 默认引导回路不变，免检入口自然闲置。
