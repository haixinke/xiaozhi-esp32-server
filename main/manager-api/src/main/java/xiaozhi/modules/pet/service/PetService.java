package xiaozhi.modules.pet.service;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.pet.dto.PetAdoptDTO;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.vo.ChatHistoryVO;
import xiaozhi.modules.pet.vo.MemoryVO;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.pet.vo.UserProfileVO;

import java.util.List;
import java.util.Map;

public interface PetService extends BaseService<PetEntity> {

    /**
     * 领养蛋：建 ai_pet(EGG)，不建 device/agent/档案。
     * prototype 后端随机(锦鲤/玉兔)；inviteCode 可选，传则核销裂变邀请码(无效码回滚整次领养)。
     */
    PetVO adopt(Long userId, PetAdoptDTO dto);

    /**
     * 破壳：EGG 态蛋到点后破壳。
     * 校验 hatchStatus==EGG 且 now>=expectedHatchTime(adopt 设 now+7d，无动作蛋到点即破)。
     * 命理 bazi 主导 → LLM 推 MBTI → LLM 生成性格(作 agent 系统提示词)；
     * 手动建蛋设备(macAddress=id)；agent 注入个性；回填破壳档案。
     */
    PetVO hatch(Long userId, String petId);

    /**
     * 按 petId 查询当前用户的宠物，校验归属。
     */
    PetVO getById(Long userId, String petId);

    /**
     * 按需刷新今日心情：today_mood_date != 今日(Asia/Shanghai) 则重新生成(LLM 失败兜底静态池)
     * 并幂等写回，本地反射。已今日则不重生。
     */
    void refreshTodayMood(PetEntity pet);

    PetVO birth(String deviceId);

    PetVO getByDeviceId(String deviceId);

    List<PetVO> listByUserId(Long userId);

    void updatePet(Long userId, String petId, String nickname);

    /**
     * 将宠物实体转为视图对象。
     */
    PetVO toVO(PetEntity pet);

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
     * 根据设备ID查询用户画像
     *
     * @param deviceId 设备ID (user_id)
     * @return 用户画像
     */
    UserProfileVO getUserProfileByDeviceId(String deviceId);
}
