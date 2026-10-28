package xiaozhi.modules.listen.service;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.modules.listen.vo.ListenImportResultVO;

/**
 * 题库 JSON 增量导入 Service。
 * 导入语义（spec 已定）：
 * <ul>
 * <li>增量：JSON 内组 id / 题 id 不入库，库内 id 后端生成；只解析最外层大类 id 做字典校验</li>
 * <li>判重整单失败制：同大类下组名重复、同组内题干重复（含与库内比对、含文件内自查），
 * 任一冲突整个文件拒绝并返回冲突明细，单事务零写入</li>
 * <li>order 追加：新组 sortOrder = 该大类现有 max+1 起递增；组内新题从 1 起（新组）。
 * JSON 内 order 只用于同次导入内相对排序</li>
 * <li>结构校验：JSON 非法、每组非 5 题、每题非 5 选项、缺字段 → 拒绝</li>
 * </ul>
 */
public interface ListenImportService {

    /**
     * 导入题库 JSON 文件。
     *
     * @param file multipart 上传的 JSON 文件（结构：大类数组 → groups → questions → options）
     * @return 写入统计（N 组 M 题）
     * @throws xiaozhi.common.exception.RenException                          大类不存在（10372）/ 格式非法（10374）
     * @throws xiaozhi.modules.listen.exception.ListenImportException 判重冲突（10373），携带冲突明细
     */
    ListenImportResultVO importBank(MultipartFile file);
}
