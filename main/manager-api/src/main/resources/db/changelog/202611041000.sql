-- 一起画局次：用户级计次与开局幂等，原始画作和绘画轨迹不入库。
CREATE TABLE ai_drawing_session (
    id VARCHAR(36) NOT NULL COMMENT '服务端局次 UUID',
    user_id BIGINT NOT NULL COMMENT '归属用户',
    pet_id VARCHAR(64) NOT NULL COMMENT '开局时已破壳的宠物',
    request_id VARCHAR(64) NOT NULL COMMENT '同用户开局重试标识',
    reference_id VARCHAR(80) NOT NULL COMMENT '本局临摹素材',
    earn_stars INT NOT NULL COMMENT '开局冻结奖励数额',
    consume_stars INT NOT NULL COMMENT '开局冻结并实际扣除的费用',
    reward_available TINYINT NOT NULL COMMENT '开局时奖励可用性快照',
    started_day DATE NOT NULL COMMENT '开局计次日（北京时间）',
    started_at DATETIME(3) NOT NULL COMMENT '成功开局时间',
    completed_at DATETIME(3) NULL COMMENT '成功完成时间',
    awarded_stars BIGINT NULL COMMENT '本局实际新发奖励',
    reward_status VARCHAR(32) NULL COMMENT '完成结算结果',
    balance_after BIGINT NULL COMMENT '完成后的余额快照',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drawing_user_request (user_id, request_id),
    KEY idx_drawing_user_day (user_id, started_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一起画开局与完成记录';
