-- 订阅套餐周期维度扩展：新增 silver/gold 的季度、年度档位，并调整月卡命名与价格
-- 命名规范：plan_name 为档位名（月/季/年共用），description 加"·周期"后缀区分
-- 价格规则：季卡 = 月促销×3×0.9（9折），年卡 = 月促销×12×0.8（8折），促销价取整到角
-- sort 规则：silver系 20-22，gold系 30-32，保证跨档比较成立

-- 1. 月卡命名与价格调整
--    silver：原价 19.9→29.9，促销 9.9→19.9（恢复折扣展示）
--    gold：改名"黄金月卡"→"灵魂共鸣"，促销 19.9→29.9
UPDATE ai_subscription_plan
SET price_fen = 2990, promo_price_fen = 1990, description = '心动互联·月'
WHERE plan_code = 'silver' AND duration_days = 30;

UPDATE ai_subscription_plan
SET plan_name = '灵魂共鸣', promo_price_fen = 2990, description = '灵魂共鸣·月'
WHERE plan_code = 'gold' AND duration_days = 30;

-- 2. 新增季度/年度档位
--    原价(price_fen) = 月促销价 × 月数（即按月购买N个月的总价，作为划线价）
--    促销价(promo_price_fen) = 原价 × 折扣，四舍五入到角
INSERT INTO ai_subscription_plan
    (plan_code, plan_name, duration_days, price_fen, promo_price_fen, features, bonus_items, description, sort)
VALUES
    ('silver', '心动互联', 90,  5970,  5370,
     '["long_term_memory","voice_input","chat_no_limit"]', NULL, '心动互联·季', 21),
    ('silver', '心动互联', 365, 23880, 19100,
     '["long_term_memory","voice_input","chat_no_limit"]', NULL, '心动互联·年', 22),
    ('gold', '灵魂共鸣', 90,  8970,  8070,
     '["long_term_memory","voice_input","voice_call","superpower","social_moments","chat_no_limit"]', NULL, '灵魂共鸣·季', 31),
    ('gold', '灵魂共鸣', 365, 35880, 28700,
     '["long_term_memory","voice_input","voice_call","superpower","social_moments","chat_no_limit"]', NULL, '灵魂共鸣·年', 32);
