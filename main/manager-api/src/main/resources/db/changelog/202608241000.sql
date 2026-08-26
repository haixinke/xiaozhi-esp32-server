-- liquibase formatted sql

-- changeset minwang:202608241000
-- 年龄区间功能：ai_wechat_user 新增 age_range 列 + EGG_AGE_RANGE 字典初始数据
ALTER TABLE `ai_wechat_user`
    ADD COLUMN `age_range` VARCHAR(50) NULL COMMENT '年龄区间（字典 EGG_AGE_RANGE 的 dict_value）' AFTER `mbti`;

-- 年龄区间字典（id 104 紧接 103 诉求类型）
delete from `sys_dict_type` where `id` = 104;
INSERT INTO `sys_dict_type` (`id`, `dict_type`, `dict_name`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(104, 'EGG_AGE_RANGE', '蛋宝宝年龄区间', '蛋宝宝小程序用户年龄区间字典', 0, 1, NOW(), 1, NOW());

delete from `sys_dict_data` where `dict_type_id` = 104;
INSERT INTO `sys_dict_data` (`id`, `dict_type_id`, `dict_label`, `dict_value`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
(104001, 104, '14 周岁及以下', 'AGE_0_14', '', 1, 1, NOW(), 1, NOW()),
(104002, 104, '15-35 周岁', 'AGE_15_35', '', 2, 1, NOW(), 1, NOW()),
(104003, 104, '36-60 周岁', 'AGE_36_60', '60 周岁请选择本区间', 3, 1, NOW(), 1, NOW()),
(104004, 104, '60 周岁以上', 'AGE_61_PLUS', '从 61 周岁起算', 4, 1, NOW(), 1, NOW());
