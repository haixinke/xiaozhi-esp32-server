-- 微信小程序用户绑定表：新增用户授权手机号字段
ALTER TABLE ai_wechat_user
    ADD COLUMN phone VARCHAR(20) NULL COMMENT '用户授权手机号' AFTER avatar_url,
    ADD INDEX idx_phone (phone);
