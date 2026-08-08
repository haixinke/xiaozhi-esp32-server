-- liquibase formatted sql

-- changeset xiaozhi:202608011500
-- 新增通用操作日志表，记录用户重要/危险操作（导出、删除等）
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NULL COMMENT '操作人用户ID（系统/匿名操作可为空）',
  `username` VARCHAR(64) NULL COMMENT '操作人用户名（冗余，便于查询展示）',
  `operation_type` VARCHAR(64) NOT NULL COMMENT '操作类型，如 CHAT_HISTORY_EXPORT',
  `operation_desc` VARCHAR(255) NULL COMMENT '操作描述（人类可读）',
  `request_uri` VARCHAR(255) NULL COMMENT '请求路径（注解方式自动填充）',
  `request_method` VARCHAR(8) NULL COMMENT '请求方法 GET/POST（注解方式自动填充）',
  `ip` VARCHAR(64) NULL COMMENT '操作IP',
  `detail` TEXT NULL COMMENT '业务上下文JSON（不含敏感信息）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1成功 0失败',
  `error_msg` VARCHAR(500) NULL COMMENT '失败原因',
  `create_date` DATETIME NOT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_create` (`user_id`, `create_date`),
  KEY `idx_type_create` (`operation_type`, `create_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用操作日志表';
