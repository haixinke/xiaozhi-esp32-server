package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.dto.CreatePdcNfcBatchDTO;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcBatchQueryDTO;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcBatchVO;

import java.util.List;

/**
 * 批次服务：创建批次并原子分配资产，支持列表查询和取消。
 */
public interface PdcNfcBatchService {

    /**
     * 创建批次并原子分配所有资产。
     * 验证功能开启、商品类型存在、批次号唯一、原型合法、数量范围。
     * 所有资产在同一事务内创建，任一分块失败回滚整批。
     */
    PdcNfcBatchVO create(CreatePdcNfcBatchDTO dto, Long operatorId);

    /**
     * 查询批次列表（含资产统计）。
     */
    List<PdcNfcBatchVO> list(PdcNfcBatchQueryDTO query);

    /**
     * 取消批次：仅当无有效 Scheme/write job 且无 CLAIMED 资产时允许。
     */
    void cancel(Long batchId, Long operatorId);
}
