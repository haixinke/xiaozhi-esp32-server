# 蛋宝宝小程序对话记录导出/删除后端逻辑调研

## 一句话结论

蛋宝宝小程序的"对话记录"页（`pages/chat-settings`）提供**导出到邮箱**和**删除全部**两个功能，两者都走 后端服务（main/manager-api）的 `/wechat/chat-history/*` 端点；作用域都是**当前登录用户的全部智能体（agent）**，即**聚合该用户名下所有宠物**的对话记录——不按单个 pet 隔离，前端不传任何 petId，后端只按 `userId` 查全部 `ai_agent` 再逐个 agent 处理。删除是**物理删除**（硬删）`ai_agent_chat_history` 文本记录 + `ai_agent_chat_title` 会话标题，**不删音频、不删长期记忆**，不可恢复。

## 前端入口

- 入口：`pages/my`（"我的"tab）→ "对话记录" 行 → `pages/chat-settings` 页面
  - `main/egg-miniprogram/miniprogram/pages/my/my.wxml:19`：`<list-row label="对话记录" bind:rowtap="onNavChatSettings">`
  - 页面注册：`main/egg-miniprogram/miniprogram/app.json:25`
- 页面 `main/egg-miniprogram/miniprogram/pages/chat-settings/chat-settings.wxml:7` 提供"导出聊天记录"区（邮箱输入框 + 按钮，`:30`），`:34-43` 提供"删除聊天记录"区（`:35` 明确提示"删除你的全部对话记录，删除后无法恢复"）。
- 导出触发：`chat-settings.js:34 onExport()` → 前端本地校验邮箱格式（`:42`）→ `request.post('/wechat/chat-history/export', { email })`（`:48`）。成功后提示"聊天记录已开始导出，稍后将发送至邮箱"（`:52-60`）——**同步返回、异步导出**。
- 删除触发：`chat-settings.js:71 onDelete()` → `wx.showModal` 二次确认（`:74-85`）→ `confirmDelete()` → `request.post('/wechat/chat-history/delete')`（`:90`），**不带任何参数**（无 petId/deviceId/agentId）。
- HTTP 封装：`main/egg-miniprogram/miniprogram/utils/request.js:33` 自动附加 `Authorization: Bearer <token>`（微信登录 token，401 静默重登）；两个端点均非匿名。

## 导出后端逻辑

链路：`POST /xiaozhi/wechat/chat-history/export` → `WechatController.exportChatHistory` → `ChatHistoryExportService.exportAndEmailAsync`。

- 端点定义：`main/manager-api/src/main/java/xiaozhi/modules/wechat/controller/WechatController.java:100-110`
  - `@RequiresPermissions("sys:role:normal")`（`:102`）+ `SecurityUser.getUserId()` 判空（`:104-107`）——登录用户方可调用，userId 取自 token 而非请求体，无法越权导出他人数据。
  - 请求体 `ChatHistoryExportReqDTO` 仅一个 `email` 字段（`@NotBlank` + `@Email` 校验）：`main/manager-api/src/main/java/xiaozhi/modules/wechat/dto/ChatHistoryExportReqDTO.java:18-21`。**请求体里没有 petId 等任何身份字段**。
- 异步导出：`main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/ChatHistoryExportServiceImpl.java:57` `@Async("taskExecutor")`——controller 立即返回 `Result<Void>`（code=0），导出与发邮件在后台线程执行，失败仅记日志（`:83-87`）。
- 数据组装 `buildExportContent(userId)`（`:95-132`）：
  1. `agentService.getUserAgents(userId, null, null)`（`:96`）取该用户**全部**智能体；`getUserAgents` 的实现就是 `WHERE user_id = ? ORDER BY created_at DESC`：`main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentServiceImpl.java:240-270`。
  2. 逐 agent 分页收集会话 ID（`collectSessionIds` `:152-170`，每页 100，SQL 为对 `ai_agent_chat_history` 按 `agent_id` 分组取 `session_id`、`MAX(created_at)` 倒序：`:192-199`）。
  3. 批量预取会话标题 `loadTitleMap`（`:175-187`，`ai_agent_chat_title WHERE session_id IN (...)`）。
  4. 逐会话分页拉消息（每页 500，`:214-239`），按 `chat_type==1` 判定用户消息（`:228`），生成 TXT 文本（`:232-233` 行格式 `[角色]-[时间]>>/<<:内容`）。
- 发送：附件 `chat-history.txt`（UTF-8）+ HTML 正文（`:64-79`、`:262-268`），经 `EmailService.sendEmail`（邮件 SMTP 配置来自系统参数）。
- 涉及表：`ai_agent_chat_history`（消息，定义见 changelog `main/manager-api/src/main/resources/db/changelog/202505022134.sql:5-20`；content 后扩为 TEXT 见 `202603311200.sql:2`）、`ai_agent_chat_title`（会话标题，定义见 `202604011545.sql:8-19`）、`ai_agent`（智能体列表）。
- 审计：导出动作写入操作日志 `operationLogService.record(OperationType.CHAT_HISTORY_EXPORT, ...)`（`:81-82`），枚举见 `main/manager-api/src/main/java/xiaozhi/modules/sys/enums/OperationType.java:11`。
- 返回格式：`Result<Void>`（信封 `{code, msg, data}`，code=0 即成功），data 为 null；邮件到达是异步副作用，前端无法感知失败。

