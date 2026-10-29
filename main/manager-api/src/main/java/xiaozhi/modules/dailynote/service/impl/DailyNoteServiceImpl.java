package xiaozhi.modules.dailynote.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.dailynote.dao.DailyNoteDao;
import xiaozhi.modules.dailynote.dto.DailyNoteSaveDTO;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;
import xiaozhi.modules.dailynote.service.DailyNoteService;
import xiaozhi.modules.dailynote.vo.DailyNoteVO;

/**
 * 小纸条管理 Service 实现。
 * 小纸条为物理删除（无 deleted 列），读取点无需过滤删除标记。
 * 长度按 Unicode 字符数（codePointCount）而非 UTF-8 字节数校验；
 * 三个文本字段保存前统一去首尾空白，正文内部换行保留。
 */
@Service
@AllArgsConstructor
public class DailyNoteServiceImpl extends BaseServiceImpl<DailyNoteDao, DailyNoteEntity>
        implements DailyNoteService {

    /** 标题最大 Unicode 字符数 */
    static final int TITLE_MAX_LENGTH = 50;
    /** 正文最大 Unicode 字符数 */
    static final int CONTENT_MAX_LENGTH = 200;
    /** 节气最大 Unicode 字符数 */
    static final int SOLAR_TERM_MAX_LENGTH = 50;

    /**
     * 严格日期格式：uuuu-MM-dd + STRICT，拒绝 2026-02-30 之类不存在的日期；
     * 同时要求解析结果无剩余字符，拒绝 2026-1-1、2026/01/01 等非规范输入
     */
    private static final DateTimeFormatter STRICT_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public PageData<DailyNoteVO> page(Map<String, Object> params) {
        // 列表固定按日期倒序，剔除调用方可能传入的 orderField/order，避免客户端改写排序
        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.remove(Constant.ORDER_FIELD);
        pageParams.remove(Constant.ORDER);
        QueryWrapper<DailyNoteEntity> wrapper = new QueryWrapper<>();
        // 可选完整日期精确过滤；非法日期一律抛业务错误，不静默当作无过滤
        String noteDate = StringUtils.trimToNull((String) pageParams.get("noteDate"));
        wrapper.eq(noteDate != null, "note_date", noteDate == null ? null : toSqlDate(noteDate));
        IPage<DailyNoteEntity> page = baseDao.selectPage(getPage(pageParams, "note_date", false), wrapper);
        return new PageData<>(ConvertUtils.sourceToTarget(page.getRecords(), DailyNoteVO.class), page.getTotal());
    }

    @Override
    public DailyNoteVO get(Long id) {
        DailyNoteEntity entity = baseDao.selectById(id);
        if (entity == null) {
            throw new RenException(ErrorCode.DAILY_NOTE_NOT_FOUND);
        }
        return ConvertUtils.sourceToTarget(entity, DailyNoteVO.class);
    }

    @Override
    public void create(DailyNoteSaveDTO dto) {
        DailyNoteEntity entity = new DailyNoteEntity();
        fillEntity(entity, dto, null);
        baseDao.insert(entity);
    }

    @Override
    public void update(Long id, DailyNoteSaveDTO dto) {
        DailyNoteEntity entity = baseDao.selectById(id);
        if (entity == null) {
            throw new RenException(ErrorCode.DAILY_NOTE_NOT_FOUND);
        }
        fillEntity(entity, dto, id);
        baseDao.updateById(entity);
    }

    /** 物理删除（不可恢复）；行不存在时明确报错而不是静默成功 */
    @Override
    public void delete(Long id) {
        int rows = baseDao.deleteById(id);
        if (rows == 0) {
            throw new RenException(ErrorCode.DAILY_NOTE_NOT_FOUND);
        }
    }

    /**
     * 用 DTO 内容填充实体（新增与更新共用）。
     * 日期判重排除自身（excludeId）：编辑自身不视为冲突；
     * 业务校验先查后写，数据库 note_date 唯一约束兜底并发写入。
     */
    private void fillEntity(DailyNoteEntity entity, DailyNoteSaveDTO dto, Long excludeId) {
        Date noteDate = toSqlDate(dto.getNoteDate());
        QueryWrapper<DailyNoteEntity> conflict = new QueryWrapper<DailyNoteEntity>().eq("note_date", noteDate);
        if (excludeId != null) {
            conflict.ne("id", excludeId);
        }
        if (baseDao.selectCount(conflict) > 0) {
            throw new RenException(ErrorCode.DAILY_NOTE_DATE_CONFLICT);
        }
        entity.setNoteDate(noteDate);
        entity.setTitle(requireText(dto.getTitle(), TITLE_MAX_LENGTH, ErrorCode.DAILY_NOTE_TITLE_REQUIRED,
                ErrorCode.DAILY_NOTE_TITLE_TOO_LONG));
        entity.setContent(requireText(dto.getContent(), CONTENT_MAX_LENGTH, ErrorCode.DAILY_NOTE_CONTENT_REQUIRED,
                ErrorCode.DAILY_NOTE_CONTENT_TOO_LONG));
        entity.setSolarTerm(optionalText(dto.getSolarTerm(), SOLAR_TERM_MAX_LENGTH,
                ErrorCode.DAILY_NOTE_SOLAR_TERM_TOO_LONG));
    }

    /**
     * 解析严格 yyyy-MM-dd 的完整日期，非法或不存在（如 2 月 30 日）一律拒绝。
     * 解析走 LocalDate + STRICT 校验，再转 java.sql.Date（JDBC DATE 语义，不含时分秒）。
     */
    private Date toSqlDate(String raw) {
        String text = StringUtils.trimToNull(raw);
        if (text == null) {
            throw new RenException(ErrorCode.DAILY_NOTE_INVALID_DATE);
        }
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(text, STRICT_DATE_FORMATTER);
        } catch (Exception e) {
            throw new RenException(ErrorCode.DAILY_NOTE_INVALID_DATE);
        }
        try {
            // java.sql.Date.valueOf 走默认时区但只取日历日，符合"数据库 DATE 语义"
            return java.sql.Date.valueOf(localDate);
        } catch (Exception e) {
            throw new RenException(ErrorCode.DAILY_NOTE_INVALID_DATE);
        }
    }

    /** 必填文本：去首尾空白后必须非空且不超过 maxLength 个 Unicode 字符 */
    private String requireText(String raw, int maxLength, int requiredError, int tooLongError) {
        String text = StringUtils.trimToNull(raw);
        if (text == null) {
            throw new RenException(requiredError);
        }
        if (text.codePointCount(0, text.length()) > maxLength) {
            throw new RenException(tooLongError);
        }
        return text;
    }

    /** 可选文本：去首尾空白后可为空（存 null），非空时不超过 maxLength 个 Unicode 字符 */
    private String optionalText(String raw, int maxLength, int tooLongError) {
        String text = StringUtils.trimToNull(raw);
        if (text == null) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > maxLength) {
            throw new RenException(tooLongError);
        }
        return text;
    }
}
