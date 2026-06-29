# 每日聊天配额限制

## Context

用户订阅了 silver/gold 套餐后享有 `chat_no_limit` 权益，免费(bronze)用户需限制每日最多30轮常规对话以引导付费转化。当前系统已有订阅权益检查机制 (`hasFeature`)，但缺少聊天次数限制功能。

**一轮定义**: 用户发送1条消息（文字或语音）= 1轮，AI回复不计数。

---

## Task 1: 数据库 - 新增 chat_no_limit 权益码

**新文件**: `main/manager-api/src/main/resources/db/changelog/202906291000.sql`

```sql
-- 为 silver/gold 套餐新增 chat_no_limit 权益
UPDATE ai_subscription_plan 
SET features = '["long_term_memory","voice_input","chat_no_limit"]'
WHERE plan_code = 'silver';

UPDATE ai_subscription_plan 
SET features = '["long_term_memory","voice_input","voice_call","superpower","social_moments","chat_no_limit"]'
WHERE plan_code = 'gold';

-- 同步更新已有生效中订阅的权益快照
UPDATE ai_user_subscription us
INNER JOIN ai_subscription_plan sp ON us.plan_code = sp.plan_code
SET us.features_snapshot = sp.features
WHERE us.status = 1 AND us.end_at > NOW();
```

并在 `db.changelog-master.yaml` 中引用该文件。

---

## Task 2: manager-api - Redis Key 与 DTO/VO

**修改**: `main/manager-api/src/main/java/xiaozhi/common/redis/RedisKeys.java`
- 新增 `getChatDailyCountKey(Long userId, String date)` → `"user:{userId}:chat_daily:{yyyyMMdd}"`

**新文件**: `main/manager-api/src/main/java/xiaozhi/modules/config/dto/ChatQuotaCheckDTO.java`
```java
@Data
public class ChatQuotaCheckDTO {
    @NotBlank private String macAddress;
}
```

**新文件**: `main/manager-api/src/main/java/xiaozhi/modules/config/vo/ChatQuotaResultVO.java`
```java
@Data
public class ChatQuotaResultVO {
    private Boolean allowed;
    private Integer remaining; // -1=无限
    private Integer total;
    private String message;   // 超限时的提示语
}
```

---

## Task 3: manager-api - 配额检查 Service 逻辑

**修改**: `main/manager-api/src/main/java/xiaozhi/modules/config/service/ConfigService.java`
- 新增接口方法: `ChatQuotaResultVO checkAndIncrementChatQuota(String macAddress)`

**修改**: `main/manager-api/src/main/java/xiaozhi/modules/config/service/impl/ConfigServiceImpl.java`
- 注入 `SubscriptionService`
- 实现配额检查逻辑:
  1. `macAddress` → `DeviceEntity` → `userId`
  2. `hasFeature(userId, "chat_no_limit")` → 有权益直接返回 allowed=true
  3. Redis `INCR` key, TTL 设为当天剩余秒数
  4. `count <= 30` → allowed=true; 否则 allowed=false + 提示语

---

## Task 4: manager-api - Controller 端点

**修改**: `main/manager-api/src/main/java/xiaozhi/modules/config/controller/ConfigController.java`
- 新增 `POST /config/chat-quota/check` 端点（复用现有 `server` ShiroFilter，xiaozhi-server 通过 secret 鉴权调用）

---

## Task 5: xiaozhi-server - 新增 API 调用函数

**修改**: `main/xiaozhi-server/config/manage_api_client.py`
- 新增异步函数 `check_chat_quota(mac_address: str) -> Optional[Dict]`
- 调用 `POST /config/chat-quota/check`
- 异常时返回 `{"allowed": True}` 保守放行

---

## Task 6: xiaozhi-server - chat() 拦截（WebSocket 事件，非 TTS）

**修改**: `main/xiaozhi-server/core/connection.py`

在 `chat()` 方法 `depth == 0` 分支内、`dialogue.put()` **之前**插入配额检查。

**核心设计**：超限时不走 TTS（避免打破沉浸体验），而是通过 WebSocket 发送一个新的事件类型 `quota_exceeded`，让小程序端用 UI 弹窗/浮层处理：

