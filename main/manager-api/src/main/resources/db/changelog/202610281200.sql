-- 听你说·小程序答题：建 ai_listen_answer（答题记录）表
-- 答题记录是用户历史数据，物理删除题库题目/分组后仍保留（不建外键），查询只 join 存活题目。
-- 与题库两表一致：不设 deleted 列，物理删除（ADR 0010 听你说惯例）。
-- 幂等由数据库兜底：UNIQUE KEY (user_id, question_id)，重复提交同一题不产生第二行。

-- 答题记录表
-- 主键走 MyBatis-Plus 全局 ASSIGN_ID（雪花），DDL 不写 AUTO_INCREMENT
-- group_id 为冗余列：可由 question_id join 推出，但抽题按组排除是热路径，冗余避免 join
CREATE TABLE `ai_listen_answer` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花)',
  `user_id` BIGINT NOT NULL COMMENT '答题用户(sys_user.id)',
  `group_id` BIGINT NOT NULL COMMENT '所属题目分组ID(ai_listen_group.id，冗余列)',
  `question_id` BIGINT NOT NULL COMMENT '题目ID(ai_listen_question.id)',
  `option_key` CHAR(1) NOT NULL COMMENT '所选选项(A-E)',
  `custom_text` VARCHAR(200) NULL COMMENT '答案文本：选A-D时冗余存选项文本，选E时存用户输入(可空，最长200字)',
  `creator` BIGINT NULL COMMENT '创建者',
  `create_date` DATETIME NULL COMMENT '创建时间',
  `updater` BIGINT NULL COMMENT '更新者',
  `update_date` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_listen_answer_user_question` (`user_id`, `question_id`),
  KEY `idx_ai_listen_answer_user_group` (`user_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='听你说-答题记录';
