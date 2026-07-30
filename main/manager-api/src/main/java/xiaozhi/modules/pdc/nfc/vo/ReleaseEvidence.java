package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 发布证据：不可变快照，记录 NFC 发布审核状态。
 *
 * @param id              证据 ID
 * @param releaseVersion  发布版本
 * @param publishedAt     发布时间
 * @param smokeEvidence   冒烟验证证据
 * @param operatorUserId  登记操作人 ID
 * @param createDate      登记时间
 */
public record ReleaseEvidence(
        Long id,
        String releaseVersion,
        String publishedAt,
        String smokeEvidence,
        Long operatorUserId,
        Date createDate
) {}
