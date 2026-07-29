package xiaozhi.modules.pdc.nfc.service;

import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;

import java.util.Map;
import java.util.Set;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus.*;

/**
 * NFC 写卡任务状态机。
 */
@Component
public final class PdcNfcWriteJobStateMachine {

    private static final Map<PdcNfcWriteJobStatus, Set<PdcNfcWriteJobStatus>> ALLOWED =
        Map.of(
            CREATED, Set.of(EXPORTED, CANCELLED),
            EXPORTED, Set.of(RESULT_IMPORTED, CANCELLED),
            RESULT_IMPORTED, Set.of(COMPLETED)
        );

    public void requireTransition(PdcNfcWriteJobStatus from, PdcNfcWriteJobStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
