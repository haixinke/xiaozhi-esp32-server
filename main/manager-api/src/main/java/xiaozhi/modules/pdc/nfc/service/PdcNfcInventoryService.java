package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcAssetQueryDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcBulkAssetOperationDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcOperationLogQueryDTO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcAssetVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBulkOperationVO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcOperationLogVO;

/**
 * NFC 库存流转服务：入库 / 激活 / 停用 / 作废，以及资产和日志查询。
 * <p>
 * 所有批量写操作通过 {@link PdcNfcAdminIdempotencyService} 保证幂等。
 */
public interface PdcNfcInventoryService {

    /**
     * 批量入库：VERIFIED → IN_STOCK
     */
    PdcNfcBulkOperationVO stockIn(PdcNfcBulkAssetOperationDTO request, Long operatorId);

    /**
     * 批量激活：IN_STOCK → ACTIVE
     */
    PdcNfcBulkOperationVO activate(PdcNfcBulkAssetOperationDTO request, Long operatorId);

    /**
     * 批量停用：IN_STOCK / ACTIVE / CLAIMED → DISABLED
     * <p>
     * CLAIMED 状态的资产保留 claimedUserId、petId 等关联信息。
     */
    PdcNfcBulkOperationVO disable(PdcNfcBulkAssetOperationDTO request, Long operatorId);

    /**
     * 批量作废：CREATED / SCHEME_GENERATED / WRITTEN / VERIFIED → SCRAPPED
     */
    PdcNfcBulkOperationVO scrap(PdcNfcBulkAssetOperationDTO request, Long operatorId);

    /**
     * 资产分页查询
     */
    PageData<PdcNfcAssetVO> queryAssets(PdcNfcAssetQueryDTO query);

    /**
     * 资产详情
     */
    PdcNfcAssetVO getAssetDetail(Long id);

    /**
     * 操作日志分页查询
     */
    PageData<PdcNfcOperationLogVO> queryOperationLogs(PdcNfcOperationLogQueryDTO query);

    /**
     * 按对象查询操作日志
     */
    PageData<PdcNfcOperationLogVO> queryLogsByObject(String objectType, Long objectId, PdcNfcOperationLogQueryDTO query);
}
