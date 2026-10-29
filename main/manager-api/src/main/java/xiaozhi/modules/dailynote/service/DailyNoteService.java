package xiaozhi.modules.dailynote.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.dailynote.dto.DailyNoteSaveDTO;
import xiaozhi.modules.dailynote.vo.DailyNoteVO;

/**
 * 小纸条管理 Service（运营管理菜单下的「小纸条管理」）。
 * 小纸条是纯运营的每日内容：一个完整日期只有一份，手工保存拒绝重复日期，
 * 删除为物理删除（无软删、无回收站）。
 */
public interface DailyNoteService {

    /**
     * 分页查询：默认按日期倒序，可选 noteDate（yyyy-MM-dd）完整日期精确过滤
     */
    PageData<DailyNoteVO> page(Map<String, Object> params);

    /**
     * 按 ID 查询详情；不存在抛 DAILY_NOTE_NOT_FOUND
     */
    DailyNoteVO get(Long id);

    /**
     * 新增：三个文本字段去首尾空白后校验，目标日期已存在时拒绝
     */
    void create(DailyNoteSaveDTO dto);

    /**
     * 编辑：同新增校验；目标日期与其他记录冲突时拒绝（编辑自身不算冲突）
     */
    void update(Long id, DailyNoteSaveDTO dto);

    /**
     * 按 ID 物理删除（不可恢复）；不存在抛 DAILY_NOTE_NOT_FOUND
     */
    void delete(Long id);
}
