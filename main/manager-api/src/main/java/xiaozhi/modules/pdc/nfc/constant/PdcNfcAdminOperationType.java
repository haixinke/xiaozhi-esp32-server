package xiaozhi.modules.pdc.nfc.constant;

/**
 * NFC 管理后台操作类型，用于幂等请求表 operation_type 字段。
 */
public enum PdcNfcAdminOperationType {
    WRITE_RESULT_IMPORT,
    STOCK_IN,
    ACTIVATE,
    DISABLE,
    SCRAP
}
