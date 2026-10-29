package xiaozhi.modules.dailynote.service.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.exception.DailyNoteImportException;
import xiaozhi.modules.dailynote.service.DailyNoteImportService;
import xiaozhi.modules.dailynote.vo.DailyNoteImportErrorVO;
import xiaozhi.modules.dailynote.vo.DailyNoteImportResultVO;

/**
 * 小纸条 JSON 导入实现。
 * 解析顺序：文件级限制（空/扩展名/1MB）→ UTF-8 解码 → JSON 解析 → 逐行校验（含文件内日期查重）
 * → 单事务写入（同日期整项覆盖，其余新增）。
 * 任何校验失败都整批拒绝：文件级问题抛 RenException(10405)，条目级问题汇总行号明细后抛
 * DailyNoteImportException(10404)；写入期唯一键冲突说明并发撞日期，转换为明确业务错误（10403）
 * 并由事务回滚保证零写入，不向运营泄露 SQL 或堆栈。
 * 与题库导入的差异：题库是“冲突拒绝”，小纸条对库内同日期是“整项覆盖”（spec 明确仅复用
 * 上传/校验/事务/结果反馈方式，不复用增量拒绝语义）。
 */
@Service
@AllArgsConstructor
public class DailyNoteImportServiceImpl implements DailyNoteImportService {

    /** 文件大小上限：1 MB（spec 限定上传规模受控） */
    private static final long MAX_FILE_BYTES = 1024 * 1024L;
    /** 条目数上限：1000（spec 限定处理规模受控） */
    private static final int MAX_ITEMS = 1000;
    /** 允许的字段白名单：除这四个字段外一律整批拒绝（不接受别名/嵌套/元数据） */
    private static final Set<String> ALLOWED_FIELDS = Set.of("date", "title", "content", "solarTerm");

    /** 与手工保存一致的长度上限（Unicode 字符数），直接复用避免两处漂移 */
    private static final int TITLE_MAX_LENGTH = DailyNoteServiceImpl.TITLE_MAX_LENGTH;
    private static final int CONTENT_MAX_LENGTH = DailyNoteServiceImpl.CONTENT_MAX_LENGTH;
    private static final int SOLAR_TERM_MAX_LENGTH = DailyNoteServiceImpl.SOLAR_TERM_MAX_LENGTH;

    /** 手工保存的严格日期解析器（uuuu-MM-dd + STRICT），导入与手工保存同一规则 */
    private static final DateTimeFormatter STRICT_DATE_FORMATTER = DailyNoteServiceImpl.STRICT_DATE_FORMATTER;

    /** 库内日期键格式化：与文件内 dateKey（yyyy-MM-dd）对齐，用于同日期覆盖判定 */
    private static final java.text.SimpleDateFormat DATE_KEY_FORMAT = new java.text.SimpleDateFormat("yyyy-MM-dd");

