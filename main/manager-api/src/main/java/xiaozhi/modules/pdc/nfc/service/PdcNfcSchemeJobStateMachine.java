package xiaozhi.modules.pdc.nfc.service;

import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcSchemeJobStatus;

import java.util.Map;
import java.util.Set;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcSchemeJobStatus.*;

/**
 * NFC Scheme 任务状态机。
 */
@Component
public final class PdcNfcSchemeJobStateMachine {

    private static final Map<PdcNfcSchemeJobStatus, Set<PdcNfcSchemeJobStatus>> ALLOWED =
        Map.of(
            PENDING, Set.of(RUNNING, CANCELLED),
            RUNNING, Set.of(PARTIAL_SUCCESS, SUCCEEDED, FAILED, CANCELLED)
        );

    public void requireTransition(PdcNfcSchemeJobStatus from, PdcNfcSchemeJobStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
