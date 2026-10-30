-- 每日星星奖励记录：每日领取资格 + 实际奖励快照（小纸条/游戏统一入口）
-- 表设计为 spec 已确认方案：不存连续天数/宠物/设备/内容ID/配置行ID，
-- 小纸条连续金额由"昨天的成功奖励记录"推导（min(昨天数量+1,17)，否则11），
-- 内容覆盖/删除/同日重建不重置当天已领取资格
-- (user_id, source_type, source_key, reward_date) 唯一约束：小纸条一天一次、每种游戏各一天一次，
-- 同时支持按完整来源+昨天日期查连续；无按日期扫全用户需求，不另加日期单列索引
-- star_transaction_id 唯一约束：奖励记录与流水严格一对一；逻辑关联不建物理外键，
-- 业务事务内先分配奖励ID作为钱包refId、入账后回填流水ID
-- 主键走 MyBatis-Plus 全局 ASSIGN_ID（雪花），DDL 不写 AUTO_INCREMENT
CREATE TABLE `ai_star_daily_reward` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花)',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `source_type` VARCHAR(16) NOT NULL COMMENT '奖励来源类型: DAILY_NOTE/GAME',
  `source_key` VARCHAR(64) NOT NULL COMMENT 'DAILY_NOTE固定为DAILY_NOTE; GAME为游戏类型code',
  `reward_date` DATE NOT NULL COMMENT '北京时间领取自然日',
  `amount` BIGINT NOT NULL COMMENT '本次实际奖励数量，必须大于0',
  `star_transaction_id` BIGINT NOT NULL COMMENT '关联星星入账流水ID',
  `create_date` DATETIME NOT NULL COMMENT '领取记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_star_daily_reward_user_source_date`
    (`user_id`, `source_type`, `source_key`, `reward_date`),
  UNIQUE KEY `uk_star_daily_reward_transaction` (`star_transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日星星奖励记录';
