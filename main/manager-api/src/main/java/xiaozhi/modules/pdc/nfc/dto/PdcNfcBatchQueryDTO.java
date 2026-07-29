package xiaozhi.modules.pdc.nfc.dto;

import lombok.Data;

@Data
public class PdcNfcBatchQueryDTO {
    private String batchNo;
    private Long productTypeId;
    private String status;
    private String prototype;
}
