-- AI伴侣表增加亲密程度字段
ALTER TABLE ai_companion ADD COLUMN intimacy FLOAT NULL COMMENT '亲密程度: 0.0~1.0' AFTER relation_type;
