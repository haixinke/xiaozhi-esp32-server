package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 操作日志视图：detailJson 经过 allowlist 过滤。
 */
public record PdcNfcOperationLogVO(
        Long id,
        String objectType,
        Long objectId,
        String operationType,
        Long operatorId,
        Date operateTime,
        String detailJson
) {}
