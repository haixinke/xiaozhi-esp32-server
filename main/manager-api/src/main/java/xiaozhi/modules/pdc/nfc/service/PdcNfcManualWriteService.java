package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.constant.PdcNfcManualMarkAction;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcManualAssetVO;

import java.util.List;

/**
 * NFC 手动写卡模式服务（ADR 0003）。
 * <p>
 * 面向小批量验证：操作员用手机 NFC App 逐张写入 Scheme，
 * 以触碰自验证（preview 命中）或人工回读确认替代工厂 CSV 结果导入。
 * 与工厂 CSV 通道互斥：仅 mode=MANUAL 的写卡任务可使用本服务。
 */
public interface PdcNfcManualWriteService {

    /**
     * 列出手动任务内全部资产及当前状态，不含 Scheme 明文。
     */
    List<PdcNfcManualAssetVO> listAssets(Long jobId);

    /**
     * 单条解密返回 Scheme 明文，每次调用记审计日志。
     * 明文不落库、不进日志，仅在响应内存中存在。
     */
    String revealScheme(Long jobId, Long assetId, Long operatorId);

    /**
     * 逐张标记：已写入 / 写坏回退 / 人工验证通过 / 已锁卡。
     * 全部走 CAS 更新，状态不符抛 PDC_NFC_INVALID_STATE。
     *
     * @return 标记后的资产视图
     */
    PdcNfcManualAssetVO mark(Long jobId, Long assetId, PdcNfcManualMarkAction action, Long operatorId);

    /**
     * 触碰自验证（claim preview 链路调用，ADR 0003）：
     * 资产 WRITTEN 且属于进行中手动任务时推进 WRITTEN → VERIFIED（verify_source=TOUCH）；
     * 资产 VERIFIED 且已锁卡未复验时记录锁后触碰复验。
     * 不满足条件时不做任何事，不抛异常，不影响 preview 正常返回。
     */
    void touchVerify(PdcNfcAssetEntity asset);

    /**
     * 任务内资产全部 VERIFIED 时完成任务并推进批次 WRITING → READY_FOR_STOCK。
     * 幂等：任务不在 CREATED 状态时不做任何事。
     */
    void maybeComplete(Long jobId, Long operatorId);
}
