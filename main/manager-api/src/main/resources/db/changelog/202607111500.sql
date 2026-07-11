-- 微信小程序用户绑定表：新增用户资料字段
ALTER TABLE ai_wechat_user
    ADD COLUMN gender VARCHAR(8) NULL COMMENT '性别: MALE/FEMALE/OTHER' AFTER phone,
    ADD COLUMN birthday DATE NULL COMMENT '生日' AFTER gender,
    ADD COLUMN city VARCHAR(32) NULL COMMENT '常驻城市' AFTER birthday,
    ADD COLUMN mbti VARCHAR(4) NULL COMMENT 'MBTI类型' AFTER city;
