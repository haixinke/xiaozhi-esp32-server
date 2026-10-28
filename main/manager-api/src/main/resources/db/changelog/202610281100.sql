-- 听你说题库：LISTEN_CATEGORY 字典小类初始化（5 个题目大类）
-- 大类取值提炼自 听你说_题库.json 的最外层 id/name
-- 字典 id 108 由 202610281000.sql 建立；小类 id 沿用 NNN00X 惯例（参考 202610161000.sql 的 GAME_TYPE 106001-106005）
-- 幂等：delete + insert，重跑安全

delete from `sys_dict_data` where `dict_type_id` = 108;
INSERT INTO `sys_dict_data` (`id`, `dict_type_id`, `dict_label`, `dict_value`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(108001, 108, '日常习惯', 'DAILY-HABIT', '', 1, 1, NOW(), 1, NOW()),
(108002, 108, '工作与生活压力', 'WORK-PRESSURE', '', 2, 1, NOW(), 1, NOW()),
(108003, 108, '婚恋与亲密关系', 'INTIMACY', '', 3, 1, NOW(), 1, NOW()),
(108004, 108, '家庭与责任', 'FAMILY', '', 4, 1, NOW(), 1, NOW()),
(108005, 108, '趣味与生活喜好', 'LIFESTYLE', '', 5, 1, NOW(), 1, NOW());
