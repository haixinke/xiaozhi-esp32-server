package xiaozhi.modules.pdc.nfc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcAssetDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobDao;
import xiaozhi.modules.pdc.nfc.dao.PdcNfcWriteJobItemDao;
import xiaozhi.modules.pdc.nfc.dto.PdcNfcWriteResultRow;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobItemEntity;
import xiaozhi.modules.pdc.nfc.service.PdcNfcSensitiveTextGuard;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteCsvExporter;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultImporter;
import xiaozhi.modules.pdc.nfc.service.PdcNfcWriteResultTransactionService;
import xiaozhi.modules.pdc.nfc.service.ValidatedWriteResult;
import xiaozhi.modules.pdc.nfc.vo.PdcNfcWriteImportVO;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.SCHEME_GENERATED;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.VERIFIED;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.WRITTEN;

/**
 * 完整读取并预检工厂写卡结果；所有数据库写入委托给独立事务 Bean。
 */
@Service
@RequiredArgsConstructor
public class PdcNfcWriteResultImporterImpl implements PdcNfcWriteResultImporter {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_LINE_LENGTH = 4096;
    private static final int MAX_ROWS = 10_000;
    private static final int RESULT_COLUMN_COUNT = 14;
    private static final int EXPECTED_NDEF_RECORD_COUNT = 2;
    private static final String EXPECTED_AAR_PACKAGE = "com.tencent.mm";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";
    private static final String VERIFY_SKIPPED = "SKIPPED";
    private static final Set<String> WRITE_RESULTS = Set.of("SUCCESS", "FAILURE");
    private static final Set<String> VERIFY_RESULTS =
            Set.of("SUCCESS", "FAILURE", "SKIPPED");

    public static final String RESULT_FORMAT_VERSION = "PDC_NFC_RESULT_V1";
    public static final String EXPECTED_HEADER =
            "format_version,job_no,asset_no,wechat_sn,write_result,verify_result,"
                    + "tag_uid,ndef_record_count,uri_sha256,aar_package,is_read_only,"
                    + "written_at,error_code,error_message";

    private final PdcNfcWriteJobDao jobDao;
    private final PdcNfcWriteJobItemDao jobItemDao;
    private final PdcNfcAssetDao assetDao;
    private final PdcNfcWriteResultTransactionService transactionService;

    @Override
    public PdcNfcWriteImportVO importResult(
            Long jobId, UUID requestId, MultipartFile file, Long operatorId) {
        validateRequest(jobId, requestId, file, operatorId);
        byte[] fileBytes = readFileBytes(file);
        validateUtf8(fileBytes);
        String resultFileSha256 = PdcNfcWriteCsvExporter.sha256Hex(fileBytes);

        String content = stripBom(new String(fileBytes, StandardCharsets.UTF_8));
        List<String> lines = splitLines(content);
        validateHeader(lines);
        List<PdcNfcWriteResultRow> rows = parseRows(lines);
        validateDuplicateRows(rows);

        PdcNfcWriteJobEntity job = loadAndValidateJob(jobId);
        List<PdcNfcWriteJobItemEntity> items = loadAndValidateItems(jobId, rows.size());
        validateDeclaredCounts(job, items, rows.size());
        Map<String, PdcNfcWriteJobItemEntity> itemsByKey = indexItems(items);
        Map<Long, PdcNfcAssetEntity> assetsById = loadAssets(items);

        List<ValidatedWriteResult> validatedRows = rows.stream()
                .map(row -> validateRow(row, job, itemsByKey, assetsById))
                .toList();
        if (validatedRows.size() != itemsByKey.size()) {
            throw contentMismatch();
        }

        return transactionService.apply(
                jobId,
                List.copyOf(validatedRows),
                resultFileSha256,
                operatorId,
                requestId);
    }

