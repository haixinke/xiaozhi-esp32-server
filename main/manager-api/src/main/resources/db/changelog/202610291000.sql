-- 小纸条（每日内容）：建 ai_daily_note 表
-- 纯运营内容，无用户侧历史关联，删除为物理删除：不设 deleted 列，
-- 这是 ADR 0006 软删惯例的例外（沿用 ADR 0010 对纯运营内容的决策方式）
-- 主键走 MyBatis-Plus 全局 ASSIGN_ID（雪花），DDL 不写 AUTO_INCREMENT
-- note_date 唯一：一个完整日期只有一份小纸条，手工保存与并发写入都由数据库兜底
CREATE TABLE `ai_daily_note` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花)',
  `note_date` DATE NOT NULL COMMENT '完整日期(yyyy-MM-dd)，全局唯一，不按年循环',
  `title` VARCHAR(50) NOT NULL COMMENT '标题，去首尾空白后1-50个Unicode字符',
  `content` VARCHAR(200) NOT NULL COMMENT '正文，去首尾空白后1-200个Unicode字符，保留内部换行，按纯文本处理',
  `solar_term` VARCHAR(50) NULL COMMENT '节气（可选，任意文案，最多50个Unicode字符）',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  `updater` BIGINT NULL COMMENT '更新者',
  `update_date` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_daily_note_date` (`note_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小纸条-每日内容';
