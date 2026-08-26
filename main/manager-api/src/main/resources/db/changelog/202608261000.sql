-- liquibase formatted sql

-- changeset minwang:202608261000
-- 蛋宝宝原型字典（id 105 紧接 104 年龄区间）
-- 注意：dict_value 必须与后端中文原型值完全一致，
-- 即 PdcNfcPrototype.code 与 PetServiceImpl 的 PROTOTYPE_KOI / PROTOTYPE_RABBIT，
-- 否则创建批次会被 PdcNfcPrototype.isValid 拒绝、领取时会被 requireValidPrototype 拒绝。
delete from `sys_dict_type` where `id` = 105;
INSERT INTO `sys_dict_type` (`id`, `dict_type`, `dict_name`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(105, 'EGG_PET_PROTOTYPE', '蛋宝宝原型', 'NFC生产批次与宠物原型字典', 0, 1, NOW(), 1, NOW());

delete from `sys_dict_data` where `dict_type_id` = 105;
INSERT INTO `sys_dict_data` (`id`, `dict_type_id`, `dict_label`, `dict_value`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(105001, 105, '锦鲤', '锦鲤', 'dict_value 需与后端中文原型值一致，勿改为拼音', 1, 1, NOW(), 1, NOW()),
(105002, 105, '玉兔', '玉兔', 'dict_value 需与后端中文原型值一致，勿改为拼音', 2, 1, NOW(), 1, NOW());
