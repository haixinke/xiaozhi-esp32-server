-- liquibase formatted sql

-- changeset xiaozhi:202608051500
-- 新增阿里云AI安全护栏系统参数；幂等：先删除后插入
delete from sys_params where param_code in (
    'aliyun.access_key_id',
    'aliyun.access_key_secret',
    'content_safety.enabled',
    'content_safety.provider',
    'content_safety.mode',
    'content_safety.region_id',
    'content_safety.endpoint',
    'content_safety.connect_timeout_ms',
    'content_safety.read_timeout_ms',
    'content_safety.max_qps',
    'content_safety.max_request_chars',
    'content_safety.output_chunk_chars',
    'content_safety.input_service',
    'content_safety.output_service',
    'content_safety.input_block_message',
    'content_safety.output_block_message'
);

INSERT INTO sys_params
(id, param_code, param_value, value_type, param_type, remark, creator, create_date, updater, update_date)
VALUES
(630, 'aliyun.access_key_id', '', 'string', 1, '阿里云通用AccessKey ID（AI安全护栏使用）', NULL, NULL, NULL, NULL),
(631, 'aliyun.access_key_secret', '', 'string', 1, '阿里云通用AccessKey Secret（AI安全护栏使用）', NULL, NULL, NULL, NULL),
(632, 'content_safety.enabled', 'false', 'boolean', 1, '是否启用LLM输入输出内容安全审核', NULL, NULL, NULL, NULL),
(633, 'content_safety.provider', 'aliyun', 'string', 1, '内容安全服务提供商', NULL, NULL, NULL, NULL),
(634, 'content_safety.mode', 'enforce', 'string', 1, '审核模式：observe仅观察，enforce阻断', NULL, NULL, NULL, NULL),
(635, 'content_safety.region_id', 'cn-shanghai', 'string', 1, '阿里云AI安全护栏地域', NULL, NULL, NULL, NULL),
(636, 'content_safety.endpoint', 'green-cip.cn-shanghai.aliyuncs.com', 'string', 1, '阿里云AI安全护栏接入端点', NULL, NULL, NULL, NULL),
(637, 'content_safety.connect_timeout_ms', '3000', 'number', 1, '内容安全连接超时毫秒', NULL, NULL, NULL, NULL),
(638, 'content_safety.read_timeout_ms', '10000', 'number', 1, '内容安全读取超时毫秒', NULL, NULL, NULL, NULL),
(639, 'content_safety.max_qps', '45', 'number', 1, '单进程内容安全最大QPS，不能超过50', NULL, NULL, NULL, NULL),
(640, 'content_safety.max_request_chars', '2000', 'number', 1, '单次内容安全审核最大字符数', NULL, NULL, NULL, NULL),
(641, 'content_safety.output_chunk_chars', '120', 'number', 1, 'LLM输出审核缓冲字符数', NULL, NULL, NULL, NULL),
(642, 'content_safety.input_service', 'query_security_check_pro', 'string', 1, 'LLM输入内容安全审核服务', NULL, NULL, NULL, NULL),
(643, 'content_safety.output_service', 'response_security_check_pro', 'string', 1, 'LLM输出内容安全审核服务', NULL, NULL, NULL, NULL),
(644, 'content_safety.input_block_message', '抱歉，这个内容我不能处理;换个话题聊聊吧', 'array', 1, '输入内容被拦截时随机回复', NULL, NULL, NULL, NULL),
(645, 'content_safety.output_block_message', '抱歉，这个回复不能继续提供;我们换个话题吧', 'array', 1, '输出内容被拦截时随机回复', NULL, NULL, NULL, NULL);
