# 按 user_id 删除小程序用户全部数据

适用库：`egg_database`（OceanBase SeekDB，MySQL 兼容模式）。

目标对象：**蛋宝宝小程序终端用户**（`ai_wechat_user.user_id` / `user_profiles.user_id` 体系）。
不包含智控台后台账号（`sys_user`）——若目标是后台管理员账号，勿用本脚本。

## 安全约束（已内置）

- 所有 DELETE 都以 `@uid` 或从 `@uid` 派生的子查询为过滤条件，无任何无 WHERE 的删除。
- SeekDB 不支持临时表（ERROR 1235），脚本不使用临时表：严格按**子表 → 父表**顺序删除，
  删子表时父表（agent/device/pet/order）的行还在，关联子查询始终有效。
- 整段包在一个事务里，预检行数确认无误后才 COMMIT。
- 必须在**同一会话**内从头到尾执行（`@uid` 是会话级变量）。

## 明确不删的表（属于他人或运营域，删除会误伤）

| 表 | 原因 |
|---|---|
| `sys_user` / `sys_user_token` / `sys_operation_log` | 智控台后台账号域，user_id 语义不同 |
| `pdc_nfc_operation_log` / `pdc_nfc_admin_request` | 运营操作审计日志，属合规留痕 |
| `pdc_nfc_batch` / `pdc_nfc_write_job` / `pdc_nfc_write_job_item` / `pdc_nfc_write_record` / `pdc_nfc_scheme_job` / `pdc_nfc_scheme_attempt` | 批次/写卡任务属运营资产，由 creator（管理员）关联，非终端用户数据 |
| `ai_item_sku` / `ai_subscription_plan` / `ai_model_*` / `sys_dict_*` / `sys_params` / `ai_ota` 等 | 全局配置字典，无用户归属 |
| `ai_device_address_book` 中 `target_mac` 指向本用户设备的他人行 | 那是别人的通讯录数据，只删用户自己设备的行（`mac_address` 属于本用户设备） |

## 删除前预检（先跑这段，核对行数符合预期）

```sql
SET @uid = '123';  -- TODO: 改成实际的 user_id。必须带引号：memories/user_profiles 的 user_id 是 varchar，
                   -- 不带引号 OceanBase 会把列值强转 DECIMAL，遇非数字值报 ERROR 1292

SELECT 'ai_agent'        AS tbl, COUNT(*) AS cnt FROM ai_agent        WHERE user_id = @uid
UNION ALL SELECT 'ai_device',        COUNT(*) FROM ai_device          WHERE user_id = @uid
UNION ALL SELECT 'ai_pet',           COUNT(*) FROM ai_pet             WHERE user_id = @uid
UNION ALL SELECT 'ai_companion',     COUNT(*) FROM ai_companion       WHERE user_id = @uid
UNION ALL SELECT 'ai_wechat_user',   COUNT(*) FROM ai_wechat_user     WHERE user_id = @uid
UNION ALL SELECT 'user_profiles',    COUNT(*) FROM user_profiles      WHERE user_id = @uid
UNION ALL SELECT 'ai_payment_order', COUNT(*) FROM ai_payment_order   WHERE user_id = @uid
UNION ALL SELECT 'ai_user_item',     COUNT(*) FROM ai_user_item       WHERE user_id = @uid
UNION ALL SELECT 'ai_voice_clone',   COUNT(*) FROM ai_voice_clone     WHERE user_id = @uid
UNION ALL SELECT 'ai_feedback',      COUNT(*) FROM ai_feedback        WHERE user_id = @uid
UNION ALL SELECT 'pdc_nfc_claim_record', COUNT(*) FROM pdc_nfc_claim_record WHERE user_id = @uid;
```

## 删除脚本（同一会话内完整执行）

