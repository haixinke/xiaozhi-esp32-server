package xiaozhi.modules.dailynote.service;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.modules.dailynote.vo.DailyNoteImportResultVO;

/**
 * 小纸条 JSON 导入 Service。
 * 运营上传 UTF-8 JSON 数组文件，一次新增多日内容或按完整日期整项覆盖既有内容；
 * 任一条目无效时整批零写入，成功时返回新增数与覆盖数。
 */
public interface DailyNoteImportService {

    /**
     * 导入小纸条 JSON 文件（multipart 字段 file，UTF-8，.json，最大 1 MB，最多 1000 项）。
     * 校验全部通过后在单事务内写入；库内同日期整项覆盖（保留 id/创建人/创建时间），其余日期新增。
     *
     * @param file 上传的 JSON 文件
     * @return 新增数与覆盖数
     * @throws xiaozhi.common.exception.RenException                          文件级格式/资源问题（10405）
     * @throws xiaozhi.modules.dailynote.exception.DailyNoteImportException 条目级校验失败（10404 + 行号明细）
     */
    DailyNoteImportResultVO importNotes(MultipartFile file);
}
