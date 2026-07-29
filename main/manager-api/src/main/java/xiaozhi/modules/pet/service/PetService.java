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
     * 纯数据库创建蛋：建 ai_pet(EGG)，不建 device/agent/档案，不刷新今日心情。
     * prototype 必须是 "锦鲤" 或 "玉兔"，由调用方指定（NFC 领取场景使用固定原型）。
     * 加入调用方事务，不发起任何外部调用。
     */
    PetVO createEgg(Long userId, String prototype);

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
     * 更换场景图：按宠物原型随机生成新场景图 URL 并持久化到 scene_url 字段。
     * 校验宠物归属且已破壳(HATCHED)，未破壳抛业务异常。
     *
     * @param userId 当前用户 ID
     * @param petId  宠物 ID
     * @return 更新后的宠物 VO（sceneUrl 已映射）
     */
    PetVO changeScene(Long userId, String petId);

    /**
     * 按需刷新今日心情：today_mood_date != 今日(Asia/Shanghai) 则重新生成(LLM 失败兜底静态池)
     * 并幂等写回，本地反射。已今日则不重生。
     */
    void refreshTodayMood(PetEntity pet);

    /**
     * 构建实时上下文（供 xiaozhi-server 动态上下文注入）。
     * 按 device_id 找到蛋宝宝，懒刷新今日心情后返回 {今日心情: ...}；
     * 无设备/无宠物时返回空 Map。
     */
    Map<String, String> buildRealtimeContext(String deviceId);

    PetVO birth(String deviceId);

    PetVO getByDeviceId(String deviceId);

    List<PetVO> listByUserId(Long userId);

    /**
     * 编辑宠物昵称，返回更新后的最新 PetVO。
     */
    PetVO updatePet(Long userId, String petId, String nickname);

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
