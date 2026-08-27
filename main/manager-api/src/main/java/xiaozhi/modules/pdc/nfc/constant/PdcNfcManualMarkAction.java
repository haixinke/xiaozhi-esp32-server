package xiaozhi.modules.pdc.nfc.constant;

/**
 * 手动写卡模式的单资产标记动作（ADR 0003）。
 * MARK_WRITTEN：写入成功，SCHEME_GENERATED → WRITTEN；
 * MARK_WRITE_FAILED：写坏回退，WRITTEN → SCHEME_GENERATED，留任务内重写；
 * MARK_VERIFIED：人工验证通过，WRITTEN → VERIFIED（verify_source=MANUAL）；
 * MARK_LOCKED：确认已锁卡，记录 locked_at（不可逆，仅 VERIFIED 后可操作）。
 */
public enum PdcNfcManualMarkAction {
    MARK_WRITTEN,
    MARK_WRITE_FAILED,
    MARK_VERIFIED,
    MARK_LOCKED
}
