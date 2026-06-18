-- 微信支付 V3 配置项
-- 敏感字段(private_key / api_v3_key)需用 AESUtils.encrypt(server.secret, ...) 加密后再写入
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark) VALUES
(601, 'wechat.pay.mchid',        'null',  'string',  1, '1737553768'),
(602, 'wechat.pay.serial_no',    'null',  'string',  1, '67552162F0E46F4538BD67B8BA7D0C4B13F8ACF7'),
(603, 'wechat.pay.private_key',  'null',  'string',  1, '商户API私钥PEM(AESUtils加密入库)'),
(604, 'wechat.pay.api_v3_key',   'null',  'string',  1, 'APIv3密钥(32字节,AESUtils加密入库)'),
(605, 'wechat.pay.notify_url',   'null',  'string',  1, 'https://127.0.0.1/payment/notify');
