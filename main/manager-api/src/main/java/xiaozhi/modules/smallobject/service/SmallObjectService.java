package xiaozhi.modules.smallobject.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.smallobject.dto.SmallObjectDTO;
import xiaozhi.modules.smallobject.entity.SmallObjectEntity;
import xiaozhi.modules.smallobject.vo.SmallObjectTypeVO;
import xiaozhi.modules.smallobject.vo.SmallObjectVO;

/**
 * 小物件 Service
 */
public interface SmallObjectService extends BaseService<SmallObjectEntity> {

    /**
     * 分页查询小物件列表（只含未删除记录，按物件类型的字典 sort 升序）
     *
     * @param params 查询参数，包含分页信息
     * @return 分页的小物件数据
     */
    PageData<SmallObjectVO> page(Map<String, Object> params);

    /**
     * 根据 ID 获取小物件详情（已删除视为不存在）
     *
     * @param id 小物件 ID
     * @return 小物件 VO
     */
    SmallObjectVO get(Long id);

    /**
     * 新增小物件。
     * 服务端完整校验：类型必须在 SMALL_OBJECT_TYPE 字典中存在、该类型在未删除记录中未被占用、
     * 图片与故事非空且故事不超过 2000 字。字段级的必填/长度约束由 DTO 注解承担，
     * 这里再校验一次是因为 Service 是唯一的安全边界（前端校验只影响体验）。
     *
     * @param dto 新增请求
     */
    void save(SmallObjectDTO dto);

    /**
     * 更新小物件（全量更新类型、图片、故事）。
     * 记录不存在或已软删时抛「物件不存在」，不做静默成功；
     * 类型唯一性校验排除自身 id——保留原类型不算重复占用。
     *
     * @param dto 更新请求，id 必填
     */
    void update(SmallObjectDTO dto);

    /**
     * 仅更新启用/停用状态。
     * 独立于 update 是刻意的：状态切换只提交 id 与目标状态，不读写图片与故事，
     * 避免并发下用陈旧副本覆盖他人刚改过的故事内容。
     *
     * @param id     小物件 ID
     * @param status 目标状态：1 启用，0 停用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 查询可选物件类型列表，供新增弹窗的类型下拉使用。
     * 每个字典小类返回 key、显示名与是否已被未删除物件占用。
     *
     * @return 物件类型列表，顺序与字典管理页一致
     */
    List<SmallObjectTypeVO> listObjectTypes();

    /**
     * 删除小物件（软删：仅置位 deleted=1，行保留在库中）。
     * 删除语义为「已删除 = 不存在」：对不存在或已软删的 id 返回物件不存在，不做幂等成功。
     *
     * @param id 小物件 ID
     */
    void delete(Long id);
}