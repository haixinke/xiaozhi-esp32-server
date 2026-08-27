package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteFile;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteJobVO;

/**
 * NFC 写卡任务服务：创建、导出 CSV、取消、查询进度。
 * <p>
 * 创建时拍摄 SCHEME_GENERATED 资产不可变快照，
 * 导出时从快照 + 解密 Scheme 生成字节稳定的 CSV（UTF-8 BOM + CRLF）。
 */
public interface PdcNfcWriteJobService {

    /**
     * 创建写卡任务：选取批次内 SCHEME_GENERATED 资产，拍摄快照，绑定 active_write_job_id。
     *
     * @param mode 写卡模式：FACTORY_CSV（工厂 CSV 通道）或 MANUAL（手动模式，ADR 0003），
     *             创建后不可变更，两模式通道互斥
     * @return 写卡任务视图
     */
    PdcNfcWriteJobVO create(Long batchId, String mode, Long operatorId);

    /**
     * 导出写卡 CSV：从快照 + 解密 Scheme 生成字节稳定的文件。
     * 可重复调用，每次产出相同字节。
     *
     * @return 包含文件名和字节内容的记录
     */
    PdcNfcWriteFile export(Long jobId, Long operatorId);

    /**
     * 取消写卡任务：仅 CREATED/EXPORTED 且无导入结果时可取消，
     * 释放所有 active_write_job_id。
     */
    void cancel(Long jobId, Long operatorId);

    /**
     * 查询写卡任务进度。
     */
    PdcNfcWriteJobVO getProgress(Long jobId);
}
