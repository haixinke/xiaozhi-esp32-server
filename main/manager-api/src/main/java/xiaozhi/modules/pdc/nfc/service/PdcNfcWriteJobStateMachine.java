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
            // CREATED -> COMPLETED：手动模式不经过导出/导入，全部资产验证通过后直接完成（ADR 0003）
            CREATED, Set.of(EXPORTED, COMPLETED, CANCELLED),
            // EXPORTED -> COMPLETED：免检放行（ADR 0005，工厂未回传结果 CSV 时跳过导入直接完成）
            EXPORTED, Set.of(RESULT_IMPORTED, COMPLETED, CANCELLED),
            RESULT_IMPORTED, Set.of(COMPLETED)
        );

    public void requireTransition(PdcNfcWriteJobStatus from, PdcNfcWriteJobStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
    }
}
