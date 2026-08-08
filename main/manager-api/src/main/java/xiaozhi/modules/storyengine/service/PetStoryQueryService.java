package xiaozhi.modules.storyengine.service;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.storyengine.vo.PetStoryHistoryVO;
import xiaozhi.modules.storyengine.vo.PetStoryStateVO;

import java.util.Map;

public interface PetStoryQueryService {

    PetStoryStateVO getCurrent(Long userId, String petId);

    PageData<PetStoryHistoryVO> getHistory(Long userId, String petId, Map<String, Object> params);
}
