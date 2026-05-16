package xiaozhi.modules.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.dao.MemoryDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.UserProfileDao;
import xiaozhi.modules.pet.entity.MemoryEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.entity.UserProfileEntity;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.util.MbtiParser;
import xiaozhi.modules.pet.util.PetBirthCalculator;
import xiaozhi.modules.pet.util.PetMood;
import xiaozhi.modules.pet.util.PetNicknameGenerator;
import xiaozhi.modules.pet.vo.ChatHistoryVO;
import xiaozhi.modules.pet.vo.MemoryVO;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.pet.vo.UserProfileVO;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class PetServiceImpl extends BaseServiceImpl<PetDao, PetEntity> implements PetService {

    private final PetDao petDao;
    private final DeviceDao deviceDao;
    private final LLMService llmService;
    private final AiAgentChatHistoryDao chatHistoryDao;
    private final MemoryDao memoryDao;
    private final UserProfileDao userProfileDao;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MBTI_PROMPT = """
            根据以下八字和五行信息，推算这个AI宠物的MBTI人格类型。

            八字：年柱-%s，月柱-%s，日柱-%s，时柱-%s
            五行：%s

            请只回复四个字母的MBTI类型，不要其他内容。""";

    private static final String PERSONALITY_PROMPT = """
            你是一个AI陪伴宠物的性格设计师。根据以下MBTI人格类型，为这个AI陪伴宠物生成一段用于和LLM交互时系统提示词的性格描述。

            MBTI类型：%s

            要求：
            1. 使用第三人称描述，以"你"作为开头
            2. 包含宠物的核心特质（2-3个关键词展开）
            3. 描述它和用户聊天时的风格和互动指南
            4. 用中文撰写，字数控制在200字以内
            5. 语气活泼有趣，让宠物形象更生动
            6. 不要在描述中出现MBTI类型名称或结论
            7. 不要在描述中定义是什么具体的宠物，例如你是机械犬、你是机灵猫等
            8. 不要包含表情符号或emoji相关内容，宠物通过语音与用户交互

            请直接输出性格描述，不要其他内容。""";

    private static final String DEFAULT_PERSONALITY = "性格温和友善，喜欢陪伴主人聊天。虽然偶尔有点小迷糊，但总能用温暖的话语让人感到安心。";

    @Override
    public PetVO birth(String deviceId) {
        // 1. 校验设备存在且已绑定用户
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null || device.getUserId() == null) {
            throw new RenException(ErrorCode.PET_DEVICE_NOT_FOUND);
        }

        // 2. 使用当前时间作为出生时间
        LocalDateTime birthTime = LocalDateTime.now();

        // 3. 计算八字、五行、星座
        PetBirthCalculator.BirthResult calcResult = PetBirthCalculator.calculate(birthTime);

        // 4. 调用 LLM 推算 MBTI
        String mbti = deriveMbti(calcResult);

        // 5. 调用 LLM 生成性格描述
        String personality = derivePersonality(mbti);

        // 6. 查询该设备是否已有宠物
        QueryWrapper<PetEntity> existWrapper = new QueryWrapper<>();
        existWrapper.eq("device_id", deviceId);
        PetEntity existingPet = petDao.selectOne(existWrapper);

        if (existingPet != null) {
            // TODO 演示逻辑：宠物已存在时，根据当前时间重新生成昵称、五行、八字、星座和MBTI并更新，后期去掉
            String nickname = PetNicknameGenerator.generate();
            existingPet.setNickname(nickname);
            existingPet.setBirthDate(Date.from(birthTime.atZone(ZoneId.systemDefault()).toInstant()));
            existingPet.setBazi(calcResult.bazi());
            existingPet.setWuxing(calcResult.wuxing());
            existingPet.setZodiac(calcResult.zodiac());
            existingPet.setMbti(mbti);
            existingPet.setPersonality(personality);
            existingPet.setTodayMood(PetMood.random().name());
            existingPet.setUpdater(device.getUserId());
            petDao.updateById(existingPet);
            log.info("宠物信息已更新（演示），deviceId={}, petId={}, nickname={}", deviceId, existingPet.getId(), nickname);
            return toVO(existingPet);
        }

        // 7. 随机分配昵称
        String nickname = PetNicknameGenerator.generate();

        // 8. 创建宠物实体
        PetEntity pet = new PetEntity();
        pet.setUserId(device.getUserId());
        pet.setDeviceId(deviceId);
        pet.setNickname(nickname);
        pet.setBirthDate(Date.from(birthTime.atZone(ZoneId.systemDefault()).toInstant()));
        pet.setBazi(calcResult.bazi());
        pet.setWuxing(calcResult.wuxing());
        pet.setZodiac(calcResult.zodiac());
        pet.setMbti(mbti);
        pet.setPersonality(personality);
        pet.setTodayMood(PetMood.random().name());
        pet.setCreator(device.getUserId());

        petDao.insert(pet);
        log.info("宠物出生成功，deviceId={}, petId={}, nickname={}", deviceId, pet.getId(), nickname);

        return toVO(pet);
    }

    @Override
    public PetVO getByDeviceId(String deviceId) {
        QueryWrapper<PetEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        PetEntity pet = petDao.selectOne(wrapper);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        return toVO(pet);
    }

    @Override
    public List<PetVO> listByUserId(Long userId) {
        QueryWrapper<PetEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("create_date");
        List<PetEntity> pets = petDao.selectList(wrapper);
        return pets.stream().map(this::toVO).toList();
    }

    @Override
    public void updatePet(Long userId, String petId, String nickname) {
        PetEntity pet = petDao.selectById(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!pet.getUserId().equals(userId)) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        if (nickname != null && !nickname.isBlank()) {
            pet.setNickname(nickname);
            pet.setUpdater(userId);
            petDao.updateById(pet);
        }
    }

    private String deriveMbti(PetBirthCalculator.BirthResult calcResult) {
        try {
            if (!llmService.isAvailable()) {
                log.warn("LLM服务不可用，使用默认MBTI");
                return "INFP";
            }

            JsonNode baziNode = MAPPER.readTree(calcResult.bazi());
            String year = baziNode.get("year").asText();
            String month = baziNode.get("month").asText();
            String day = baziNode.get("day").asText();
            String hour = baziNode.get("hour").asText();

            JsonNode wuxingNode = MAPPER.readTree(calcResult.wuxing());
            String wuxingDisplay = "金-" + wuxingNode.get("metal").asInt()
                    + "，木-" + wuxingNode.get("wood").asInt()
                    + "，水-" + wuxingNode.get("water").asInt()
                    + "，火-" + wuxingNode.get("fire").asInt()
                    + "，土-" + wuxingNode.get("earth").asInt();

            String prompt = String.format(MBTI_PROMPT, year, month, day, hour, wuxingDisplay);

            String response = llmService.generateSummary("", prompt);
            return MbtiParser.parse(response);
        } catch (Exception e) {
            log.error("LLM推算MBTI失败，使用默认值", e);
            return "INFP";
        }
    }

    private String derivePersonality(String mbti) {
        try {
            if (!llmService.isAvailable()) {
                log.warn("LLM服务不可用，使用默认性格描述");
                return DEFAULT_PERSONALITY;
            }

            String prompt = String.format(PERSONALITY_PROMPT, mbti);
            String response = llmService.generateSummary("", prompt);

            if (response != null && !response.isBlank()) {
                String trimmed = response.trim();
                if (trimmed.length() > 500) {
                    return trimmed.substring(0, 500);
                }
                return trimmed;
            }
            return DEFAULT_PERSONALITY;
        } catch (Exception e) {
            log.error("LLM生成性格描述失败，使用默认值", e);
            return DEFAULT_PERSONALITY;
        }
    }

    private PetVO toVO(PetEntity pet) {
        PetVO vo = new PetVO();
        vo.setId(pet.getId());
        vo.setUserId(pet.getUserId());
        vo.setDeviceId(pet.getDeviceId());
        vo.setNickname(pet.getNickname());
        vo.setBirthDate(pet.getBirthDate());
        vo.setBazi(pet.getBazi());
        vo.setWuxing(pet.getWuxing());
        vo.setZodiac(pet.getZodiac());
        vo.setMbti(pet.getMbti());
        vo.setPersonality(pet.getPersonality());
        vo.setTodayMood(pet.getTodayMood());
        vo.setCreateDate(pet.getCreateDate());
        return vo;
    }

    @Override
    public PageData<ChatHistoryVO> getChatHistoryByMacAddress(String macAddress, Map<String, Object> params) {
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Integer.parseInt(params.get(Constant.LIMIT).toString());

        // 构建查询条件
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("mac_address", macAddress)
                .orderByDesc("created_at");

        // 执行分页查询
        Page<AgentChatHistoryEntity> pageParam = new Page<>(page, limit);
        IPage<AgentChatHistoryEntity> result = chatHistoryDao.selectPage(pageParam, wrapper);

        // 转换为VO
        List<ChatHistoryVO> records = result.getRecords().stream().map(entity -> {
            ChatHistoryVO vo = new ChatHistoryVO();
            vo.setId(entity.getId());
            vo.setSessionId(entity.getSessionId());
            vo.setChatType(entity.getChatType());
            vo.setContent(entity.getContent());
            vo.setAudioId(entity.getAudioId());
            vo.setCreatedAt(entity.getCreatedAt());
            return vo;
        }).toList();

        return new PageData<>(records, result.getTotal());
    }

    @Override
    public PageData<MemoryVO> getMemoryByDeviceId(String deviceId, Map<String, Object> params) {
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Integer.parseInt(params.get(Constant.LIMIT).toString());

        // 构建查询条件
        QueryWrapper<MemoryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", deviceId)
                .orderByDesc("created_at");

        // 执行分页查询
        Page<MemoryEntity> pageParam = new Page<>(page, limit);
        IPage<MemoryEntity> result = memoryDao.selectPage(pageParam, wrapper);

        // 转换为VO
        List<MemoryVO> records = result.getRecords().stream()
                .map(this::toMemoryVO)
                .toList();

        return new PageData<>(records, result.getTotal());
    }

    /**
     * 转换MemoryEntity为MemoryVO
     *
     * @param entity MemoryEntity
     * @return MemoryVO
     */
    private MemoryVO toMemoryVO(MemoryEntity entity) {
        MemoryVO vo = new MemoryVO();
        vo.setId(entity.getId());
        vo.setCategory(entity.getCategory());
        vo.setDocument(entity.getDocument());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    @Override
    public PageData<UserProfileVO> getUserProfileByDeviceId(String deviceId, Map<String, Object> params) {
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Integer.parseInt(params.get(Constant.LIMIT).toString());

        // 构建查询条件
        QueryWrapper<UserProfileEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", deviceId)
                .orderByDesc("created_at");

        // 执行分页查询
        Page<UserProfileEntity> pageParam = new Page<>(page, limit);
        IPage<UserProfileEntity> result = userProfileDao.selectPage(pageParam, wrapper);

        // 转换为VO
        List<UserProfileVO> records = result.getRecords().stream()
                .map(this::toUserProfileVO)
                .toList();

        return new PageData<>(records, result.getTotal());
    }

    /**
     * 转换UserProfileEntity为UserProfileVO
     *
     * @param entity UserProfileEntity
     * @return UserProfileVO
     */
    private UserProfileVO toUserProfileVO(UserProfileEntity entity) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(entity.getId());
        vo.setProfileContent(entity.getProfileContent());
        vo.setTopics(entity.getTopics());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
