package xiaozhi.modules.pet.service;

import xiaozhi.common.service.BaseService;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.vo.PetVO;

import java.util.List;

public interface PetService extends BaseService<PetEntity> {

    PetVO birth(String deviceId);

    PetVO getByDeviceId(String deviceId);

    List<PetVO> listByUserId(Long userId);

    void updatePet(Long userId, String petId, String nickname);
}
