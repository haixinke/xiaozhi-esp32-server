package xiaozhi.modules.storyengine.service;

import xiaozhi.common.service.BaseService;
import xiaozhi.modules.storyengine.dto.BigSceneDTO;
import xiaozhi.modules.storyengine.entity.BigSceneEntity;
import xiaozhi.modules.storyengine.vo.BigSceneVO;

import java.util.List;

public interface StoryBigSceneService extends BaseService<BigSceneEntity> {

    /**
     * 查询全部大场景，按排序序号升序。
     */
    List<BigSceneVO> listAll();

    void save(BigSceneDTO dto);

    void update(BigSceneDTO dto);

    /**
     * 删除大场景，并级联删除其下所有小场景、动作与动作图片（含OSS文件）。
     */
    void delete(String id);
}
