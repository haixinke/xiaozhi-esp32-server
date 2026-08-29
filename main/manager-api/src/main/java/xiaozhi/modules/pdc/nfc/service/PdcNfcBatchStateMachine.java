package xiaozhi.modules.pdc.nfc.service;

import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;

import java.util.Map;
import java.util.Set;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus.*;

/**
 * NFC 批次状态机。
 */
@Component
public final class PdcNfcBatchStateMachine {

    private static final Map<PdcNfcBatchStatus, Set<PdcNfcBatchStatus>> ALLOWED = Map.of(
        DRAFT, Set.of(SCHEME_GENERATING, CANCELLED),
        SCHEME_GENERATING, Set.of(READY_FOR_WRITE, CANCELLED),
        READY_FOR_WRITE, Set.of(WRITING, CANCELLED),
        // 写卡任务取消时允许回退 READY_FOR_WRITE：取消只在无写卡结果时发生，资产已释放可重建任务
        WRITING, Set.of(READY_FOR_STOCK, READY_FOR_WRITE, CANCELLED),
        READY_FOR_STOCK, Set.of(COMPLETED, CANCELLED),
        COMPLETED, Set.of(CLOSED)
    );

    public void requireTransition(PdcNfcBatchStatus from, PdcNfcBatchStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
