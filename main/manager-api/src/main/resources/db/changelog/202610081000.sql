-- 星星罐（star）模块：用户级虚拟货币账户 + 流水
-- 设计决策见 ADR 0008 / 0009：单表流水（earn/consume 同表）、强制 refId 幂等；
-- 流水只承载资金变动，履约状态/履约产物/宠物原型不落在流水表（ADR 0009 推翻 0008 决策#4#5），
-- 履约语义由 refId 关联的业务对象承载，异步履约场景落地时再建对应实体
-- 账户表：一人一行；余额只能经 StarAccountDao 原子条件 UPDATE 变动（WHERE balance >= ?），杜绝负余额
CREATE TABLE `ai_star_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID(sys_user.id)',
  `balance` BIGINT NOT NULL DEFAULT 0 COMMENT '星星余额(整数,永不过期,不允许负值)',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  `updater` BIGINT NULL COMMENT '更新者',
  `update_date` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_star_account_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='星星罐账户';

-- 流水表：earn/consume 同表，amount 带正负；唯一索引 (user_id, biz_type, ref_id) 做幂等兜底
-- 流水仅记录资金变动事实；履约相关语义（状态/产物/原型）由 refId 关联的业务对象承载，不在此表
CREATE TABLE `ai_star_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID(sys_user.id)',
  `type` VARCHAR(16) NOT NULL COMMENT '类型: earn-赚取, consume-消费',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型: earn[sign_in/ad_reward/invite/activity/admin_grant], consume[exchange/lottery]',
  `ref_id` VARCHAR(64) NOT NULL COMMENT '业务单号(幂等键): 签到=日期, 兑换=订单号等，格式由调用方自定',
  `amount` BIGINT NOT NULL COMMENT '变动数量(earn为正,consume为负)',
  `balance_after` BIGINT NOT NULL COMMENT '操作后余额快照',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_star_txn_user_biz_ref` (`user_id`, `biz_type`, `ref_id`),
  KEY `idx_ai_star_txn_user_id` (`user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='星星罐流水';
