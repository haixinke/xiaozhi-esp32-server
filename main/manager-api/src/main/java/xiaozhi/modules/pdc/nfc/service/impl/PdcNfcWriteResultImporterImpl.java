package xiaozhi.modules.pdc.nfc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcBatchDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcOperationLogDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteRecordDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcWriteResultRow;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteRecordEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcAssetStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteCsvExporter;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteJobStateMachine;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultImporter;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.*;

/**
 * NFC 写卡结果导入器实现。
 * <p>
 * Phase 1: 解析 CSV、校验格式、计算文件 SHA-256
 * Phase 2: 在事务内锁定资产、匹配快照、逐行验证、原子更新状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdcNfcWriteResultImporterImpl implements PdcNfcWriteResultImporter {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MiB
    private static final int MAX_LINE_LENGTH = 4096;

    public static final String EXPECTED_HEADER =
            "format_version,job_no,batch_no,item_no,asset_no,wechat_sn,sku_code,"
                    + "prototype,uri_tnf,uri_type,uri_payload,aar_tnf,aar_type,aar_payload,"
                    + "write_success,verify_success,ndef_record_count,uri_sha256,aar_package,read_only";

    private final PdcNfcWriteJobDao jobDao;
    private final PdcNfcWriteJobItemDao jobItemDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcBatchDao batchDao;
    private final PdcNfcWriteRecordDao writeRecordDao;
    private final PdcNfcOperationLogDao operationLogDao;
    private final PdcNfcWriteJobStateMachine writeJobStateMachine;
    private final PdcNfcAssetStateMachine assetStateMachine;

    @Override
    public PdcNfcWriteImportVO importResult(Long jobId, UUID requestId,
                                            MultipartFile file, Long operatorId) {
        // Phase 1: 文件校验和 CSV 解析
        validateFile(file);
        byte[] fileBytes = readFileBytes(file);

        // Issue 4: UTF-8 有效性检查
        validateUtf8(fileBytes);

        String fileSha256 = PdcNfcWriteCsvExporter.sha256Hex(fileBytes);

        String content = stripBom(new String(fileBytes, StandardCharsets.UTF_8));
        List<String> lines = splitLines(content);
        validateHeader(lines);

        List<PdcNfcWriteResultRow> rows = new ArrayList<>();
        List<String> jobNos = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) continue;

            // Issue 3: 行长度检查
            if (line.length() > MAX_LINE_LENGTH) {
                throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR,
                        "Row exceeds " + MAX_LINE_LENGTH + " characters");
            }

            PdcNfcWriteResultRow row = parseRow(line, i + 1);
            rows.add(row);

            // 提取 job_no（第 2 列）用于后续校验
            List<String> fields = parseCsvFields(line);
            jobNos.add(fields.get(1));
        }

        if (rows.isEmpty()) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }

        // 检查重复键 (assetNo + wechatSn)
        Set<String> rowKeys = new HashSet<>();
        for (PdcNfcWriteResultRow row : rows) {
            String key = row.assetNo() + "|" + row.wechatSn();
            if (!rowKeys.add(key)) {
                throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
            }
        }

        // Phase 2: 事务内处理
        return doImport(jobId, requestId, rows, jobNos, fileSha256, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PdcNfcWriteImportVO doImport(Long jobId, UUID requestId,
                                        List<PdcNfcWriteResultRow> rows,
                                        List<String> jobNos,
                                        String fileSha256, Long operatorId) {
        // 加载写卡任务
        PdcNfcWriteJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }

        String currentJobStatus = job.getStatus();
        if (!PdcNfcWriteJobStatus.EXPORTED.name().equals(currentJobStatus)
                && !PdcNfcWriteJobStatus.RESULT_IMPORTED.name().equals(currentJobStatus)) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }

        // Issue 2: job_no 校验 — 所有行的 job_no 必须匹配任务编号
        String expectedJobNo = job.getJobNo();
        for (String jobNo : jobNos) {
            if (!expectedJobNo.equals(jobNo)) {
                throw new RenException(ErrorCode.PDC_NFC_CSV_CONTENT_MISMATCH);
            }
        }

        // 加载快照
        List<PdcNfcWriteJobItemEntity> items = jobItemDao.selectList(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId)
                        .orderByAsc(PdcNfcWriteJobItemEntity::getSequenceNo));

        // 集合等价校验：行数必须匹配
        if (rows.size() != items.size()) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_CONTENT_MISMATCH);
        }

        // 构建快照索引 (assetNo + wechatSn → item)
        Map<String, PdcNfcWriteJobItemEntity> itemMap = new LinkedHashMap<>();
        for (PdcNfcWriteJobItemEntity item : items) {
            itemMap.put(item.getAssetNo() + "|" + item.getWechatSn(), item);
        }

        // 锁定资产（按 ID 排序 FOR UPDATE）
        List<Long> sortedAssetIds = items.stream()
                .map(PdcNfcWriteJobItemEntity::getAssetId)
                .sorted()
                .collect(Collectors.toList());
        List<PdcNfcAssetEntity> lockedAssets = assetDao.selectByIdsForUpdate(sortedAssetIds);
        Map<Long, PdcNfcAssetEntity> assetById = lockedAssets.stream()
                .collect(Collectors.toMap(PdcNfcAssetEntity::getId, a -> a));

        // 逐行匹配和验证
        int verifiedCount = 0;
        int writtenCount = 0;
        int failureCount = 0;
        int scrappedCount = 0;
        Date now = new Date();

        List<RowResult> rowResults = new ArrayList<>();

        for (PdcNfcWriteResultRow row : rows) {
            String key = row.assetNo() + "|" + row.wechatSn();
            PdcNfcWriteJobItemEntity item = itemMap.get(key);
            if (item == null) {
                throw new RenException(ErrorCode.PDC_NFC_CSV_CONTENT_MISMATCH);
            }

            PdcNfcAssetEntity asset = assetById.get(item.getAssetId());
            if (asset == null) {
                throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
            }

            // 验证六项检查
            boolean fullyVerified = row.writeSuccess() && row.verifySuccess()
                    && row.uriSha256() != null && row.uriSha256().equals(item.getUriSha256())
                    && row.ndefRecordCount() == 2
                    && "com.tencent.mm".equals(row.aarPackage())
                    && row.readOnly();

            PdcNfcAssetStatus targetStatus = determineAssetTargetStatus(row, fullyVerified);

            if (fullyVerified) {
                verifiedCount++;
            } else if (targetStatus == SCRAPPED) {
                scrappedCount++;
            } else if (row.writeSuccess()) {
                writtenCount++;
            } else {
                failureCount++;
            }

            rowResults.add(new RowResult(row, item, asset, targetStatus, fullyVerified));
        }

        boolean allVerified = (verifiedCount == rows.size());

        // 更新资产状态
        for (RowResult rr : rowResults) {
            PdcNfcAssetEntity asset = rr.asset();
            PdcNfcAssetStatus currentStatus = PdcNfcAssetStatus.valueOf(asset.getStatus());

            if (!currentStatus.equals(rr.targetStatus())) {
                PdcNfcAssetStatus target = rr.targetStatus();
                // SCHEME_GENERATED → VERIFIED 需要两步: → WRITTEN → VERIFIED
                if (currentStatus == SCHEME_GENERATED && target == VERIFIED) {
                    assetStateMachine.requireTransition(SCHEME_GENERATED, WRITTEN);
                    asset.setStatus(WRITTEN.name());
                    asset.setWrittenAt(now);
                    assetDao.updateById(asset);
                    assetStateMachine.requireTransition(WRITTEN, VERIFIED);
                    asset.setStatus(VERIFIED.name());
                    asset.setVerifiedAt(now);
                } else {
                    assetStateMachine.requireTransition(currentStatus, target);
                    asset.setStatus(target.name());
                    if (target == WRITTEN) {
                        asset.setWrittenAt(now);
                    } else if (target == VERIFIED) {
                        if (asset.getWrittenAt() == null) {
                            asset.setWrittenAt(now);
                        }
                        asset.setVerifiedAt(now);
                    }
                }
            }

            // 成功处理（WRITTEN 或 VERIFIED）时释放 active write lease
            if (rr.targetStatus() == WRITTEN || rr.targetStatus() == VERIFIED) {
                asset.setActiveWriteJobId(null);
            }

            asset.setUpdater(operatorId);
            asset.setUpdateDate(now);
            assetDao.updateById(asset);

            // 创建写卡记录
            PdcNfcWriteRecordEntity record = new PdcNfcWriteRecordEntity();
            record.setJobId(jobId);
            record.setAssetId(asset.getId());
            record.setAttemptNo(1);
            record.setWriteResult(rr.row().writeSuccess() ? "SUCCESS" : "FAILURE");
            record.setVerifyResult(rr.fullyVerified() ? "SUCCESS" :
                    (rr.row().verifySuccess() ? "SUCCESS" : "FAILURE"));
            record.setNdefRecordCount(rr.row().ndefRecordCount());
            record.setUriSha256(rr.row().uriSha256());
            record.setAarPackage(rr.row().aarPackage());
            record.setIsReadOnly(rr.row().readOnly());
            record.setWrittenAt(now);
            record.setImportedAt(now);
            record.setImportUserId(operatorId);
            writeRecordDao.insert(record);
        }

        // 推进任务状态
        if (allVerified) {
            advanceJobToCompleted(job, currentJobStatus, verifiedCount, 0,
                    fileSha256, requestId, operatorId, now);
        } else {
            advanceJobToResultImported(job, currentJobStatus, verifiedCount, failureCount,
                    fileSha256, requestId, operatorId, now);
        }

        // 操作日志
        logOperation(operatorId, requestId, "WRITE_JOB", job.getId(), "IMPORT_RESULT",
                currentJobStatus, job.getStatus(), rows.size());

        log.info("Write result imported: jobId={}, verified={}, written={}, failed={}, scrapped={}, sha256={}",
                jobId, verifiedCount, writtenCount, failureCount, scrappedCount, fileSha256);

        return new PdcNfcWriteImportVO(
                jobId, job.getJobNo(),
                verifiedCount, writtenCount, failureCount,
                fileSha256, requestId
        );
    }

    // --- 状态判定 ---

    private PdcNfcAssetStatus determineAssetTargetStatus(PdcNfcWriteResultRow row,
                                                          boolean fullyVerified) {
        if (fullyVerified) return VERIFIED;
        // Issue 5: 设备被锁定（其他应用已写入且只读）→ SCRAPPED
        if (row.deviceLocked()) return SCRAPPED;
        if (row.writeSuccess()) return WRITTEN;
        return SCHEME_GENERATED;
    }

    private void advanceJobToCompleted(PdcNfcWriteJobEntity job, String currentStatus,
                                       int successCount, int failureCount,
                                       String fileSha256, UUID requestId,
                                       Long operatorId, Date now) {
        PdcNfcWriteJobStatus from = PdcNfcWriteJobStatus.valueOf(currentStatus);

        if (from == PdcNfcWriteJobStatus.EXPORTED) {
            writeJobStateMachine.requireTransition(
                    PdcNfcWriteJobStatus.EXPORTED, PdcNfcWriteJobStatus.RESULT_IMPORTED);
        }
        writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.RESULT_IMPORTED, PdcNfcWriteJobStatus.COMPLETED);

        job.setStatus(PdcNfcWriteJobStatus.COMPLETED.name());
        job.setSuccessCount(successCount);
        job.setFailureCount(failureCount);
        job.setResultFileSha256(fileSha256);
        job.setImportRequestId(requestId.toString());
        job.setImportUserId(operatorId);
        job.setImportedAt(now);
        job.setCompletedAt(now);
        job.setUpdater(operatorId);
        job.setUpdateDate(now);
        jobDao.updateById(job);
    }

    private void advanceJobToResultImported(PdcNfcWriteJobEntity job, String currentStatus,
                                            int successCount, int failureCount,
                                            String fileSha256, UUID requestId,
                                            Long operatorId, Date now) {
        PdcNfcWriteJobStatus from = PdcNfcWriteJobStatus.valueOf(currentStatus);
        if (from == PdcNfcWriteJobStatus.EXPORTED) {
            writeJobStateMachine.requireTransition(
                    PdcNfcWriteJobStatus.EXPORTED, PdcNfcWriteJobStatus.RESULT_IMPORTED);
            job.setStatus(PdcNfcWriteJobStatus.RESULT_IMPORTED.name());
        }

        job.setSuccessCount(successCount);
        job.setFailureCount(failureCount);
        job.setResultFileSha256(fileSha256);
        job.setImportRequestId(requestId.toString());
        job.setImportUserId(operatorId);
        job.setImportedAt(now);
        job.setUpdater(operatorId);
        job.setUpdateDate(now);
        jobDao.updateById(job);
    }

    // --- CSV 解析 ---

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }
    }

    /**
     * Issue 4: 检测文件字节是否为合法 UTF-8。
     * 方法：解码后再编码回 UTF-8，比较字节数组是否一致。
     * 不一致说明原始字节包含无效 UTF-8 序列（Java 静默替换为 U+FFFD）。
     */
    private void validateUtf8(byte[] fileBytes) {
        String decoded = new String(fileBytes, StandardCharsets.UTF_8);
        byte[] reencoded = decoded.getBytes(StandardCharsets.UTF_8);
        if (!Arrays.equals(fileBytes, reencoded)) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR,
                    "File is not valid UTF-8");
        }
    }

    private String stripBom(String text) {
        if (text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }

    private List<String> splitLines(String content) {
        // 规范化：去除所有孤立 \r，保留 \r\n 或 \n
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                lines.add(part);
            }
        }
        return lines;
    }

    private void validateHeader(List<String> lines) {
        if (lines.isEmpty()) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }
        String headerLine = unquoteLine(lines.get(0));
        if (!EXPECTED_HEADER.equals(headerLine)) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }
    }

    private String unquoteLine(String line) {
        List<String> fields = parseCsvFields(line);
        return String.join(",", fields);
    }

    private PdcNfcWriteResultRow parseRow(String line, int lineNum) {
        List<String> fields = parseCsvFields(line);
        if (fields.size() != 20) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }

        // Issue 1: format_version 校验
        String formatVersion = fields.get(0);
        if (!PdcNfcWriteCsvExporter.FORMAT_VERSION.equals(formatVersion)) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }

        return new PdcNfcWriteResultRow(
                fields.get(4),  // asset_no
                fields.get(5),  // wechat_sn
                fields.get(6),  // sku_code
                parseBoolean(fields.get(14)),  // write_success
                parseBoolean(fields.get(15)),  // verify_success
                fields.get(17),                // uri_sha256
                parseInt(fields.get(16), lineNum),  // ndef_record_count
                fields.get(18),                // aar_package
                parseBoolean(fields.get(19)),  // read_only
                false                          // deviceLocked (Issue 5: 默认 false，未来可从 CSV 额外列读取)
        );
    }

    public static List<String> parseCsvFields(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        int len = line.length();

        while (i <= len) {
            if (i == len) {
                // trailing comma case
                if (i > 0 && line.charAt(i - 1) == ',') {
                    fields.add("");
                }
                break;
            }

            if (line.charAt(i) == '"') {
                // 引号字段
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote
                while (i < len) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (i + 1 < len && line.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            i++; // skip closing quote
                            break;
                        }
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                fields.add(sb.toString());
                if (i < len && line.charAt(i) == ',') {
                    i++; // skip comma
                }
            } else {
                // 非引号字段
                int start = i;
                while (i < len && line.charAt(i) != ',') {
                    i++;
                }
                fields.add(line.substring(start, i));
                if (i < len) {
                    i++; // skip comma
                } else {
                    break;
                }
            }
        }

        return fields;
    }

    private boolean parseBoolean(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase();
        return "true".equals(lower) || "1".equals(lower)
                || "yes".equals(lower) || "y".equals(lower);
    }

    private int parseInt(String value, int lineNum) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
        }
    }

    // --- 操作日志 ---

    private void logOperation(Long operatorId, UUID requestId, String objectType,
                              Long objectId, String operationType,
                              String beforeStatus, String afterStatus, Integer quantity) {
        try {
            PdcNfcOperationLogEntity logEntry = new PdcNfcOperationLogEntity();
            logEntry.setOperatorUserId(operatorId);
            logEntry.setRequestId(requestId != null ? requestId.toString() : null);
            logEntry.setSource("ADMIN");
            logEntry.setObjectType(objectType);
            logEntry.setObjectId(objectId);
            logEntry.setOperationType(operationType);
            logEntry.setBeforeStatus(beforeStatus);
            logEntry.setAfterStatus(afterStatus);
            logEntry.setQuantity(quantity);
            logEntry.setResult("SUCCESS");
            logEntry.setCreateDate(new Date());
            operationLogDao.insert(logEntry);
        } catch (Exception e) {
            log.warn("Failed to write operation log: {}", e.getMessage());
        }
    }

    // --- 内部记录 ---

    private record RowResult(
            PdcNfcWriteResultRow row,
            PdcNfcWriteJobItemEntity item,
            PdcNfcAssetEntity asset,
            PdcNfcAssetStatus targetStatus,
            boolean fullyVerified
    ) {}
}
