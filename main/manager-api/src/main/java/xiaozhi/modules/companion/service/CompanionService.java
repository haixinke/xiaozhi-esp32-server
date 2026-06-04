package xiaozhi.modules.companion.service;

import xiaozhi.common.service.BaseService;
import xiaozhi.modules.companion.dto.CompanionCreateDTO;
import xiaozhi.modules.companion.dto.CompanionUpdateDTO;
import xiaozhi.modules.companion.entity.CompanionEntity;
import xiaozhi.modules.companion.vo.CompanionVO;

public interface CompanionService extends BaseService<CompanionEntity> {

    CompanionVO create(CompanionCreateDTO dto);

    CompanionVO update(CompanionUpdateDTO dto);

    CompanionVO getByDeviceId(String deviceId);

    void syncPromptToAgent(String agentId, Long companionId);
}
