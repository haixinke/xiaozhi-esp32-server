package xiaozhi.modules.smallobject.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.smallobject.entity.SmallObjectEntity;
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
}