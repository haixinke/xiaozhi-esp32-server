package xiaozhi.modules.pdc.nfc.constant;

/**
 * NFC 资产验证来源。
 * TOUCH：触碰自验证（手动写卡模式，preview 命中后由后端自动推进，ADR 0003）；
 * MANUAL：操作员 NFC App 回读比对后人工确认（手动写卡模式）；
 * FACTORY_TRUST：免检放行（工厂 CSV 模式，代工厂未回传结果 CSV 时由操作员抽检 +
 * 锁卡人工声明后批量信任推进，ADR 0005），不提供 uri_sha256 / 锁卡文件证据。
 * 走硬校验导入的工厂 CSV 资产该字段为空，验证证据来自结果文件的 sha256 与锁卡标记。
 */
public enum PdcNfcVerifySource {
    TOUCH,
    MANUAL,
    FACTORY_TRUST
}