    private void validateRequest(
            Long jobId, UUID requestId, MultipartFile file, Long operatorId) {
        if (jobId == null || requestId == null || operatorId == null
                || file == null || file.isEmpty()
                || file.getSize() > MAX_FILE_SIZE
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw formatError();
        }
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw formatError();
        }
    }

    private void validateUtf8(byte[] fileBytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(fileBytes));
        } catch (CharacterCodingException exception) {
            throw formatError();
        }
    }

    private String stripBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private List<String> splitLines(String content) {
        return Arrays.stream(content.replace("\r\n", "\n").replace('\r', '\n')
                        .split("\n", -1))
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private void validateHeader(List<String> lines) {
        if (lines.isEmpty()
                || !EXPECTED_HEADER.equals(String.join(
                        ",", parseCsvFields(lines.getFirst())))) {
            throw formatError();
        }
    }

    private List<PdcNfcWriteResultRow> parseRows(List<String> lines) {
        if (lines.size() <= 1 || lines.size() - 1 > MAX_ROWS) {
            throw formatError();
        }
        List<PdcNfcWriteResultRow> rows = new ArrayList<>(lines.size() - 1);
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.length() > MAX_LINE_LENGTH) {
                throw formatError();
            }
            rows.add(parseRow(line));
        }
        return List.copyOf(rows);
    }

    private PdcNfcWriteResultRow parseRow(String line) {
        List<String> fields = parseCsvFields(line);
        if (fields.size() != RESULT_COLUMN_COUNT
                || !RESULT_FORMAT_VERSION.equals(fields.get(0))
                || anyBlank(fields, 1, 2, 3, 4, 5)) {
            throw formatError();
        }
        // 自由文本字段在任何数据库访问前拒绝敏感明文
        PdcNfcSensitiveTextGuard.requireNoSchemeLeakage(fields.get(12));
        PdcNfcSensitiveTextGuard.requireNoSchemeLeakage(fields.get(13));

        String writeResult = fields.get(4);
        String verifyResult = fields.get(5);
        if (!WRITE_RESULTS.contains(writeResult)
                || !VERIFY_RESULTS.contains(verifyResult)
                || fields.get(6).length() > 128
                || (!fields.get(8).isEmpty()
                        && !fields.get(8).matches("[0-9a-fA-F]{64}"))
                || fields.get(9).length() > 128
                || fields.get(12).length() > 64
                || fields.get(13).length() > 512) {
            throw formatError();
        }
        if (RESULT_FAILURE.equals(writeResult)
                && RESULT_SUCCESS.equals(verifyResult)) {
            // 写入失败但验证成功的矛盾结果
            throw formatError();
        }

        return new PdcNfcWriteResultRow(
                fields.get(0),
                fields.get(1),
                fields.get(2),
                fields.get(3),
                writeResult,
                verifyResult,
                fields.get(6),
                parseOptionalInteger(fields.get(7)),
                blankToNull(fields.get(8).toLowerCase(Locale.ROOT)),
                fields.get(9),
                parseOptionalBoolean(fields.get(10)),
                parseOptionalWrittenAt(fields.get(11)),
                fields.get(12),
                fields.get(13)
        );
    }

    private boolean anyBlank(List<String> fields, int... indexes) {
        return Arrays.stream(indexes).anyMatch(index -> fields.get(index).isBlank());
    }

    private String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private Integer parseOptionalInteger(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw formatError();
        }
    }

    private Boolean parseOptionalBoolean(String value) {
        if (value.isEmpty()) {
            return null;
        }
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw formatError();
    }

    private LocalDateTime parseOptionalWrittenAt(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeException exception) {
            throw formatError();
        }
    }

    private void validateDuplicateRows(List<PdcNfcWriteResultRow> rows) {
        Set<String> keys = new HashSet<>();
        for (PdcNfcWriteResultRow row : rows) {
            if (!keys.add(key(row.assetNo(), row.wechatSn()))) {
                throw formatError();
            }
        }
    }

    private PdcNfcWriteJobEntity loadAndValidateJob(Long jobId) {
        PdcNfcWriteJobEntity job = jobDao.selectById(jobId);
        if (job == null) {
            throw new RenException(ErrorCode.PDC_NFC_JOB_NOT_FOUND);
        }
        if (!PdcNfcWriteJobStatus.EXPORTED.name().equals(job.getStatus())
                && !PdcNfcWriteJobStatus.RESULT_IMPORTED.name().equals(job.getStatus())) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
        return job;
    }

    private List<PdcNfcWriteJobItemEntity> loadAndValidateItems(
            Long jobId, int resultRowCount) {
        List<PdcNfcWriteJobItemEntity> items = jobItemDao.selectList(
                new LambdaQueryWrapper<PdcNfcWriteJobItemEntity>()
                        .eq(PdcNfcWriteJobItemEntity::getJobId, jobId)
                        .orderByAsc(PdcNfcWriteJobItemEntity::getSequenceNo));
        if (items == null || items.size() != resultRowCount) {
            throw contentMismatch();
        }
        return List.copyOf(items);
    }

    /**
     * 上传行数、快照条数、job.total_count 与 job.row_count 必须一致，
     * 且快照 sequence_no 必须恰好是 1..N。
     */
    private void validateDeclaredCounts(
            PdcNfcWriteJobEntity job,
            List<PdcNfcWriteJobItemEntity> items,
            int resultRowCount) {
        if (job.getTotalCount() == null || job.getRowCount() == null
                || job.getTotalCount() != resultRowCount
                || job.getRowCount() != resultRowCount) {
            throw contentMismatch();
        }
        for (int index = 0; index < items.size(); index++) {
            Integer sequenceNo = items.get(index).getSequenceNo();
            if (sequenceNo == null || sequenceNo != index + 1) {
                throw contentMismatch();
            }
        }
    }

    private Map<String, PdcNfcWriteJobItemEntity> indexItems(
            List<PdcNfcWriteJobItemEntity> items) {
        Map<String, PdcNfcWriteJobItemEntity> indexed = new LinkedHashMap<>();
        for (PdcNfcWriteJobItemEntity item : items) {
            if (item == null || item.getAssetId() == null
                    || item.getAssetNo() == null || item.getWechatSn() == null
                    || item.getUriSha256() == null
                    || indexed.put(
                            key(item.getAssetNo(), item.getWechatSn()), item) != null) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        }
        return Map.copyOf(indexed);
    }

    private Map<Long, PdcNfcAssetEntity> loadAssets(
            List<PdcNfcWriteJobItemEntity> items) {
        List<Long> ids = items.stream()
                .map(PdcNfcWriteJobItemEntity::getAssetId)
                .toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
        }
        List<PdcNfcAssetEntity> assets = assetDao.selectBatchIds(ids);
        if (assets == null || assets.size() != ids.size()) {
            throw new RenException(ErrorCode.PDC_NFC_ASSET_NOT_FOUND);
        }
        Map<Long, PdcNfcAssetEntity> indexed = new HashMap<>();
        for (PdcNfcAssetEntity asset : assets) {
            if (asset == null || asset.getId() == null
                    || !ids.contains(asset.getId())
                    || indexed.put(asset.getId(), asset) != null) {
                throw new RenException(ErrorCode.PDC_NFC_INVALID_STATE);
            }
        }
        return Map.copyOf(indexed);
    }

    private ValidatedWriteResult validateRow(
            PdcNfcWriteResultRow row,
            PdcNfcWriteJobEntity job,
            Map<String, PdcNfcWriteJobItemEntity> itemsByKey,
            Map<Long, PdcNfcAssetEntity> assetsById) {
        if (!job.getJobNo().equals(row.jobNo())) {
            throw contentMismatch();
        }
        PdcNfcWriteJobItemEntity item =
                itemsByKey.get(key(row.assetNo(), row.wechatSn()));
        if (item == null) {
            throw contentMismatch();
        }
        PdcNfcAssetEntity asset = assetsById.get(item.getAssetId());
        if (asset == null || !row.assetNo().equals(asset.getAssetNo())) {
            throw contentMismatch();
        }

        boolean writeSucceeded = RESULT_SUCCESS.equals(row.writeResult());
        boolean verifySucceeded = RESULT_SUCCESS.equals(row.verifyResult());
        if (writeSucceeded && row.writtenAt() == null) {
            throw formatError();
        }

        boolean fullyVerified = writeSucceeded && verifySucceeded;
        if (fullyVerified) {
            // 完全成功必须携带完整且一致的完整性证据
            if (row.uriSha256() == null
                    || !row.uriSha256().equalsIgnoreCase(item.getUriSha256())
                    || !EXPECTED_AAR_PACKAGE.equals(row.aarPackage())
                    || row.ndefRecordCount() == null
                    || row.ndefRecordCount() != EXPECTED_NDEF_RECORD_COUNT
                    || !Boolean.TRUE.equals(row.isReadOnly())) {
                throw contentMismatch();
            }
        } else if (row.uriSha256() != null
                && !row.uriSha256().equalsIgnoreCase(item.getUriSha256())) {
            // 提供的验证证据与快照不一致时同样拒绝
            throw contentMismatch();
        }

        PdcNfcAssetStatus targetStatus = fullyVerified
                ? VERIFIED
                : (writeSucceeded ? WRITTEN : SCHEME_GENERATED);
        return new ValidatedWriteResult(
                row, item, asset, targetStatus, fullyVerified);
    }

    private String key(String assetNo, String wechatSn) {
        return assetNo + '\u0000' + wechatSn;
    }

    public static List<String> parseCsvFields(String line) {
        List<String> fields = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            if (line.charAt(index) == '"') {
                StringBuilder value = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < line.length()) {
                    char current = line.charAt(index++);
                    if (current != '"') {
                        value.append(current);
                    } else if (index < line.length() && line.charAt(index) == '"') {
                        value.append('"');
                        index++;
                    } else {
                        closed = true;
                        break;
                    }
                }
                if (!closed || (index < line.length() && line.charAt(index) != ',')) {
                    throw formatError();
                }
                fields.add(value.toString());
            } else {
                int start = index;
                while (index < line.length() && line.charAt(index) != ',') {
                    if (line.charAt(index) == '"') {
                        throw formatError();
                    }
                    index++;
                }
                fields.add(line.substring(start, index));
            }
            if (index < line.length() && line.charAt(index) == ',') {
                index++;
                if (index == line.length()) {
                    fields.add("");
                }
            }
        }
        if (line.isEmpty()) {
            fields.add("");
        }
        return List.copyOf(fields);
    }

    private static RenException formatError() {
        return new RenException(ErrorCode.PDC_NFC_CSV_FORMAT_ERROR);
    }

    private static RenException contentMismatch() {
        return new RenException(ErrorCode.PDC_NFC_CSV_CONTENT_MISMATCH);
    }
}
