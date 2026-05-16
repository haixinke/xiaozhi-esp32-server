package xiaozhi.modules.pet.service;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.vo.ChatHistoryVO;
import xiaozhi.modules.pet.vo.MemoryVO;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.pet.vo.UserProfileVO;

import java.util.List;
import java.util.Map;

public interface PetService extends BaseService<PetEntity> {

    PetVO birth(String deviceId);

    PetVO getByDeviceId(String deviceId);

    List<PetVO> listByUserId(Long userId);

    void updatePet(Long userId, String petId, String nickname);

    /**
     * 根据MAC地址查询聊天历史记录
     *
     * @param macAddress 设备MAC地址
     * @param params     查询参数（包含分页信息）
     * @return 分页聊天历史记录
     */
    PageData<ChatHistoryVO> getChatHistoryByMacAddress(String macAddress, Map<String, Object> params);

    /**
     * 根据设备ID查询记忆记录
     *
     * @param deviceId 设备ID (user_id)
     * @param params   查询参数（包含分页信息）
     * @return 分页记忆记录
     */
    PageData<MemoryVO> getMemoryByDeviceId(String deviceId, Map<String, Object> params);

    /**
     * 根据设备ID查询用户画像记录
     *
     * @param deviceId 设备ID (user_id)
     * @param params   查询参数（包含分页信息）
     * @return 分页用户画像记录
     */
    PageData<UserProfileVO> getUserProfileByDeviceId(String deviceId, Map<String, Object> params);
}
