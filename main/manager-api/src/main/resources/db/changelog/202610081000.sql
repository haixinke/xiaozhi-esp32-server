-- 星星罐（star）模块：用户级虚拟货币账户 + 流水
-- 设计决策见 ADR 0008：单表流水（earn/consume 同表）、强制 refId 幂等、履约状态承载于流水、旅行预订挂原型
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
-- fulfill_status: 即时场景(exchange/lottery)写入即 fulfilled；travel 预订写入 pending，故事引擎履约后回填 fulfill_ref_id
CREATE TABLE `ai_star_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID(sys_user.id)',
  `type` VARCHAR(16) NOT NULL COMMENT '类型: earn-赚取, consume-消费',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型: earn[sign_in/ad_reward/invite/activity/admin_grant], consume[exchange/lottery/travel]',
  `ref_id` VARCHAR(64) NOT NULL COMMENT '业务单号(幂等键): 签到=日期, 兑换=订单号, 旅行预订=tv-前缀单号',
  `amount` BIGINT NOT NULL COMMENT '变动数量(earn为正,consume为负)',
  `balance_after` BIGINT NOT NULL COMMENT '操作后余额快照',
  `pet_prototype` VARCHAR(32) NULL COMMENT '宠物原型(仅 travel 消费必填): KOI/RABBIT',
  `fulfill_status` VARCHAR(16) NOT NULL DEFAULT 'fulfilled' COMMENT '履约状态: pending-待履约(仅travel), fulfilled-已履约',
  `fulfill_ref_id` VARCHAR(64) NULL COMMENT '履约产物ID(如旅行日记ID),履约时回填',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_star_txn_user_biz_ref` (`user_id`, `biz_type`, `ref_id`),
  KEY `idx_ai_star_txn_user_id` (`user_id`, `id`),
  KEY `idx_ai_star_txn_pending_travel` (`user_id`, `pet_prototype`, `fulfill_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='星星罐流水';
