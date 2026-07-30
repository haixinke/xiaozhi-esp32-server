-- NFC 写卡任务快照列重命名：scheme_sha256 -> uri_sha256
-- 配合 202607291000 基线，对已在旧 schema 上部署的环境执行列重命名。
ALTER TABLE pdc_nfc_write_job_item
    CHANGE COLUMN scheme_sha256 uri_sha256 CHAR(64) NOT NULL;
