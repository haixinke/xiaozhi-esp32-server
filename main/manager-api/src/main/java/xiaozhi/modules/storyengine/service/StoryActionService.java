package xiaozhi.modules.storyengine.service;

import xiaozhi.common.service.BaseService;
import xiaozhi.modules.storyengine.dto.ActionDTO;
import xiaozhi.modules.storyengine.entity.ActionEntity;
import xiaozhi.modules.storyengine.vo.ActionVO;

import java.util.List;

public interface StoryActionService extends BaseService<ActionEntity> {

    /**
     * 查询指定小场景下的动作列表，每个动作携带其图片（按原型+时段+序号排序）。
     */
    List<ActionVO> listBySmallSceneId(String smallSceneId);

    void save(ActionDTO dto);

    void update(ActionDTO dto);

    /**
     * 删除动作，并级联删除其图片记录与OSS文件。
     */
    void delete(String id);
}
