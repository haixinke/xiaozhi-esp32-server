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
     * 按原型直接查询共享当前状态，不做宠物归属与破壳校验。
     * 供机对机场景(如实时上下文注入)在已解析出原型后调用。无 ACTIVE 状态时返回 null。
     */
    PetStoryStateVO getCurrentByPrototype(String petPrototype);

    /**
     * 分页查询宠物所属原型的共享历史。未破壳返回空分页。
     */
    PageData<PetStoryHistoryVO> getHistory(Long userId, String petId, Map<String, Object> params);
}
