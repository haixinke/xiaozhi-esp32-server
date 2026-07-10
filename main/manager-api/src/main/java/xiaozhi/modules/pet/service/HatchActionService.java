package xiaozhi.modules.pet.service;

import xiaozhi.modules.pet.dto.HatchActionDTO;
import xiaozhi.modules.pet.vo.HatchActionVO;
import xiaozhi.modules.pet.vo.HatchActionResultVO;

import java.util.List;

public interface HatchActionService {

    /**
     * 记录一次孵化修炼动作。
     * 幂等：一次性动作每宠一次；每日动作每宠每天一次。命中幂等则不更新宠物、不插入明细。
     * 时间模型采用草案：首个动作写 hatch_start_time=now、expected_hatch_time=now+7d；
     * 后续动作累加 accelerated_minutes 并重算 expected_hatch_time=hatch_start_time+7d-accelerated(clamp 不早于 hatch_start_time)。
     */
    HatchActionResultVO recordHatchAction(Long userId, String petId, HatchActionDTO dto);

    /**
     * 查询某宠物的修炼动作记录(按创建时间倒序)。
     */
    List<HatchActionVO> listByPetId(Long userId, String petId);
}
