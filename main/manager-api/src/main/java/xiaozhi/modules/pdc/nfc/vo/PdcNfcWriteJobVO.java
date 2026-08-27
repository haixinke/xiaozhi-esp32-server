package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 写卡任务视图对象。
 *
 * @param id             任务 ID
 * @param jobNo          任务编号
 * @param batchId        关联批次 ID
 * @param batchNo        批次编号
 * @param formatVersion  CSV 格式版本号
 * @param mode           写卡模式（FACTORY_CSV / MANUAL）
 * @param status         任务状态
 * @param totalCount     待处理总数
 * @param successCount   成功数
 * @param failureCount   失败数
 * @param fileSha256     导出 CSV 文件的 SHA-256 哈希
 * @param rowCount       CSV 数据行数
 * @param exportedAt     导出时间
 * @param createdAt      创建时间
 */
public record PdcNfcWriteJobVO(
        Long id,
        String jobNo,
        Long batchId,
        String batchNo,
        String formatVersion,
        String mode,
        String status,
        int totalCount,
        int successCount,
        int failureCount,
        String fileSha256,
        int rowCount,
        Date exportedAt,
        Date createdAt
) {}
