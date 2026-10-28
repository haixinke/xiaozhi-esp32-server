package xiaozhi.modules.listen.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.vo.ListenGroupDetailVO;
import xiaozhi.modules.listen.vo.ListenGroupVO;

/**
 * 题目分组 Service。
 * 本期只读：题组分页与组详情；导入/编辑/删除在后续 ticket 落地。
 */
public interface ListenGroupService extends BaseService<ListenGroupEntity> {

    /**
     * 分页查询题组列表（按 sort_order 升序），带组内题数。
     * params.categoryCode 非空时按大类过滤；大类名由字典 LISTEN_CATEGORY 实时翻译，
     * 翻译不到时回落原始 code。
     *
     * @param params 查询参数，包含分页信息与可选 categoryCode
     * @return 分页的题组数据
     */
    PageData<ListenGroupVO> page(Map<String, Object> params);

    /**
     * 组详情：分组本身 + 组内全部题目（按 sort_order 升序），
     * 每题的 options_json 解析为 key/text 选项列表后下发。
     * 组不存在时抛「题目分组不存在」（题库物理删除，无软删语义可区分）。
     *
     * @param id 分组 ID
     * @return 组详情 VO
     */
    ListenGroupDetailVO getDetail(Long id);
}
