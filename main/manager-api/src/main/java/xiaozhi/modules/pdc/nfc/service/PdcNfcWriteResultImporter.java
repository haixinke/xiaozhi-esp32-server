package xiaozhi.modules.pdc.nfc.service;

import org.springframework.web.multipart.MultipartFile;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.util.UUID;

/**
 * NFC 写卡结果导入器：解析工厂结果 CSV，校验并与快照对比，原子更新资产和任务状态。
 */
public interface PdcNfcWriteResultImporter {

    /**
     * 导入写卡结果。
     *
     * @param jobId      写卡任务 ID
     * @param requestId  幂等请求 ID
     * @param file       结果 CSV 文件
     * @param operatorId 操作员 ID
     * @return 导入结果
     */
    PdcNfcWriteImportVO importResult(Long jobId, UUID requestId, MultipartFile file, Long operatorId);
}
