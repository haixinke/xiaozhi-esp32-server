-- 完美女友：订阅 / 道具 / 支付 相关表
-- 1. 订阅套餐档位
CREATE TABLE IF NOT EXISTS ai_subscription_plan (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    plan_code       VARCHAR(32)  NOT NULL COMMENT '档位编码: bronze/silver/gold',
    plan_name       VARCHAR(64)  NOT NULL COMMENT '档位名称',
    duration_days   INT          NOT NULL COMMENT '周期天数',
    price_fen       BIGINT       NOT NULL COMMENT '原价(分)',
    promo_price_fen BIGINT       NULL     COMMENT '促销价(分)',
    features        TEXT         NOT NULL COMMENT '权益JSON数组: ["long_term_memory","voice_input","superpower","social_moments"]',
    bonus_items     TEXT         NULL     COMMENT '附赠道具JSON: [{"skuCode":"rose","count":10}]',
    description     VARCHAR(500) NULL     COMMENT '描述',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    sort            INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_duration (plan_code, duration_days)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅套餐档位';

-- 2. 用户订阅
CREATE TABLE IF NOT EXISTS ai_user_subscription (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    plan_id           BIGINT       NOT NULL COMMENT '档位ID',
    plan_code         VARCHAR(32)  NOT NULL COMMENT '档位冗余',
    order_id          BIGINT       NOT NULL COMMENT '订单ID',
    features_snapshot TEXT         NOT NULL COMMENT '权益JSON快照',
    start_at          DATETIME     NOT NULL COMMENT '生效时间',
    end_at            DATETIME     NOT NULL COMMENT '到期时间',
    status            TINYINT      NOT NULL COMMENT '0未生效 1生效中 2已过期 3已退款',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order (order_id),
    KEY idx_user_status (user_id, status, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅记录';

-- 3. 道具SKU
CREATE TABLE IF NOT EXISTS ai_item_sku (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    sku_code        VARCHAR(64)  NOT NULL COMMENT '道具编码',
    sku_name        VARCHAR(64)  NOT NULL COMMENT '道具名称',
    category        VARCHAR(32)  NOT NULL COMMENT '类型: consumable_change/outfit/voice_quota/intimacy',
    price_fen       BIGINT       NOT NULL COMMENT '原价(分)',
    promo_price_fen BIGINT       NULL     COMMENT '促销价(分)',
    attributes      TEXT         NULL     COMMENT '道具属性JSON',
    icon_url        VARCHAR(256) NULL     COMMENT '图标',
    description     VARCHAR(500) NULL     COMMENT '描述',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    sort            INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='道具SKU';

-- 4. 用户道具库存
CREATE TABLE IF NOT EXISTS ai_user_item (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id      BIGINT      NOT NULL COMMENT '用户ID',
    sku_code     VARCHAR(64) NOT NULL COMMENT '道具编码',
    total_count  INT         NOT NULL DEFAULT 0 COMMENT '累计获得',
    used_count   INT         NOT NULL DEFAULT 0 COMMENT '累计消耗',
    remain_count INT         NOT NULL DEFAULT 0 COMMENT '剩余',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME    NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_sku (user_id, sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户道具库存';

-- 5. 道具发放流水
CREATE TABLE IF NOT EXISTS ai_item_grant_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id    BIGINT       NOT NULL COMMENT '用户ID',
    sku_code   VARCHAR(64)  NOT NULL COMMENT '道具编码',
    count      INT          NOT NULL COMMENT '发放数量',
    source     VARCHAR(32)  NOT NULL COMMENT '来源: purchase/subscription_bonus/admin_grant',
    source_ref VARCHAR(64)  NULL     COMMENT '关联订单号或运营记录',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_ref_sku (source_ref, sku_code),
    KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='道具发放流水';

-- 6. 道具消耗流水
CREATE TABLE IF NOT EXISTS ai_item_consume_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id    BIGINT       NOT NULL COMMENT '用户ID',
    sku_code   VARCHAR(64)  NOT NULL COMMENT '道具编码',
    count      INT          NOT NULL COMMENT '消耗数量',
    biz_type   VARCHAR(32)  NOT NULL COMMENT '业务类型',
    biz_ref_id VARCHAR(64)  NULL     COMMENT '业务ID',
    remark     VARCHAR(200) NULL     COMMENT '备注',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id, created_at),
    KEY idx_biz (biz_type, biz_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='道具消耗流水';

-- 7. 统一支付订单
CREATE TABLE IF NOT EXISTS ai_payment_order (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    out_trade_no      VARCHAR(40)  NOT NULL COMMENT '商户订单号',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    product_type      VARCHAR(16)  NOT NULL COMMENT 'SUBSCRIPTION/ITEM',
    product_ref_id    BIGINT       NOT NULL COMMENT 'plan_id 或 sku_id',
    product_snapshot  TEXT         NOT NULL COMMENT '产品快照JSON',
    quantity          INT          NOT NULL DEFAULT 1 COMMENT '数量',
    amount_fen        BIGINT       NOT NULL COMMENT '金额(分)',
    pay_channel       VARCHAR(16)  NOT NULL DEFAULT 'WECHAT_JSAPI' COMMENT '支付渠道',
    status            TINYINT      NOT NULL COMMENT '0待支付 1已支付 2已发货 3已取消 4已退款 5已超时',
    prepay_id         VARCHAR(64)  NULL     COMMENT '微信预支付ID',
    transaction_id    VARCHAR(40)  NULL     COMMENT '微信支付订单号',
    paid_at           DATETIME     NULL     COMMENT '支付时间',
    fulfilled_at      DATETIME     NULL     COMMENT '发货时间',
    expire_at         DATETIME     NOT NULL COMMENT '订单超时时间',
    refund_amount_fen BIGINT       NOT NULL DEFAULT 0 COMMENT '已退款金额(分)',
    client_ip         VARCHAR(64)  NULL     COMMENT '下单IP',
    fail_reason       VARCHAR(500) NULL     COMMENT '失败原因',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_user_status (user_id, status, created_at),
    KEY idx_transaction (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一支付订单';

-- 8. 退款记录
CREATE TABLE IF NOT EXISTS ai_payment_refund (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    out_refund_no VARCHAR(40)  NOT NULL COMMENT '商户退款单号',
    order_id      BIGINT       NOT NULL COMMENT '订单ID',
    refund_fen    BIGINT       NOT NULL COMMENT '退款金额(分)',
    reason        VARCHAR(200) NULL     COMMENT '原因',
    status        TINYINT      NOT NULL COMMENT '0处理中 1成功 2失败',
    refund_id     VARCHAR(64)  NULL     COMMENT '微信退款单号',
    refunded_at   DATETIME     NULL     COMMENT '退款完成时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_refund_no (out_refund_no),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款记录';

-- 9. 支付回调审计日志
CREATE TABLE IF NOT EXISTS ai_payment_callback_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '渠道',
    out_trade_no    VARCHAR(40)  NULL     COMMENT '商户订单号',
    transaction_id  VARCHAR(40)  NULL     COMMENT '微信订单号',
    raw_headers     TEXT         NULL     COMMENT '请求头',
    raw_body        TEXT         NOT NULL COMMENT '请求体',
    signature_valid TINYINT      NOT NULL COMMENT '签名是否有效',
    process_result  VARCHAR(32)  NOT NULL COMMENT 'SUCCESS/DUPLICATE/SIGN_FAIL/PROCESS_FAIL',
    remark          VARCHAR(500) NULL     COMMENT '备注',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trans (transaction_id),
    KEY idx_out_trade (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调原始日志';

-- 种子数据：3 档订阅
INSERT INTO ai_subscription_plan (plan_code, plan_name, duration_days, price_fen, promo_price_fen, features, bonus_items, description, sort)
VALUES
('silver', '心动互联', 30, 1990, 990,
 '["long_term_memory","voice_input"]',
 NULL,
 '灵魂共鸣', 20),
('gold', '黄金月卡', 30, 3990, 1990,
 '["long_term_memory","voice_input","voice_call","superpower","social_moments"]',
 NULL,
 '全部权益：长久记忆 + 语音输入 + 超能力 + 朋友圈；', 30);

-- 种子数据：道具 SKU
INSERT INTO ai_item_sku (sku_code, sku_name, category, price_fen, attributes, description, sort)
VALUES
('occupation_change', '身份变更', 'consumable_change', 29900, NULL, '变更女友职业身份', 10),
('soul_quirk_change', '灵魂变更', 'consumable_change', 9900, NULL, '变更女友的灵魂特质，包括她的小任性', 11),
('voice_clone_quota', '声音克隆额度', 'voice_quota', 29900, NULL, '可进行一次声音克隆', 12),
('outfit_office', 'OL职场套装', 'outfit', 800, '{"outfitImage":""}', '职场OL换装', 21),
('rose', '玫瑰花', 'intimacy', 200, '{"intimacyDelta":5}', '亲密度+5', 30),
('milktea', '奶茶', 'intimacy', 500, '{"intimacyDelta":10}', '亲密度+10', 31),
('diamond_ring', '挚爱钻戒', 'intimacy', 9900, '{"intimacyDelta":100}', '亲密度+100', 32);
