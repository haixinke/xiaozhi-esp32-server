package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 发布证据：不可变快照，记录商品类型发布审核状态。
 *
 * @param id              证据 ID
 * @param evidenceType    证据类型（FIRMWARE_VERSION / PRODUCTION_VERIFY / QUALITY_AUDIT）
 * @param evidenceContent 证据内容
 * @param operatorUserId  登记操作人 ID
 * @param createDate      登记时间
 */
public record ReleaseEvidence(
        Long id,
        String evidenceType,
        String evidenceContent,
        Long operatorUserId,
        Date createDate
) {}
