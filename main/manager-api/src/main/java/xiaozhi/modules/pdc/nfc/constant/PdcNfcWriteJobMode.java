package xiaozhi.modules.pdc.nfc.constant;

/**
 * NFC 写卡任务模式（ADR 0003）。
 * FACTORY_CSV：工厂批量写卡，CSV 导出/导入通道；
 * MANUAL：小批量手动写卡，手机 NFC App 逐张写入 + 触碰自验证。
 * 创建任务时选定，任务内不可变更，两模式通道互斥。
 */
public enum PdcNfcWriteJobMode {
    FACTORY_CSV,
    MANUAL
}
