-- liquibase formatted sql

-- changeset minwang:202608071100
-- 为 sys_user 添加 role 字段，区分管理员和运营者
ALTER TABLE `sys_user` ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'admin' COMMENT '角色：admin=管理员 operator=运营者';
