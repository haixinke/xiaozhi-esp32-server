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
     * 查询可选物件类型列表，供新增弹窗的类型下拉使用。
     * 每个字典小类返回 key、显示名与是否已被未删除物件占用。
     *
     * @return 物件类型列表，顺序与字典管理页一致
     */
    List<SmallObjectTypeVO> listObjectTypes();
}