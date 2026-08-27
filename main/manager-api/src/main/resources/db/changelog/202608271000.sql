-- 手动写卡模式（ADR 0003）：
-- 1. 写卡任务增加模式字段，存量任务默认工厂 CSV 模式，保证旧行为不变。
-- 2. 资产增加验证来源与锁卡字段，支撑手动模式的触碰自验证与入库锁卡门禁。

ALTER TABLE pdc_nfc_write_job
    ADD COLUMN mode varchar(16) NOT NULL DEFAULT 'FACTORY_CSV' COMMENT '写卡模式：FACTORY_CSV 工厂CSV模式，MANUAL 手动模式' AFTER format_version;

ALTER TABLE pdc_nfc_asset
    ADD COLUMN verify_source varchar(16) NULL COMMENT '验证来源：TOUCH 触碰自验证，MANUAL 人工验证；工厂CSV模式为空' AFTER verified_at,
    ADD COLUMN locked_at datetime NULL COMMENT '锁卡确认时间（手动模式），为空表示未锁卡' AFTER verify_source,
    ADD COLUMN lock_verified_at datetime NULL COMMENT '锁后触碰复验时间（手动模式）' AFTER locked_at;
