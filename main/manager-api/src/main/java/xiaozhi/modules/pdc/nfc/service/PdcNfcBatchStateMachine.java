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
        WRITING, Set.of(READY_FOR_STOCK, CANCELLED),
        READY_FOR_STOCK, Set.of(COMPLETED, CANCELLED),
        COMPLETED, Set.of(CLOSED)
    );

    public void requireTransition(PdcNfcBatchStatus from, PdcNfcBatchStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
