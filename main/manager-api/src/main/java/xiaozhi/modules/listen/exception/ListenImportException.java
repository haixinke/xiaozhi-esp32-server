package xiaozhi.modules.listen.exception;

import java.util.List;

import xiaozhi.common.exception.RenException;
import xiaozhi.modules.listen.vo.ListenImportConflictVO;

/**
 * 题库导入判重冲突异常：携带完整冲突明细列表。
 * RenException 本身不带 data 载荷，而判重冲突要求把「哪类、哪组/哪题、冲突原因」逐条下发给前端弹窗，
 * 故扩展 RenException 附加 conflicts 字段，由 RenExceptionHandler 专门处理（code=10373，data=明细列表）。
 */
public class ListenImportException extends RenException {

    /**
     * 冲突明细列表（组名重复 / 题干重复逐条记录）
     */
    private final List<ListenImportConflictVO> conflicts;

    public ListenImportException(int code, List<ListenImportConflictVO> conflicts) {
        super(code);
        this.conflicts = conflicts;
    }

    public List<ListenImportConflictVO> getConflicts() {
        return conflicts;
    }
}
