-- AI生图任务表：用户照片 + IP参考图 + 预置文案 → Seedream 合成图
-- 状态机：PENDING(照片审核中) → RUNNING(生成中) → REVIEWING(结果图审核中) → SUCCEEDED/FAILED
CREATE TABLE `ai_image_task` (
  `id` BIGINT NOT NULL COMMENT '任务ID(雪花)',
  `user_id` BIGINT NOT NULL COMMENT '归属用户ID',
  `pet_id` VARCHAR(64) NOT NULL COMMENT '宠物ID',
  `photo_url` VARCHAR(512) NOT NULL COMMENT '用户照片OSS URL',
  `ip_image_url` VARCHAR(512) NOT NULL COMMENT 'IP参考图URL快照',
  `caption` VARCHAR(64) NOT NULL COMMENT '抽中的预置文案快照',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING照片审核/RUNNING生成中/REVIEWING结果审核/SUCCEEDED成功/FAILED失败',
  `result_url` VARCHAR(512) DEFAULT NULL COMMENT '结果图OSS URL',
  `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '用户可读失败原因',
  `photo_trace_id` VARCHAR(128) DEFAULT NULL COMMENT '照片mediaCheckAsync的trace_id',
  `result_trace_id` VARCHAR(128) DEFAULT NULL COMMENT '结果图mediaCheckAsync的trace_id',
  `counted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否计入每日配额: 0否 1是(成功才计次)',
  `creator` BIGINT DEFAULT NULL COMMENT '创建者',
  `create_date` DATETIME DEFAULT NULL COMMENT '创建时间',
  `updater` BIGINT DEFAULT NULL COMMENT '更新者',
  `update_date` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_create` (`user_id`, `create_date`),
  KEY `idx_photo_trace` (`photo_trace_id`),
  KEY `idx_result_trace` (`result_trace_id`),
  KEY `idx_status_create` (`status`, `create_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI生图任务表';
