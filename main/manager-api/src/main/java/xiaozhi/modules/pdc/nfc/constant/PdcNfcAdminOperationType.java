package xiaozhi.modules.pdc.nfc.constant;

/**
 * NFC 管理后台操作类型。
 * 用作幂等请求表 operation_type 字段；除 WRITE_RESULT_IMPORT 仅为幂等通道键外，
 * 其余取值同时写入操作日志表（如 TRUST_RELEASE，见 ADR 0005）。
 */
public enum PdcNfcAdminOperationType {
    WRITE_RESULT_IMPORT,
    STOCK_IN,
    ACTIVATE,
    DISABLE,
    SCRAP,
    TRUST_RELEASE
}
