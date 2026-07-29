package xiaozhi.modules.pdc.nfc.service;

import java.util.UUID;

import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimPreviewVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimResultVO;

public interface PdcNfcClaimService {

    PdcNfcClaimPreviewVO preview(Long userId, String claimRef);

    PdcNfcClaimResultVO confirm(Long userId, String claimRef, UUID requestId);
}
