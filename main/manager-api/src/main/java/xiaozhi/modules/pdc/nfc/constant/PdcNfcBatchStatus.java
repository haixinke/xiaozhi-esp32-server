package xiaozhi.modules.pdc.nfc.constant;

/**
 * NFC 批次状态。
 */
public enum PdcNfcBatchStatus {
    DRAFT,
    SCHEME_GENERATING,
    READY_FOR_WRITE,
    WRITING,
    READY_FOR_STOCK,
    COMPLETED,
    CLOSED,
    CANCELLED
}
