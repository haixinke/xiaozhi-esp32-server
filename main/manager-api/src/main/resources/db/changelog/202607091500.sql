-- 邀请码主表（一行一码）
CREATE TABLE IF NOT EXISTS ai_invite_code (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    code          VARCHAR(32)  NOT NULL COMMENT '邀请码字符串',
    type          TINYINT      NOT NULL COMMENT '1=个人邀请码 2=企业邀请码',
    owner_user_id BIGINT       NULL     COMMENT '个人码=归属用户id;企业码=NULL',
    quota         INT          NOT NULL COMMENT '总配额数量',
    used_count    INT          NOT NULL DEFAULT 0 COMMENT '已使用数量',
    remaining     INT          NOT NULL COMMENT '剩余数量',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0=失效 1=有效',
    expire_time   DATETIME     NULL     COMMENT '过期时间,NULL=不过期',
    remark        VARCHAR(255) NULL     COMMENT '备注',
    creator       BIGINT       NULL     COMMENT '创建人',
    create_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater       BIGINT       NULL     COMMENT '更新人',
    update_date   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    UNIQUE KEY uk_owner_type (owner_user_id, type),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码主表';

-- 邀请码使用记录表
CREATE TABLE IF NOT EXISTS ai_invite_usage (
    id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT 'ID',
    code_id         BIGINT   NOT NULL COMMENT '关联ai_invite_code.id',
    invitee_user_id BIGINT   NOT NULL COMMENT '被邀请人user_id',
    create_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消耗时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_invitee (code_id, invitee_user_id),
    KEY idx_invitee (invitee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码使用记录表';
