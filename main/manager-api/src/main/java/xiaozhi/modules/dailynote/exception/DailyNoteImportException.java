package xiaozhi.modules.dailynote.exception;

import java.util.List;

import xiaozhi.common.exception.RenException;
import xiaozhi.modules.dailynote.vo.DailyNoteImportErrorVO;

/**
 * 小纸条导入校验失败异常：携带逐行错误明细（行号、字段、原因）。
 * RenException 本身不带 data 载荷，而导入校验要求把全部错误一次返回给前端列表展示，
 * 故扩展 RenException 附加 errors 字段，由 RenExceptionHandler 专门处理（code=10404，data=明细列表）。
 * 任一错误存在时整批零写入。
 */
public class DailyNoteImportException extends RenException {

    /**
     * 逐行错误明细列表
     */
    private final List<DailyNoteImportErrorVO> errors;

    public DailyNoteImportException(int code, List<DailyNoteImportErrorVO> errors) {
        super(code);
        this.errors = errors;
    }

    public List<DailyNoteImportErrorVO> getErrors() {
        return errors;
    }
}
