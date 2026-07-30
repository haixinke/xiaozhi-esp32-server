package xiaozhi.modules.pdc.nfc.vo;

/**
 * 写卡 CSV 导出文件：文件名 + 字节内容（UTF-8 BOM + CRLF）。
 *
 * @param fileName 导出文件名（如 "write-job-NFC-20260729-001.csv"）
 * @param bytes    CSV 文件的字节内容（含 UTF-8 BOM）
 */
public record PdcNfcWriteFile(String fileName, byte[] bytes) {}
