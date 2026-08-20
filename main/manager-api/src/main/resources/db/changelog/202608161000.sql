-- liquibase formatted sql

-- changeset xiaozhi:202608161000
-- 危机内容(轻生/自残)分级处置系统参数：命中危机标签的输入不拦截,放行给LLM共情回复;
-- 危机上下文下LLM输出被出口护栏拦截时播放危机专用兜底话术。幂等：先删除后插入
delete from sys_params where param_code in (
    'content_safety.crisis_labels',
    'content_safety.crisis_output_fallback_message'
);

INSERT INTO sys_params
(id, param_code, param_value, value_type, param_type, remark, creator, create_date, updater, update_date)
VALUES
(646, 'content_safety.crisis_labels', 'inappropriate_suicide', 'array', 1, '危机类目标签(分号分隔)：命中后输入不拦截,放行LLM并标记危机上下文', NULL, NULL, NULL, NULL),
(647, 'content_safety.crisis_output_fallback_message', '听到你这么说,我真的很担心你。你不是一个人,24小时心理援助热线 400-161-9995 一直有人愿意听你说。', 'array', 1, '危机上下文下LLM输出被拦截时的兜底话术(分号分隔多条随机)', NULL, NULL, NULL, NULL);
