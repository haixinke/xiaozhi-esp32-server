package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.vo.PdcNfcClaimPreviewVO;

public interface PdcNfcClaimService {

    PdcNfcClaimPreviewVO preview(Long userId, String claimRef);
}
