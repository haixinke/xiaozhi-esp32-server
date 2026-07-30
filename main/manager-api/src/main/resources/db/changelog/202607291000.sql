-- NFC 实物生产域数据库骨架
-- 11 张 pdc_ 前缀表 + 固定商品类型种子

-- 商品类型主数据
CREATE TABLE pdc_nfc_product_type (
  id BIGINT NOT NULL,
  type_code VARCHAR(32) NOT NULL,
  type_name VARCHAR(64) NOT NULL,
  claim_page_path VARCHAR(128) NOT NULL,
  capability_mode VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_product_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO pdc_nfc_product_type
  (id, type_code, type_name, claim_page_path, capability_mode, status, create_date)
VALUES
  (1, 'EGG_BABY_NFC', '蛋宝宝 NFC 实物',
   '/pages/nfc-claim/nfc-claim', 'ONE_DEVICE_ONE_CODE', 'ENABLED', NOW(3));

-- 批次表
CREATE TABLE pdc_nfc_batch (
  id BIGINT NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  product_type_id BIGINT NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  prototype VARCHAR(16) NOT NULL,
  planned_quantity INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  remark VARCHAR(512) NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_batch_no (batch_no),
  KEY idx_pdc_nfc_batch_product (product_type_id),
  KEY idx_pdc_nfc_batch_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 资产表
CREATE TABLE pdc_nfc_asset (
  id BIGINT NOT NULL,
  asset_no VARCHAR(64) NOT NULL,
  batch_id BIGINT NOT NULL,
  item_no VARCHAR(16) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  prototype VARCHAR(16) NOT NULL,
  wechat_sn VARCHAR(64) NOT NULL,
  claim_ref_hash CHAR(64) NOT NULL,
  claim_ref_hash_version VARCHAR(16) NOT NULL,
  claim_ref_key_version VARCHAR(16) NOT NULL,
  claim_ref_nonce VARBINARY(12) NOT NULL,
  claim_ref_ciphertext VARBINARY(128) NOT NULL,
  scheme_key_version VARCHAR(16) NULL,
  scheme_nonce VARBINARY(12) NULL,
  scheme_ciphertext MEDIUMBLOB NULL,
  scheme_sha256 CHAR(64) NULL,
  tag_uid VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  active_scheme_job_id BIGINT NULL,
  active_write_job_id BIGINT NULL,
  scheme_generated_at DATETIME(3) NULL,
  written_at DATETIME(3) NULL,
  verified_at DATETIME(3) NULL,
  stocked_at DATETIME(3) NULL,
  activated_at DATETIME(3) NULL,
  claimed_at DATETIME(3) NULL,
  disabled_at DATETIME(3) NULL,
  scrapped_at DATETIME(3) NULL,
  claimed_user_id BIGINT NULL,
  pet_id VARCHAR(64) NULL,
  stock_business_no VARCHAR(64) NULL,
  activation_business_no VARCHAR(64) NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_asset_no (asset_no),
  UNIQUE KEY uk_pdc_nfc_asset_batch_item (batch_id, item_no),
  UNIQUE KEY uk_pdc_nfc_asset_wechat_sn (wechat_sn),
  UNIQUE KEY uk_pdc_nfc_asset_claim_hash (claim_ref_hash),
  KEY idx_pdc_nfc_asset_status (status),
  KEY idx_pdc_nfc_asset_scheme_lease (active_scheme_job_id),
  KEY idx_pdc_nfc_asset_write_lease (active_write_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Scheme 任务表
CREATE TABLE pdc_nfc_scheme_job (
  id BIGINT NOT NULL,
  job_no VARCHAR(64) NOT NULL,
  batch_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  requested_by BIGINT NOT NULL,
  total_count INT NOT NULL,
  success_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  cursor_asset_id BIGINT NULL,
  lease_owner VARCHAR(128) NULL,
  lease_until DATETIME(3) NULL,
  heartbeat_at DATETIME(3) NULL,
  next_retry_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  create_date DATETIME(3) NOT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_scheme_job_no (job_no),
  KEY idx_pdc_nfc_scheme_job_batch (batch_id),
  KEY idx_pdc_nfc_scheme_job_lease (status, next_retry_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Scheme 尝试记录表
CREATE TABLE pdc_nfc_scheme_attempt (
  id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  wechat_error_code INT NULL,
  error_message VARCHAR(512) NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_scheme_attempt (job_id, asset_id, attempt_no),
  KEY idx_pdc_nfc_scheme_attempt_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 写卡任务表
CREATE TABLE pdc_nfc_write_job (
  id BIGINT NOT NULL,
  job_no VARCHAR(64) NOT NULL,
  batch_id BIGINT NOT NULL,
  format_version VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  total_count INT NOT NULL,
  success_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  file_sha256 CHAR(64) NULL,
  row_count INT NULL,
  export_user_id BIGINT NULL,
  exported_at DATETIME(3) NULL,
  result_file_sha256 CHAR(64) NULL,
  import_request_id CHAR(36) NULL,
  result_response_json JSON NULL,
  import_user_id BIGINT NULL,
  imported_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  creator BIGINT NULL,
  create_date DATETIME(3) NOT NULL,
  updater BIGINT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_write_job_no (job_no),
  KEY idx_pdc_nfc_write_job_batch (batch_id),
  KEY idx_pdc_nfc_write_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 写卡任务项不可变快照表
CREATE TABLE pdc_nfc_write_job_item (
  id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  sequence_no INT NOT NULL,
  asset_no VARCHAR(64) NOT NULL,
  batch_no VARCHAR(64) NOT NULL,
  wechat_sn VARCHAR(64) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  prototype VARCHAR(16) NOT NULL,
  uri_sha256 CHAR(64) NOT NULL,
  uri_tnf VARCHAR(8) NOT NULL,
  uri_type VARCHAR(8) NOT NULL,
  aar_tnf VARCHAR(8) NOT NULL,
  aar_type VARCHAR(32) NOT NULL,
  aar_payload VARCHAR(128) NOT NULL,
  create_date DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_write_item_asset (job_id, asset_id),
  UNIQUE KEY uk_pdc_nfc_write_item_seq (job_id, sequence_no),
  KEY idx_pdc_nfc_write_item_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 写卡结果记录表
CREATE TABLE pdc_nfc_write_record (
  id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  write_result VARCHAR(16) NOT NULL,
  verify_result VARCHAR(16) NOT NULL,
  tag_uid VARCHAR(128) NULL,
  ndef_record_count INT NULL,
  uri_sha256 CHAR(64) NULL,
  aar_package VARCHAR(128) NULL,
  is_read_only TINYINT(1) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(512) NULL,
  written_at DATETIME(3) NULL,
  imported_at DATETIME(3) NOT NULL,
  import_user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_write_record_attempt (job_id, asset_id, attempt_no),
  KEY idx_pdc_nfc_write_record_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 领取记录表（只保存成功或本人重放）
CREATE TABLE pdc_nfc_claim_record (
  id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  request_id CHAR(36) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  pet_id VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  create_date DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_claim_asset (asset_id),
  UNIQUE KEY uk_pdc_nfc_claim_user_request (user_id, request_id),
  KEY idx_pdc_nfc_claim_pet (pet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理幂等请求表
CREATE TABLE pdc_nfc_admin_request (
  id BIGINT NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  request_id CHAR(36) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NULL,
  status VARCHAR(16) NOT NULL,
  operator_user_id BIGINT NOT NULL,
  create_date DATETIME(3) NOT NULL,
  update_date DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pdc_nfc_admin_request (operation_type, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
CREATE TABLE pdc_nfc_operation_log (
  id BIGINT NOT NULL,
  operator_user_id BIGINT NULL,
  request_id CHAR(36) NULL,
  source VARCHAR(32) NOT NULL,
  object_type VARCHAR(32) NOT NULL,
  object_id BIGINT NULL,
  operation_type VARCHAR(64) NOT NULL,
  before_status VARCHAR(32) NULL,
  after_status VARCHAR(32) NULL,
  quantity INT NULL,
  business_no VARCHAR(64) NULL,
  result VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NULL,
  detail_json JSON NULL,
  create_date DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pdc_nfc_operation_object (object_type, object_id, create_date),
  KEY idx_pdc_nfc_operation_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