```python
def _check_chat_quota(self) -> bool:
    """检查配额。超限时发送 WebSocket 事件并返回 True。"""
    try:
        from config.manage_api_client import check_chat_quota
        future = asyncio.run_coroutine_threadsafe(
            check_chat_quota(self.headers.get("device-id")), self.loop
        )
        result = future.result(timeout=5)
        if result is None or result.get("allowed", True):
            return False  # 放行

        # 超限：发送 WebSocket 事件（非 TTS），由小程序端 UI 层处理
        asyncio.run_coroutine_threadsafe(
            self.websocket.send(json.dumps({
                "type": "quota_exceeded",
                "remaining": 0,
                "total": result.get("total", 30),
            })),
            self.loop,
        )
        return True  # 超限，跳过 LLM
    except Exception as e:
        self.logger.bind(tag=TAG).warning(f"配额检查异常，保守放行: {e}")
        return False
```

拦截条件：`depth == 0` + `read_config_from_api=True` + 非 `close_after_chat`

---

## Task 7: 小程序端 - 双层配额机制

小程序侧**不完全依赖** xiaozhi-server 返回，采用"主动预取 + 被动兜底"双层设计：

### 7.1 主动层：页面加载时预取配额

**修改**: `main/manager-api/src/.../subscription/controller/SubscriptionController.java`
- 新增 `GET /subscription/chat-quota` 端点（oauth2 鉴权，只读不加）
- 返回 `{allowed, remaining, total}`（remaining=-1 表示无限）

**修改**: `main/miniprogram/app.js`
- `fetchSubscription()` 中并行获取配额信息存入 `globalData.chatQuota`

**修改**: `main/miniprogram/pages/index/index.js`
- 页面 `onShow` 时从 `globalData.chatQuota` 读取并设置 `chatRemaining`
- `onTextSend()` / 语音发送前：本地 `chatRemaining--`，当 `chatRemaining <= 0` 时弹出升级弹窗，**不发送消息**
- 发送成功后递减本地计数

### 7.2 被动层：WebSocket 事件兜底

**修改**: `main/miniprogram/pages/index/index.js`
- `_handleWSMessage()` 中新增 `case 'quota_exceeded'`
- 收到该事件时显示升级引导弹窗（底部浮层/模态框），不在聊天流中插入消息

### 7.3 UI 设计方向

超限提示用 **模态弹窗/底部浮层**（非聊天气泡），保持对话沉浸感：
- 标题：「她今天有些累了」
- 说明：「每日免费对话 30 轮已用完，升级套餐可无限畅聊」
- CTA 按钮：「立即升级」→ 跳转订阅页
- 次要按钮：「明天再来」→ 关闭弹窗

**修改**: `main/miniprogram/pages/index/index.wxml` + `index.wxss`
- 新增 quota-exceeded 弹窗组件

---

## 边界情况

| 场景 | 处理 |
|------|------|
| 跨天重置 | Redis key 含日期 + TTL 自动过期 |
| 断开重连 | 每次 chat() 实时查 Redis |
| 并发 | Redis INCR 原子操作 |
| API 超时/故障 | 5s 超时 + 保守放行 |
| 用户中途升级 | hasFeature 实时查 DB |
| 设备未绑定用户 | 保守放行 |

---

## 验证方案

1. **单元测试**: ConfigServiceImpl.checkAndIncrementChatQuota - mock Redis/DB 验证 30 轮限制逻辑
2. **集成测试**: 
   - 启动 manager-api，用 curl 调用 `POST /config/chat-quota/check` 验证计数递增和超限响应
   - 修改 Redis 中的计数值为 30，验证下次调用返回 allowed=false
3. **端到端测试**: 
   - 启动完整服务链（OceanBase + manager-api + xiaozhi-server）
   - 通过小程序发送 >30 条消息，验证第 31 条时收到 `quota_exceeded` WebSocket 事件并弹出 UI 提示
   - 验证付费用户（has chat_no_limit）不受限
   - 验证小程序主动层：本地计数达零时客户端直接拦截，不发送消息
4. **跨天测试**: 手动删除 Redis key 或等待 TTL 过期，验证重置后可继续聊天
