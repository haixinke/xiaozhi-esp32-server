package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.util.List;
import java.util.UUID;

/**
 * 写卡结果的唯一事务落库入口。
 */
public interface PdcNfcWriteResultTransactionService {

    PdcNfcWriteImportVO apply(
            Long jobId,
            List<ValidatedWriteResult> rows,
            String resultFileSha256,
            Long operatorId,
            UUID requestId);
}
