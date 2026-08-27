package xiaozhi.modules.pdc.nfc.constant;

/**
 * NFC 资产验证来源（仅手动写卡模式使用，ADR 0003）。
 * TOUCH：触碰自验证，preview 命中后由后端自动推进；
 * MANUAL：操作员 NFC App 回读比对后人工确认。
 * 工厂 CSV 模式该字段为空，验证证据来自结果文件的 sha256 与锁卡标记。
 */
public enum PdcNfcVerifySource {
    TOUCH,
    MANUAL
}
