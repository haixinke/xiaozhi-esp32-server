-- 订阅权益调整：
-- 1. gold 套餐新增 memory_enhance（记忆增强）、message_speed（消息回复速度提升）、
--    message_delete（历史消息撤回）三个标记权益
-- 2. 暂时移除 social_moments（看女友的私密空间）权益，后期功能上线再加回
-- 3. 重排 silver/gold features 数组顺序，统一全局展示顺序为：
--    long_term_memory, chat_no_limit, voice_input, superpower, voice_call,
--    memory_enhance, message_speed, message_delete
-- 说明：展示顺序由 gold 数组顺序决定（小程序 buildFeatureTable 以 gold 数组为基准），
--       silver 为 gold 的子集，仅重排不增删。

UPDATE ai_subscription_plan
SET features = '["long_term_memory","chat_no_limit","voice_input"]'
WHERE plan_code = 'silver';

UPDATE ai_subscription_plan
SET features = '["long_term_memory","chat_no_limit","voice_input","superpower","voice_call","memory_enhance","message_speed","message_delete"]'
WHERE plan_code = 'gold';

-- 同步刷新生效中订阅的权益快照，让已订阅用户立即生效
UPDATE ai_user_subscription us
INNER JOIN ai_subscription_plan sp ON us.plan_code = sp.plan_code
SET us.features_snapshot = sp.features
WHERE us.status = 1 AND us.end_at > NOW();
