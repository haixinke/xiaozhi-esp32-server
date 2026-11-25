ALTER TABLE ai_invite_code
    ADD COLUMN name VARCHAR(100) NULL COMMENT '个人账号名快照或企业活动名称' AFTER code;

-- 已无归属账号的历史记录保留空名称，码值、归属和使用记录照常保留。
UPDATE ai_invite_code AS invite
    JOIN sys_user AS owner ON owner.id = invite.owner_user_id
SET invite.name = owner.username
WHERE invite.type = 1;

UPDATE ai_invite_code
SET name = CONCAT('历史企业邀请码 #', id)
WHERE type = 2;
