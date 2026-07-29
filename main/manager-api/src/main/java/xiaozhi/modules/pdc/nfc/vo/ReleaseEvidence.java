package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 发布证据：不可变快照，记录商品类型发布审核状态。
 */
public record ReleaseEvidence(
        Long id,
        String evidenceType,
        String evidenceContent,
        Long operatorUserId,
        Date createDate
) {}