## 删除后端逻辑

链路：`POST /xiaozhi/wechat/chat-history/delete` → `WechatController.deleteChatHistory` → `ChatHistoryDeleteService.deleteAllByUserId`。

- 端点定义：`WechatController.java:112-122`——同样 `@RequiresPermissions("sys:role:normal")` + userId 判空，**无请求体、无路径参数**。
- 删除实现：`main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/ChatHistoryDeleteServiceImpl.java:41-65`
  1. `agentService.getUserAgents(userId, null, null)` 取用户全部 agent（`:42`）。
  2. 逐 agent：先删会话标题，再删文本记录（`deleteByAgent` `:70-74`）；单个 agent 失败不中断，记日志继续（`:50-58`），最后统计成功/失败数并写操作日志（`:59-64`，`OperationType.CHAT_HISTORY_DELETE`）。
- 删标题：`deleteTitlesByAgent`（`:79-92`）——先 `SELECT DISTINCT session_id FROM ai_agent_chat_history WHERE agent_id=?` 取出会话集合，再 `DELETE FROM ai_agent_chat_title WHERE session_id IN (...)`（`:89-91`，MyBatis-Plus `delete(wrapper)`，物理删）。
- 删文本：`agentChatHistoryService.deleteByAgentId(agentId, false, true)`（`:73`）——`deleteAudio=false, deleteText=true`：`main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentChatHistoryServiceImpl.java:121-155` 跳过音频分支（`:124-150`），执行 `baseMapper.deleteHistoryByAgentId(agentId)`（`:151-153`），对应 SQL 为 `DELETE FROM ai_agent_chat_history WHERE agent_id = #{agentId}`：`main/manager-api/src/main/resources/mapper/agent/AiAgentChatHistoryDao.xml:45-48`。**硬删，无软删标记、无回收站**。
- 级联范围（明确不删的）：
  - **不删音频**：实现类注释写明"蛋宝宝音频不落 OSS，故不删音频"（`ChatHistoryDeleteServiceImpl.java:26`、`:72`），传 `deleteAudio=false`，因此 `ai_agent_chat_audio` 表记录不删除（若存在 TTS 音频 blob，删除后成为孤儿数据）。
  - **不删智能体/设备/宠物本身**：只删聊天记录与标题，不动 `ai_agent`、`ai_device`、`ai_pet`。
  - **不删长期记忆**：manager-api 的 Liquibase changelog 中没有任何 memory/summary 表，记忆由聊天服务（main/xiaozhi-server）的 Memory Provider 托管，本端点不触碰。前端提示"无法恢复"仅针对这两张表。
- 无批量上限保护：逐 agent 全量 `DELETE`，无分页/分批（文本删除未分批；对比音频路径才有每批 1000 的分批逻辑）。

## 多宠物处理

- 数据隔离维度是 **agentId（智能体）**，不是 petId。蛋宝宝模型为 `微信用户 ─1:N─ ai_pet ─1:1─ ai_device ─1:1─ ai_agent`（破壳时懒创建设备与 agent），每只宠物对应一个 agent，聊天记录挂 `agent_id` + `session_id`（表定义 `202505022134.sql:9-10`）。
- 导出/删除端点的输入只有 **userId（来自登录 token）**：`getUserAgents(userId)` 查出该用户名下**所有** agent（即所有宠物），然后**聚合处理全部**。
- 结论：**导出 = 该用户所有宠物的聊天记录合并成一个 TXT 按智能体分节导出；删除 = 删除该用户所有宠物的全部聊天记录**。不存在"按单个宠物导出/删除"的能力，前端也没有任何 pet 选择器。
- 权限安全：userId 由服务端从 token 解析（`SecurityUser.getUserId()`），请求方无法指定他人 userId 或他人 agentId，无水平越权面。

## 未确认 / 需人工确认

1. **`ai_agent_chat_audio` 孤儿数据**：蛋宝宝链路音频不落库不入 OSS，但 ESP32 设备的音频会进 `ai_agent_chat_audio`；若同一账号下混有 ESP32 设备，删除聊天记录后音频 blob 会残留（`deleteAudio=false` 是有意为之）。是否接受该行为需产品确认。
2. **邮件送达失败的用户感知**：导出失败仅记服务端日志（`ChatHistoryExportServiceImpl.java:83-87`），前端已提示"导出成功"，用户收不到邮件时无任何补偿/重试通知机制。
3. **删除与长期记忆的一致性**：删除聊天记录不清记忆，若记忆是基于历史对话生成的（如 powermem），"删除后无法恢复"的承诺与"AI 仍记得旧事"的用户预期可能冲突，需产品确认是否有意设计。
4. **导出 TXT 含全部宠物会话**：导出文件按"智能体"分节，文件名固定 `chat-history.txt`，多宠物用户无法只导出某一只宠物的记录——若产品需要按宠物隔离导出，前后端都要加 petId 维度（当前完全没有）。
