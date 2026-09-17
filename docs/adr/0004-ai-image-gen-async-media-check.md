# AI 生图采用完整异步内容审核链（mediaCheckAsync 双次 + 消息推送回调）

AI 生图功能中，用户照片和 AI 生成结果图各过一次微信 `security.mediaCheckAsync`，审核结果经 mp 消息推送回调（30 分钟内）驱动任务状态机（PENDING → RUNNING → REVIEWING → SUCCEEDED/FAILED），manager-api 为此新增 `/wechat/mp/callback` 回调端点。选择此链路而非"方舟护栏硬拦截 + 先展示后补审"的简化方案，是因为微信运营规范 5.18 对 UGC 场景的内容安全检测是硬性要求，"先展示后审"存在下架风险；代价是出图到可见之间增加秒级到分钟级延迟，结果页需常驻"审核中"态，且任务表需维护两个审核阶段（`photo_trace_id` / `result_trace_id`）与 24h 未终态兜底清理。

**Considered Options**

- 简化链：依赖方舟内置安全护栏在生成期硬拦截，结果图展示前不调审核或事后补审。上线快、无回调端点，但违反平台规范的 UGC 审核要求，被否。
- 同步 `security.imgSecCheck`（1.0）：官方 2021-09-01 起停止维护，且限制 ≤750×1334px/≤1M，生成图（2K 尺寸）超限，不可用。

**Consequences**

- mp 后台需配置「消息推送」（URL/Token/EncodingAESKey，安全模式 AES），回调端点必须幂等且 5s 内 ack。
- 审核驳回的 FAILED 不计入每日配额（与"成功才计次"一致）。
- 若未来接入其他 AI 生成功能，回调端点与 trace_id 路由可直接复用。
