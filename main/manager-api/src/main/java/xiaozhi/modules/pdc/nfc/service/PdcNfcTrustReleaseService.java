package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.vo.PdcNfcTrustReleaseVO;

import java.util.UUID;

/**
 * NFC 免检放行服务（ADR 0005）。
 * <p>
 * 工厂 CSV 模式的退路通道：代工厂只写卡不回传结果 CSV 时，
 * 操作员触碰抽检（证明 URI 写对、卡可读）并声明确认锁卡后，
 * 将 EXPORTED 任务内全部 SCHEME_GENERATED 资产批量推进为 VERIFIED
 * （verify_source=FACTORY_TRUST），任务置 COMPLETED，批次推进 READY_FOR_STOCK。
 * 与硬校验导入互斥：已 RESULT_IMPORTED 的任务拒绝免检。
 */
public interface PdcNfcTrustReleaseService {

    /**
     * 免检放行一个工厂 CSV 写卡任务。
     *
     * @param jobId         写卡任务 ID（FACTORY_CSV 模式且 EXPORTED）
     * @param lockConfirmed 锁卡人工声明，必须为 true（免检通道无锁卡文件证据）
     * @param operatorId    操作员用户 ID
     * @param requestId     幂等请求 ID（由控制器层幂等服务去重）
     * @return 放行结果
     */
    PdcNfcTrustReleaseVO trustRelease(Long jobId, boolean lockConfirmed, Long operatorId, UUID requestId);
}
