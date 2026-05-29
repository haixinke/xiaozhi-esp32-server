-- 微信小程序用户绑定表
CREATE TABLE ai_wechat_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    openid VARCHAR(64) NOT NULL UNIQUE COMMENT '微信openid',
    user_id BIGINT NOT NULL COMMENT '关联sys_user.id',
    session_key VARCHAR(128) COMMENT '微信会话密钥',
    nickname VARCHAR(64) COMMENT '微信昵称',
    avatar_url VARCHAR(512) COMMENT '微信头像URL',
    create_date DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_openid (openid),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信小程序用户绑定表';
