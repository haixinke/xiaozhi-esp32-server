package xiaozhi.modules.storyengine.service;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.storyengine.vo.PetStoryHistoryVO;
import xiaozhi.modules.storyengine.vo.PetStoryStateVO;

import java.util.Map;

/**
 * 宠物故事只读查询服务。按宠物解析其原型，读取该原型的共享状态与历史。
 */
public interface PetStoryQueryService {

    /**
     * 查询宠物所属原型的共享当前状态。未破壳或无 ACTIVE 状态时返回 null。
     */
    PetStoryStateVO getCurrent(Long userId, String petId);

    /**
     * 分页查询宠物所属原型的共享历史。未破壳返回空分页。
     */
    PageData<PetStoryHistoryVO> getHistory(Long userId, String petId, Map<String, Object> params);
}
