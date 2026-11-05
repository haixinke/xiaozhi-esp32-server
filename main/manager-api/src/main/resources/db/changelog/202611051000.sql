-- 一起画局次只保存游玩事实，费用与奖励按完成时配置结算，钱包事实由星星模块维护。
ALTER TABLE ai_drawing_session
    DROP COLUMN pet_id,
    DROP COLUMN earn_stars,
    DROP COLUMN consume_stars,
    DROP COLUMN reward_available,
    DROP COLUMN awarded_stars,
    DROP COLUMN balance_after;