    /** 解析导入文件用的共享 ObjectMapper（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DailyNoteDao dailyNoteDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyNoteImportResultVO importNotes(MultipartFile file) {
        JsonNode items = parseFile(file);
        List<ValidatedItem> validated = validateItems(items);
        return writeAll(validated);
    }

    /** 文件级限制与解析：空文件、扩展名、大小、UTF-8、JSON 合法性、顶层非空数组、条数上限 */
    private JsonNode parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".json")) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "仅支持 .json 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "文件大小不能超过 1 MB");
        }
        // 严格 UTF-8 解码：非 UTF-8（如 GBK）内容直接整批拒绝，不允许替换字符静默入库
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes())).toString();
        } catch (CharacterCodingException e) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "文件必须是 UTF-8 编码");
        } catch (IOException e) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "文件读取失败");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "JSON 解析失败");
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "顶层必须是非空数组");
        }
        if (root.size() > MAX_ITEMS) {
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_INVALID_FORMAT, "最多导入 1000 条");
        }
        return root;
    }

    /**
     * 逐行校验：汇总全部错误（行号从 1 开始、字段、原因）后一次性抛出，整批零写入。
     * 校验规则与手工保存一致：日期严格 yyyy-MM-dd 且真实存在，标题/正文必填去首尾空白，
     * 长度按 Unicode 字符数，节气可选任意文案；未知字段与文件内重复日期同样在此收集。
     */
    private List<ValidatedItem> validateItems(JsonNode items) {
        List<DailyNoteImportErrorVO> errors = new ArrayList<>();
        List<ValidatedItem> validated = new ArrayList<>();
        Map<String, Integer> firstRowOfDate = new HashMap<>();
        int row = 0;
        for (JsonNode item : items) {
            row++;
            ValidatedItem valid = validateItem(item, row, errors);
            if (valid != null) {
                // 文件内相同日期整批拒绝，不采用第一条或最后一条
                Integer firstRow = firstRowOfDate.putIfAbsent(valid.dateKey(), row);
                if (firstRow != null) {
                    errors.add(error(row, "date", "文件内与第 " + firstRow + " 行日期重复"));
                } else {
                    validated.add(valid);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new DailyNoteImportException(ErrorCode.DAILY_NOTE_IMPORT_INVALID, errors);
        }
        return validated;
    }

    /** 校验单个条目；全部通过返回 ValidatedItem，否则把错误追加到 errors 并返回 null */
    private ValidatedItem validateItem(JsonNode item, int row, List<DailyNoteImportErrorVO> errors) {
        int errorCountBefore = errors.size();
        if (!item.isObject()) {
            errors.add(error(row, null, "每项必须是对象"));
            return null;
        }
        // 字段白名单：未知字段整批拒绝（拼写错误不会静默入库）
        List<String> unknownFields = new ArrayList<>();
        item.fieldNames().forEachRemaining(name -> {
            if (!ALLOWED_FIELDS.contains(name)) {
                unknownFields.add(name);
            }
        });
        for (String name : unknownFields) {
            errors.add(error(row, name, "未知字段"));
        }

        LocalDate date = requireDate(item, row, errors);
        String title = requireText(item, row, "title", TITLE_MAX_LENGTH, errors);
        String content = requireText(item, row, "content", CONTENT_MAX_LENGTH, errors);
        String solarTerm = optionalText(item, row, "solarTerm", SOLAR_TERM_MAX_LENGTH, errors);

        if (errors.size() > errorCountBefore) {
            return null;
        }
        return new ValidatedItem(date.toString(), java.sql.Date.valueOf(date), title, content, solarTerm);
    }

    /** 日期：必填字符串，严格 yyyy-MM-dd 且为真实存在的完整日期 */
    private LocalDate requireDate(JsonNode item, int row, List<DailyNoteImportErrorVO> errors) {
        String raw = textOrNull(item.path("date"));
        if (raw == null) {
            errors.add(error(row, "date", "日期必填且必须是字符串"));
            return null;
        }
        try {
            return LocalDate.parse(raw, STRICT_DATE_FORMATTER);
        } catch (Exception e) {
            errors.add(error(row, "date", "日期必须是合法的完整年月日(yyyy-MM-dd)"));
            return null;
        }
    }

    /** 必填文本：去首尾空白后非空且不超过 maxLength 个 Unicode 字符 */
    private String requireText(JsonNode item, int row, String field, int maxLength,
            List<DailyNoteImportErrorVO> errors) {
        String raw = textOrNull(item.path(field));
        if (raw == null) {
            errors.add(error(row, field, "不能为空"));
            return null;
        }
        if (raw.codePointCount(0, raw.length()) > maxLength) {
            errors.add(error(row, field, "最多 " + maxLength + " 字"));
            return null;
        }
        return raw;
    }

    /** 可选文本：缺失或 null 存 null；去首尾空白后非空时不超过 maxLength 个 Unicode 字符 */
    private String optionalText(JsonNode item, int row, String field, int maxLength,
            List<DailyNoteImportErrorVO> errors) {
        JsonNode node = item.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String raw = textOrNull(node);
        if (raw == null) {
            // 类型错误（非字符串）或纯空白：纯空白按未填处理，类型错误需报出
            if (!node.isTextual()) {
                errors.add(error(row, field, "必须是字符串或 null"));
                return null;
            }
            return null;
        }
        if (raw.codePointCount(0, raw.length()) > maxLength) {
            errors.add(error(row, field, "最多 " + maxLength + " 字"));
            return null;
        }
        return raw;
    }

    /** 取文本字段并去首尾空白；非字符串返回 null */
    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return null;
        }
        return StringUtils.trimToNull(node.asText());
    }

    /**
     * 单事务写入：先一次性查出文件涉及的库内同日期记录（避免逐条查库），
     * 同日期整项覆盖内容字段（保留 id、creator、create_date，updater/update_date 由填充器刷新），
     * 其余日期新增；不触碰任何已打开记录。
     * 并发下唯一键冲突（另一导入/手工保存撞日期）转换为明确业务错误，事务回滚整批零写入。
     */
    private DailyNoteImportResultVO writeAll(List<ValidatedItem> validated) {
        Set<String> dateKeys = new HashSet<>();
        for (ValidatedItem item : validated) {
            dateKeys.add(item.dateKey());
        }
        Map<String, DailyNoteEntity> existingByDate = new HashMap<>();
        if (!dateKeys.isEmpty()) {
            List<DailyNoteEntity> existing = dailyNoteDao.selectList(
                    new QueryWrapper<DailyNoteEntity>().in("note_date", dateKeys));
            for (DailyNoteEntity entity : existing) {
                // noteDate 是 java.util.Date，toString() 返回 "Thu Feb 01 ..." 而非 yyyy-MM-dd；
                // 必须用 SQL DATE 语义格式化，否则与文件内日期键对不上，同日期会被误判为新增
                existingByDate.put(DATE_KEY_FORMAT.format(entity.getNoteDate()), entity);
            }
        }

        int addedCount = 0;
        int overwrittenCount = 0;
        try {
            for (ValidatedItem item : validated) {
                DailyNoteEntity existing = existingByDate.get(item.dateKey());
                if (existing != null) {
                    // 整项覆盖：用 UpdateWrapper 显式 SET，允许把节气从有值覆盖为 null
                    // （updateById 默认忽略 null 字段，达不到"整项覆盖"语义）
                    dailyNoteDao.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DailyNoteEntity>()
                            .eq("id", existing.getId())
                            .set("title", item.title())
                            .set("content", item.content())
                            .set("solar_term", item.solarTerm()));
                    overwrittenCount++;
                } else {
                    DailyNoteEntity entity = new DailyNoteEntity();
                    entity.setNoteDate(item.noteDate());
                    entity.setTitle(item.title());
                    entity.setContent(item.content());
                    entity.setSolarTerm(item.solarTerm());
                    dailyNoteDao.insert(entity);
                    addedCount++;
                }
            }
            // 无需显式 flush：逐条 insert/update 在 SIMPLE executor 下立即执行，
            // 并发撞日期唯一键冲突在写入点同步抛出，由下方 catch 转换为业务错误
        } catch (DuplicateKeyException e) {
            // 并发撞日期唯一键：转换为明确业务错误（不泄露 SQL/堆栈），事务回滚零写入
            throw new RenException(ErrorCode.DAILY_NOTE_IMPORT_CONFLICT, e);
        }

        DailyNoteImportResultVO result = new DailyNoteImportResultVO();
        result.setAddedCount(addedCount);
        result.setOverwrittenCount(overwrittenCount);
        return result;
    }

    private DailyNoteImportErrorVO error(int row, String field, String reason) {
        DailyNoteImportErrorVO vo = new DailyNoteImportErrorVO();
        vo.setRow(row);
        vo.setField(field);
        vo.setReason(reason);
        return vo;
    }

    /** 校验通过的条目（dateKey 为 yyyy-MM-dd 字符串，用于查重与库内比对） */
    private record ValidatedItem(String dateKey, java.sql.Date noteDate, String title, String content,
            String solarTerm) {
    }
}
