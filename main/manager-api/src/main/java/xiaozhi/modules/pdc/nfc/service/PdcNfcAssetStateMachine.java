package xiaozhi.modules.pdc.nfc.service;

import org.springframework.stereotype.Component;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;

import java.util.Map;
import java.util.Set;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.*;

/**
 * NFC 资产状态机，仅负责转换合法性校验。
 */
@Component
public final class PdcNfcAssetStateMachine {

    private static final Map<PdcNfcAssetStatus, Set<PdcNfcAssetStatus>> ALLOWED = Map.of(
        CREATED, Set.of(SCHEME_GENERATED, SCRAPPED),
        SCHEME_GENERATED, Set.of(WRITTEN, SCRAPPED),
        // WRITTEN -> SCHEME_GENERATED：手动模式写坏回退重写（ADR 0003）
        WRITTEN, Set.of(VERIFIED, SCHEME_GENERATED, SCRAPPED),
        VERIFIED, Set.of(IN_STOCK, SCRAPPED),
        IN_STOCK, Set.of(ACTIVE, DISABLED),
        ACTIVE, Set.of(CLAIMED, DISABLED),
        CLAIMED, Set.of(DISABLED)
    );

    public void requireTransition(PdcNfcAssetStatus from, PdcNfcAssetStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
