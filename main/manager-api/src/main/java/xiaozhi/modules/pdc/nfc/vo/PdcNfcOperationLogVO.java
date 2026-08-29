package xiaozhi.modules.pdc.nfc.vo;

import java.util.Date;

/**
 * 操作日志视图：detailJson 经过 allowlist 过滤。
 *
 * @param id            日志 ID
 * @param objectType    对象类型（BATCH / ASSET / SCHEME_JOB / WRITE_JOB）
 * @param objectId      对象 ID
 * @param operationType 操作类型
 * @param operatorId    操作人 ID
 * @param operateTime   操作时间
 * @param beforeStatus  变更前状态（无状态流转的操作为 null）
 * @param afterStatus   变更后状态（无状态流转的操作为 null）
 * @param detailJson    详情 JSON（经 allowlist 过滤，不含敏感字段）
 */
public record PdcNfcOperationLogVO(
        Long id,
        String objectType,
        Long objectId,
        String operationType,
        Long operatorId,
        Date operateTime,
        String beforeStatus,
        String afterStatus,
        String detailJson
) {}
