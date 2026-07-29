package xiaozhi.modules.pdc.nfc.vo;

/**
 * 写卡 CSV 导出文件：文件名 + 字节内容（UTF-8 BOM + CRLF）。
 */
public record PdcNfcWriteFile(String fileName, byte[] bytes) {}
