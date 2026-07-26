-- liquibase formatted sql

-- changeset xiaozhi:202607261200
-- 存量补偿：为已破壳(HATCHED)且尚无上下文源的蛋宝宝智能体登记今日心情实时上下文源。
-- 幂等：仅当 ai_agent_context_provider 中不存在该 agent_id 的行时才插入；
-- 已存在行(理论上有其他 provider)的 agent 跳过，由应用层 ensurePetContextProvider 逻辑兜底。
INSERT INTO ai_agent_context_provider (id, agent_id, context_providers, created_at, updated_at)
SELECT REPLACE(UUID(), '-', ''),
       d.agent_id,
       JSON_ARRAY(JSON_OBJECT('url', '/config/pet-context', 'headers', JSON_OBJECT())),
       NOW(), NOW()
FROM ai_pet p
JOIN ai_device d ON d.id = p.device_id
WHERE p.hatch_status = 'HATCHED'
  AND d.agent_id IS NOT NULL
  AND d.agent_id != ''
  AND NOT EXISTS (
      SELECT 1 FROM ai_agent_context_provider cp WHERE cp.agent_id = d.agent_id
  );
