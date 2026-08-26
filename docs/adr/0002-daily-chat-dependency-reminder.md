# 0002. 每日聊天依赖提醒：轮询计数 + 纯前端执行

- 状态：已接受
- 日期：2026-08-24

## 背景

蛋宝宝小程序需在用户当日与 AI 宠物聊天超过 300 条（用户发送的消息数）时弹窗提示"长时间 AI 陪伴易产生依赖，请多参与线下户外活动"。年龄区间为 `AGE_0_14`（≤14 周岁）的用户弹窗后退出聊天页、当日禁聊；成年人可继续使用，当日不再二次提示，跨天后重新计算。

聊天消息走 WebSocket（egg-miniprogram → xiaozhi-server），不经 HTTP。后端已存在一套订阅制聊天配额基建（`ConfigServiceImpl.checkAndIncrementChatQuota`，Redis 计数 `user:{userId}:chat_daily:{yyyyMMdd}`），但被双关闭：`ConfigServiceImpl` L585 豁免了 `BOARD_WECHAT_EGG` 设备，`xiaozhi-server connection.py` L1175-1178 的调用点被注释（等订阅功能上线）。前端也未处理 `quota_exceeded` WS 消息。

## 决策

1. **计数来源**：复用 `ai_agent_chat_history`（`chat_type=1` 即用户消息，JOIN `ai_device` 按 `user_id` 聚合，跨设备合并），新增只读查询接口 `GET /agent/chat-history/daily-user-count`。不动现有被禁用的订阅配额代码，两套业务互不干扰。日界在 Java 侧按 `Asia/Shanghai` 计算后传入查询，不依赖数据库服务器时区，与孵化动作/每日心情的后端日界口径一致。

2. **触发通道**：前端定时轮询（每 60 秒，`onShow` 启动 / `onHide`/`onUnload` 停止，进入页面时立即查一次）。不做 WebSocket 服务端推送——轮询实现最轻，Python 服务端零改动，60 秒延迟符合"不用精确"的诉求。

3. **≤14 岁禁聊的执行层**：纯前端执行。接口返回 `{ todayCount, minor, chatLimited }`，`chatLimited = minor && todayCount > 300`。前端在聊天页入口与轮询中据此弹窗、退出回首页、当日不再进入；成年人分支仅前端单次提醒（本地按日期标记，跨天重置）。不在服务端硬拒 WS 消息——硬拒需改 WS 发送路径，违背轻量原则；保护留作后续订阅配额上线时统一处理。

## 后果

- 未成年人禁聊可被技术手段绕过（直接连 WS 发消息），但聊天消息仍被服务端记录、计数仍在，次日仍会被门禁拦截；作为合规提示性保护可接受。若监管要求硬阻断，需在 xiaozhi-server 发送路径加服务端年龄判断，届时与订阅配额一并实现。
- 前端"今日已弹过"标记用设备本地时区，与后端 `Asia/Shanghai` 日界在凌晨临界有极小偏差，不影响保护语义（最多多/少弹一次提示）。
- 新接口与现有订阅配额（`freeDailyChatLimit` 默认 30 条）阈值、意图不同：前者为依赖提醒（300 条，未成年禁聊），后者为订阅硬配额（30 条，超限拒发）。订阅上线时两者并行，互不覆盖。
- 阈值 300 硬编码在 `AgentChatHistoryServiceImpl.DAILY_CHAT_DEPENDENCY_THRESHOLD`，如需运营可配再迁至 `sys_params`。
