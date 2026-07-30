package xiaozhi.modules.pdc.nfc.service;

import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 严格 V1 CSV 导出器：字节稳定、UTF-8 BOM、CRLF、RFC 4180 双引号转义。
 * <p>
 * 任何文本单元格（trim 后以 = + - @ 开头）添加单引号前缀防止公式注入；
 * Scheme URI 不做公式处理但始终以双引号包裹。
 */
@Component
public class PdcNfcWriteCsvExporter {

    public static final String FORMAT_VERSION = "PDC_NFC_WRITE_V1";
    public static final String HEADER =
            "format_version,job_no,batch_no,item_no,asset_no,wechat_sn,sku_code,"
                    + "prototype,uri_tnf,uri_type,uri_payload,aar_tnf,aar_type,aar_payload";

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String CRLF = "\r\n";

    /** NDEF 常量 */
    public static final String URI_TNF = "0x01";
    public static final String URI_TYPE = "U";
    public static final String AAR_TNF = "0x04";
    public static final String AAR_TYPE = "android.com:pkg";
    public static final String AAR_PAYLOAD = "com.tencent.mm";

    /**
     * 生成字节稳定的 CSV（UTF-8 BOM + CRLF）。
     *
     * @param jobNo   任务编号
     * @param batchNo 批次编号
     * @param items   按 sequenceNo 排序的内存行
     * @return CSV 字节数组
     */
    public byte[] generate(String jobNo, String batchNo, List<PdcNfcWriteCsvRow> items) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(UTF8_BOM);
            writeLine(out, HEADER);
            for (PdcNfcWriteCsvRow item : items) {
                String line = buildRow(jobNo, batchNo, item);
                writeLine(out, line);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("CSV generation failed", e);
        }
    }

    /**
     * 计算 SHA-256 摘要（小写 hex）。
     */
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // --- internal ---

    private String buildRow(String jobNo, String batchNo, PdcNfcWriteCsvRow item) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvCell(FORMAT_VERSION)).append(',');
        sb.append(csvCell(formulaGuard(jobNo))).append(',');
        sb.append(csvCell(formulaGuard(batchNo))).append(',');
        sb.append(csvCell(String.format("%06d", item.sequenceNo()))).append(',');
        sb.append(csvCell(formulaGuard(item.assetNo()))).append(',');
        sb.append(csvCell(formulaGuard(item.wechatSn()))).append(',');
        sb.append(csvCell(formulaGuard(item.skuCode()))).append(',');
        sb.append(csvCell(formulaGuard(item.prototype()))).append(',');
        // NDEF 常量：固定值，不做公式处理
        sb.append(csvCell(URI_TNF)).append(',');
        sb.append(csvCell(URI_TYPE)).append(',');
        // Scheme URI：始终双引号包裹，不做公式处理
        sb.append(quote(item.uriPayload())).append(',');
        sb.append(csvCell(AAR_TNF)).append(',');
        sb.append(csvCell(AAR_TYPE)).append(',');
        sb.append(csvCell(AAR_PAYLOAD));
        return sb.toString();
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return quote(value);
        }
        return value;
    }

    /**
     * RFC 4180 双引号转义：内部双引号 → 两个双引号，然后整体用双引号包裹。
     */
    static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    /**
     * 公式注入防护：trim 后以 = + - @ 开头则前缀单引号。
     */
    static String formulaGuard(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            char first = trimmed.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                return "'" + value;
            }
        }
        return value;
    }

    private void writeLine(ByteArrayOutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }
}