```sql
SET @uid = '123';  -- TODO: 改成实际的 user_id，必须与预检一致（带引号，原因见预检段注释）

START TRANSACTION;

-- ============ 第 1 步：智能体子表（agent 行还在，子查询有效） ============

-- 聊天音频：被 chat_history.audio_id 和 voice_print.audio_id 引用，先删
DELETE FROM ai_agent_chat_audio
 WHERE id IN (
   SELECT audio_id FROM ai_agent_chat_history
    WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid)
      AND audio_id IS NOT NULL
 )
 OR id IN (
   SELECT audio_id FROM ai_agent_voice_print
    WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid)
      AND audio_id IS NOT NULL
 );

DELETE FROM ai_agent_chat_title
 WHERE session_id IN (
   SELECT session_id FROM ai_agent_chat_history
    WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid)
      AND session_id IS NOT NULL
 );

DELETE FROM ai_agent_voice_print
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid);

DELETE FROM ai_agent_chat_history
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid);

-- 纠错词：先删 item，再删文件（带"未被其他智能体引用"守卫），最后删 mapping
DELETE FROM ai_agent_correct_word_item
 WHERE file_id IN (
   SELECT file_id FROM ai_agent_correct_word_mapping
    WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid)
      AND file_id IS NOT NULL
 );

DELETE FROM ai_agent_correct_word_file
 WHERE id IN (
   SELECT file_id FROM ai_agent_correct_word_mapping
    WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid)
      AND file_id IS NOT NULL
 )
 AND id NOT IN (
   SELECT file_id FROM ai_agent_correct_word_mapping
    WHERE agent_id NOT IN (SELECT id FROM ai_agent WHERE user_id = @uid)
      AND file_id IS NOT NULL
 );

DELETE FROM ai_agent_correct_word_mapping
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid);

DELETE FROM ai_agent_context_provider
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid);

DELETE FROM ai_agent_plugin_mapping
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid);

DELETE FROM ai_agent_tag_relation
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid);

DELETE FROM ai_agent_snapshot
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid)
    OR user_id = @uid;

DELETE FROM memories
 WHERE agent_id IN (SELECT id FROM ai_agent WHERE user_id = @uid)
    OR user_id = @uid;

-- ============ 第 2 步：设备与宠物子表（device/pet 行还在） ============

-- 只删用户自己设备的通讯录行（mac_address 属于本用户设备）；
-- 他人通讯录里 target_mac 指向本用户设备的行不删，那是别人的数据。
-- COLLATE 必需：address_book.mac_address 是 utf8mb4_0900_ai_ci，
-- device.mac_address 是 utf8mb4_general_ci，直接比较报 ERROR 1267
DELETE FROM ai_device_address_book
 WHERE mac_address COLLATE utf8mb4_general_ci IN (
   SELECT mac_address FROM ai_device WHERE user_id = @uid
 );

DELETE FROM ai_companion
 WHERE user_id = @uid
    OR device_id IN (SELECT id FROM ai_device WHERE user_id = @uid);

DELETE FROM ai_pet_collection_card
 WHERE pet_id IN (SELECT id FROM ai_pet WHERE user_id = @uid);

DELETE FROM ai_pet_hatch_action
 WHERE pet_id IN (SELECT id FROM ai_pet WHERE user_id = @uid);

-- 剧情表无 pet_id，仅 creator 关联；creator 即小程序登录用户 ID
DELETE FROM ai_pet_story_history WHERE creator = @uid;
DELETE FROM ai_pet_story_state   WHERE creator = @uid;

DELETE FROM pdc_nfc_claim_record
 WHERE user_id = @uid
    OR pet_id IN (SELECT id FROM ai_pet WHERE user_id = @uid);

-- 注意：pdc_nfc_asset 是 NFC 物理资产档案。仅当该资产已绑定到此用户的宠物
-- 且确认要连资产档案一起销毁时才执行；多数情况应保留，注释下行即可。
DELETE FROM pdc_nfc_asset
 WHERE pet_id IN (SELECT id FROM ai_pet WHERE user_id = @uid);

DELETE FROM ai_pet   WHERE user_id = @uid;
DELETE FROM ai_device WHERE user_id = @uid;

-- ============ 第 3 步：用户直接持有的业务数据 ============

DELETE FROM ai_voice_clone        WHERE user_id = @uid;
DELETE FROM ai_feedback           WHERE user_id = @uid;
DELETE FROM ai_user_item          WHERE user_id = @uid;
DELETE FROM ai_item_consume_log   WHERE user_id = @uid;
DELETE FROM ai_item_grant_log     WHERE user_id = @uid;
DELETE FROM ai_user_subscription  WHERE user_id = @uid;

-- 退款与回调日志先于订单删除，order_id / out_trade_no 子查询才有效
DELETE FROM ai_payment_refund
 WHERE order_id IN (SELECT id FROM ai_payment_order WHERE user_id = @uid);

DELETE FROM ai_payment_callback_log
 WHERE out_trade_no IN (
   SELECT out_trade_no FROM ai_payment_order WHERE user_id = @uid
 );

DELETE FROM ai_payment_order WHERE user_id = @uid;

-- 作为被邀请人的使用记录；用户自己发出的邀请码（creator=@uid）如有需要另行评估
DELETE FROM ai_invite_usage WHERE invitee_user_id = @uid;

-- ============ 第 4 步：用户主体（最后删） ============

DELETE FROM ai_agent WHERE user_id = @uid;

DELETE FROM ai_wechat_user WHERE user_id = @uid;

DELETE FROM user_profiles WHERE user_id = @uid;

-- ============ 第 5 步：确认无误后提交；有异常改 ROLLBACK ============

COMMIT;
-- ROLLBACK;
```

## 执行后验证（应全部返回 0）

```sql
SELECT (SELECT COUNT(*) FROM ai_agent      WHERE user_id = @uid)
     + (SELECT COUNT(*) FROM ai_device     WHERE user_id = @uid)
     + (SELECT COUNT(*) FROM ai_pet        WHERE user_id = @uid)
     + (SELECT COUNT(*) FROM ai_wechat_user WHERE user_id = @uid)
     + (SELECT COUNT(*) FROM user_profiles WHERE user_id = @uid) AS remaining;
```

注意：`@uid` 随会话结束失效，验证须与删除在同一会话执行，或把 `@uid` 替换为字面量。

## 已知边界

0. **SeekDB 限制**：不支持 MySQL 临时表（ERROR 1235）；CTAS 带列定义子句会报 4028。脚本因此全部使用实时子查询，依赖删除顺序保证关联有效——**不要调整各 DELETE 的先后顺序**。
   另：`memories` / `user_profiles` 的 `user_id` 是 varchar（Python 服务端按字符串存），`@uid` 必须带引号赋值，否则 OceanBase 把列值强转 DECIMAL，遇非数字值报 ERROR 1292。
1. **OSS 文件不在本脚本范围**：`ai_agent_chat_audio.oss_key`、`ai_voice_clone` 等指向的对象存储文件需另行清理。
2. **`creator` 语义**：`ai_pet_story_*` 等表的 `creator` 由框架填登录用户 ID，本脚本假定其等于小程序用户 ID；执行前可用 `SELECT COUNT(*) FROM ai_pet_story_state WHERE creator = @uid` 抽样确认。
3. **`ai_invite_code`**：若该用户发过邀请码且被他人使用，删除邀请码会影响受邀人的归因，默认未删（`ai_invite_usage` 只删其作为受邀人的记录）。
