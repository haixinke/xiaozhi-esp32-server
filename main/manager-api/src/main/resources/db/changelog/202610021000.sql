-- 宠物创建来源标记：区分领养入口（邀请码 / NFC 触碰 / 设备出生）
-- 三步：加列(可空) → 存量回填 → 改 NOT NULL
-- 存量统一回填 INVITE_CODE（产品决策：历史数据全部按邀请码口径处理，含已逻辑删除行）
ALTER TABLE `ai_pet` ADD COLUMN `source` VARCHAR(32) NULL COMMENT '创建来源: INVITE_CODE-邀请码(激活码)领养, NFC-NFC触碰领取, DEVICE_BIRTH-设备出生';

UPDATE `ai_pet` SET `source` = 'INVITE_CODE' WHERE `source` IS NULL;

ALTER TABLE `ai_pet` MODIFY COLUMN `source` VARCHAR(32) NOT NULL COMMENT '创建来源: INVITE_CODE-邀请码(激活码)领养, NFC-NFC触碰领取, DEVICE_BIRTH-设备出生';
