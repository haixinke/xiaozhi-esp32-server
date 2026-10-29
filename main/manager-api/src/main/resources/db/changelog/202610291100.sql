-- 小纸条-用户已打开记录：建 ai_daily_note_opened 表
-- 已打开状态按用户+完整日期隔离：换设备生效（只存用户ID，与设备无关）、次日不继承（完整日期）
-- 主键走 MyBatis-Plus 全局 ASSIGN_ID（雪花），DDL 不写 AUTO_INCREMENT
-- 不保存 note_id、不建外键：小纸条内容覆盖/物理删除/同日重建都不影响已打开记录（永久保留，本期不清理）
-- 联合唯一约束 (user_id, note_date) 兜底并发重复上报；不建 note_date 单列索引（无按日期查全用户需求）
CREATE TABLE `ai_daily_note_opened` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花)',
  `user_id` BIGINT NOT NULL COMMENT '用户ID(后台用户与微信用户主键均为BIGINT)',
  `note_date` DATE NOT NULL COMMENT '小纸条完整日期(yyyy-MM-dd)，不跨年复用',
  `opened_at` DATETIME NOT NULL COMMENT '首次打开时间，重复上报不更新',
  `create_date` DATETIME NULL COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_daily_note_opened_user_date` (`user_id`, `note_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小纸条-用户已打开记录';
