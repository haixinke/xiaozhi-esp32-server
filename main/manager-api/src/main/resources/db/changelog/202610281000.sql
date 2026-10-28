-- 听你说题库：建 ai_listen_group（题目分组）/ ai_listen_question（题目）两表 + LISTEN_CATEGORY 字典大类
-- 题库是纯内容记录，无用户侧历史关联（答题记录本期不存在），因此删除为物理删除：
-- 两张表都不设 deleted 列，这是 ADR 0006 软删惯例的例外（ADR 0010 记录决策）
-- 本 changeset 只交付配置层，不预置任何字典小类：5 个题目大类由运营在字典管理页维护

-- 题目分组表
-- 主键走 MyBatis-Plus 全局 ASSIGN_ID（雪花），因此 DDL 不写 AUTO_INCREMENT（与 202610271000 一致）
-- 组名在同一大类内唯一：题库物理删除，不存在"软删行占用唯一键"的问题，
-- 与 ai_small_object 刻意不建唯一索引的取舍相反，这里可以放心交给数据库兜底
CREATE TABLE `ai_listen_group` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花)',
  `category_code` VARCHAR(64) NOT NULL COMMENT '题目大类(字典LISTEN_CATEGORY的dict_value)',
  `name` VARCHAR(255) NOT NULL COMMENT '题目分组名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号，越小越靠前(同一大类内递增追加)',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  `updater` BIGINT NULL COMMENT '更新者',
  `update_date` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_listen_group_category_name` (`category_code`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='听你说题库-题目分组';

-- 题目表
-- 选项不单独建表：每题固定 5 项(A-E)、无独立生命周期，JSON 列足够（格式 [{"key":"A","text":"..."}]）
-- 题干在同一分组内唯一，由数据库联合唯一约束兜底
-- 不建 group_id 外键：仓库无 DB 级 FK 先例，级联删除由 Service 在同一事务内完成
CREATE TABLE `ai_listen_question` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花)',
  `group_id` BIGINT NOT NULL COMMENT '所属题目分组ID(ai_listen_group.id)',
  `text` VARCHAR(500) NOT NULL COMMENT '题干',
  `type` VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '题目类型:当前仅SINGLE(单选)',
  `options_json` JSON NOT NULL COMMENT '选项列表JSON,格式[{"key":"A","text":"..."}]',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '组内排序序号，越小越靠前(组内递增追加)',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  `updater` BIGINT NULL COMMENT '更新者',
  `update_date` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_listen_question_group_text` (`group_id`, `text`),
  KEY `idx_ai_listen_question_group_sort` (`group_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='听你说题库-题目';

-- 题目大类字典大类（id 101-107 已被占用）；5 个题目大类由运营在字典管理页新增
delete from `sys_dict_type` where `id` = 108;
INSERT INTO `sys_dict_type` (`id`, `dict_type`, `dict_name`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(108, 'LISTEN_CATEGORY', '听你说题目大类', '听你说题库运营中题目的大类，小类由运营在字典管理里维护', 0, 1, NOW(), 1, NOW());