-- liquibase formatted sql

-- changeset xiaozhi:202608011200
-- 新增阿里云邮件推送（DirectMail）系统参数，用于聊天记录导出邮件发送
-- 幂等：先删除后插入
delete from sys_params where param_code in (
    'aliyun.dm.access_key_id',
    'aliyun.dm.access_key_secret',
    'aliyun.dm.account_name',
    'aliyun.dm.from_alias',
    'aliyun.dm.tag_name',
    'aliyun.dm.reply_to_address'
);
INSERT INTO sys_params
(id, param_code, param_value, value_type, param_type, remark, creator, create_date, updater, update_date)
    VALUES
(620, 'aliyun.dm.access_key_id', '', 'string', 1, '阿里云邮件推送access_key_id', NULL, NULL, NULL, NULL),
(621, 'aliyun.dm.access_key_secret', '', 'string', 1, '阿里云邮件推送access_key_secret', NULL, NULL, NULL, NULL),
(622, 'aliyun.dm.account_name', '', 'string', 1, '阿里云邮件推送控制台配置的发信地址', NULL, NULL, NULL, NULL),
(623, 'aliyun.dm.from_alias', '蛋宝宝', 'string', 1, '阿里云邮件推送发信人昵称', NULL, NULL, NULL, NULL),
(624, 'aliyun.dm.tag_name', 'chat-export', 'string', 1, '阿里云邮件推送邮件标签', NULL, NULL, NULL, NULL),
(625, 'aliyun.dm.reply_to_address', 'false', 'boolean', 1, '阿里云邮件推送是否启用回信地址', NULL, NULL, NULL, NULL);
