-- 听你说·每日答题额度：为 ai_listen_answer 补 (user_id, create_date) 索引
-- 额度校验在 draw 与 submit 上都是热路径：按 user_id 过滤 + create_date 范围扫描 + DISTINCT group_id。
-- 现有 idx_ai_listen_answer_user_group 前缀只有 user_id，时间条件只能回表逐行过滤，
-- 该联合索引让时间范围直接命中索引。仅新增索引，不改表结构、不动已有 changeset。

ALTER TABLE `ai_listen_answer`
  ADD INDEX `idx_ai_listen_answer_user_create_date` (`user_id`, `create_date`);