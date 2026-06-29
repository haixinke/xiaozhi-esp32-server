-- 为 silver/gold 套餐新增 chat_no_limit 权益
UPDATE ai_subscription_plan
SET features = '["long_term_memory","voice_input","chat_no_limit"]'
WHERE plan_code = 'silver';

UPDATE ai_subscription_plan
SET features = '["long_term_memory","voice_input","voice_call","superpower","social_moments","chat_no_limit"]'
WHERE plan_code = 'gold';

-- 同步更新已有生效中订阅的权益快照（让已订阅用户立即生效）
UPDATE ai_user_subscription us
INNER JOIN ai_subscription_plan sp ON us.plan_code = sp.plan_code
SET us.features_snapshot = sp.features
WHERE us.status = 1 AND us.end_at > NOW();
