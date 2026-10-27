-- 小物件管理：建 ai_small_object 表 + SMALL_OBJECT_TYPE 字典大类
-- 小物件是纯内容记录（一张图 + 一段故事），不保存名称、无归属与库存语义，
-- 与 item 模块的「道具」是两套概念（见根 GLOSSARY）
-- 本 changeset 只交付配置层，不预置任何字典小类：小类由运营在字典管理页维护

-- 小物件表
-- 主键走 MyBatis-Plus 全局 ASSIGN_ID（雪花），因此 DDL 不写 AUTO_INCREMENT（与 ai_game 的刻意差异）
-- 不建唯一索引：删除是软删（deleted 标记），(object_type) 唯一索引会阻止"删除后为同一类型重建"；
-- 唯一性由 Service 层按 deleted = 0 的 count 校验承担，理由见 ADR 0006
CREATE TABLE `ai_small_object` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花)',
  `object_type` VARCHAR(64) NOT NULL COMMENT '物件类型(字典SMALL_OBJECT_TYPE的dict_value)',
  `image_url` VARCHAR(512) NOT NULL COMMENT '物件图片完整OSS URL',
  `story` VARCHAR(2000) NOT NULL COMMENT '物件背后的故事(纯文本)',
  `status` INT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记:0未删除 1已删除',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  `updater` BIGINT NULL COMMENT '更新者',
  `update_date` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_small_object_type` (`object_type`),
  KEY `idx_ai_small_object_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小物件';

-- 物件类型字典大类（id 101-106 已被占用）；小类不预置，由运营在字典管理页新增
delete from `sys_dict_type` where `id` = 107;
INSERT INTO `sys_dict_type` (`id`, `dict_type`, `dict_name`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(107, 'SMALL_OBJECT_TYPE', '小物件类型', '小物件管理中物件的类型，小类由运营在字典管理里维护', 0, 1, NOW(), 1, NOW());