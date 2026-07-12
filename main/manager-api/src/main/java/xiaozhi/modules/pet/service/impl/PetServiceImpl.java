package xiaozhi.modules.pet.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.dto.AgentCreateDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.constant.MoodLinePool;
import xiaozhi.modules.pet.constant.TodayMood;
import xiaozhi.modules.pet.dao.MemoryDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.UserProfileDao;
import xiaozhi.modules.pet.dto.PetAdoptDTO;
import xiaozhi.modules.pet.entity.MemoryEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.entity.UserProfileEntity;
import xiaozhi.modules.pet.event.CollectionCardGenerationEvent;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.util.MbtiParser;
import xiaozhi.modules.pet.util.MoodDecider;
import xiaozhi.modules.pet.util.PetBirthCalculator;
import xiaozhi.modules.pet.util.PetMood;
import xiaozhi.modules.pet.util.PetNicknameGenerator;
import xiaozhi.modules.pet.util.PetSystemPromptTemplate;
import xiaozhi.modules.pet.vo.ChatHistoryVO;
import xiaozhi.modules.pet.vo.MemoryVO;
import xiaozhi.modules.pet.vo.PetVO;
import xiaozhi.modules.pet.vo.UserProfileVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetServiceImpl extends BaseServiceImpl<PetDao, PetEntity> implements PetService {

    private final PetDao petDao;
    private final DeviceDao deviceDao;
    private final LLMService llmService;
    private final AiAgentChatHistoryDao chatHistoryDao;
    private final MemoryDao memoryDao;
    private final UserProfileDao userProfileDao;
    private final InviteService inviteService;
    private final AgentService agentService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${pet.avatar.koi-defaults:}")
    private String koiDefaultsRaw;

    @Value("${pet.avatar.rabbit-defaults:}")
    private String rabbitDefaultsRaw;

    @Value("${pet.avatar.koi:}")
    private String koiAvatarRaw;

    @Value("${pet.avatar.rabbit:}")
    private String rabbitAvatarRaw;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    private static final String HATCH_STATUS_EGG = "EGG";
    private static final String HATCH_STATUS_HATCHED = "HATCHED";
    private static final String PROTOTYPE_KOI = "锦鲤";
    private static final String PROTOTYPE_RABBIT = "玉兔";
    private static final List<String> PROTOTYPES = List.of(PROTOTYPE_KOI, PROTOTYPE_RABBIT);

    private static final String BOARD_WECHAT_EGG = "wechat-egg-miniprogram";

    private static final List<String> PERSONALITY_BRIEF_POOL = List.of(
            "自带锦鲤体质，靠近就有好运。",
            "慢热但长情，认主就掏心掏肺。",
            "嘴上傲娇，行动诚实，偏爱被夸。",
            "记性好记仇少，温柔里藏着小倔强。",
            "情绪稳定的小太阳，专治雨天低气压。",
            "脑洞大开型选手，聊着聊着就跑题。",
            "务实派陪伴者，不画饼只兜底。",
            "社交牛杂症，独处也自洽。",
            "好奇星人，对一切新鲜事都想插嘴。"
    );

    private static final String DEFAULT_AVATAR_URL = "https://example.com/egg-babe/avatar/default.png";

    private static final String MBTI_PROMPT = """
            根据以下八字和五行信息，推算这个AI宠物的MBTI人格类型。

            八字：年柱-%s，月柱-%s，日柱-%s，时柱-%s
            五行：%s

            请只回复四个字母的MBTI类型，不要其他内容。""";

    private static final String MOOD_SENTENCE_PROMPT = """
            你是一个AI陪伴宠物的内心独白写手。请根据以下信息，写一句它今天的状态文案。

            阶段：%s（孵化期=蛋，破壳后=宠物）
            今日心情：%s
            性格描述：%s
            昵称/原型：%s

            要求：
            1. 中文，20字以内，最多不超过30字
            2. 像宠物自己的状态，不像系统通知，不要鸡汤
            3. 孵化期只写壳里的动静/等待/被照顾/即将破壳，不要写尾巴/跑跳等破壳后动作
            4. 破壳后可写心情/行为/想念/今天在做什么
            5. 不要出现心情类型字样，不要emoji，不要引号
            请直接输出这一句话。""";

    private static final String MOOD_ZONE_ID = "Asia/Shanghai";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetVO adopt(Long userId, PetAdoptDTO dto) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }

        // 1. 先建蛋(EGG)：不建 device/agent，不生成任何破壳档案
        //    device_id=NULL 已由 changeset 202607101500 放宽
        //    Model X: adopt 即为破壳时间基线，写 hatchStartTime=now, expectedHatchTime=now+7d
        String prototype = PROTOTYPES.get(ThreadLocalRandom.current().nextInt(PROTOTYPES.size()));
        Date now = new Date();
        PetEntity pet = new PetEntity();
        pet.setUserId(userId);
        pet.setPrototype(prototype);
        pet.setHatchStatus(HATCH_STATUS_EGG);
        pet.setHatchStartTime(now);
        pet.setExpectedHatchTime(new Date(now.getTime() + SEVEN_DAYS_MS));
        pet.setAcceleratedMinutes(0);
        pet.setCreator(userId);
        petDao.insert(pet);

        // 2. 核销邀请码(REQUIRES_NEW)。
        //    无效/过期/无剩余码会抛异常 → 外层事务回滚 → 蛋回滚，不会产生孤儿蛋。
        //    幂等：同一被邀请人对同一码重复消耗不重复扣减。
        String inviteCode = dto.getInviteCode() == null ? null : dto.getInviteCode().trim();
        if (inviteCode != null && !inviteCode.isBlank()) {
            inviteService.consume(inviteCode, userId);
        }

        log.info("蛋领养成功 userId={}, petId={}, prototype={}", userId, pet.getId(), prototype);
        return toVO(pet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 5. 随机分配性别和血型
        String gender = ThreadLocalRandom.current().nextInt(2) == 0 ? "MALE" : "FEMALE";
        String bloodType = new String[]{"A", "B", "O", "AB"}[ThreadLocalRandom.current().nextInt(4)];

        // 6. 查询该设备是否已有宠物
        QueryWrapper<PetEntity> existWrapper = new QueryWrapper<>();
        existWrapper.eq("device_id", deviceId);
        PetEntity existingPet = petDao.selectOne(existWrapper);

        Date birthDate = Date.from(birthTime.atZone(ZoneId.systemDefault()).toInstant());

        if (existingPet != null) {
            // TODO 演示逻辑：宠物已存在时，根据当前时间重新生成昵称、五行、八字、星座和MBTI并更新，后期去掉
            String nickname = PetNicknameGenerator.generate();
            existingPet.setNickname(nickname);
            existingPet.setBirthDate(birthDate);
            existingPet.setBazi(calcResult.bazi());
            existingPet.setWuxing(calcResult.wuxing());
            existingPet.setZodiac(calcResult.zodiac());
            existingPet.setMbti(mbti);
            existingPet.setGender(gender);
            existingPet.setBloodType(bloodType);
            existingPet.setTodayMood(PetMood.random().name());
            existingPet.setUpdater(device.getUserId());
            petDao.updateById(existingPet);

            // 同步更新关联 agent 的角色设定
            updateAgentSystemPrompt(device.getAgentId(), existingPet, birthDate, calcResult, mbti);

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
        pet.setBirthDate(birthDate);
        pet.setBazi(calcResult.bazi());
        pet.setWuxing(calcResult.wuxing());
        pet.setZodiac(calcResult.zodiac());
        pet.setMbti(mbti);
        pet.setGender(gender);
        pet.setBloodType(bloodType);
        pet.setTodayMood(PetMood.random().name());
        pet.setCreator(device.getUserId());

        petDao.insert(pet);

        // 9. 创建 agent 并注入角色设定
        AgentCreateDTO agentDto = new AgentCreateDTO();
        agentDto.setAgentName(nickname);
        String agentId = agentService.createAgent(agentDto);
        String systemPrompt = renderSystemPrompt(pet, birthDate, calcResult, mbti);
        agentService.update(null, new UpdateWrapper<AgentEntity>()
                .eq("id", agentId)
                .set("system_prompt", systemPrompt));

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
        return pets.stream()
                .peek(this::refreshTodayMood)
                .map(this::toVO)
                .toList();
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

    @Override
    public PetVO getById(Long userId, String petId) {
        PetEntity pet = petDao.selectById(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        refreshTodayMood(pet);
        return toVO(pet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetVO hatch(Long userId, String petId) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }

        PetEntity pet = petDao.selectByIdForUpdate(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        if (!HATCH_STATUS_EGG.equals(pet.getHatchStatus())) {
            throw new RenException(ErrorCode.PET_ALREADY_HATCHED);
        }

        Date now = new Date();
        if (pet.getExpectedHatchTime() == null) {
            // 兜底：无动作且未设基线的蛋，按 createDate+7d 推算到点时间
            long baseTs = pet.getCreateDate() != null ? pet.getCreateDate().getTime() : now.getTime();
            pet.setExpectedHatchTime(new Date(baseTs + SEVEN_DAYS_MS));
        }
        if (now.before(pet.getExpectedHatchTime())) {
            throw new RenException(ErrorCode.PET_HATCH_TIME_NOT_REACHED);
        }

        // 命理 bazi 主导 → LLM 推 MBTI → 模板渲染 agent 系统提示词
        LocalDateTime hatchTime = LocalDateTime.now();
        PetBirthCalculator.BirthResult calc = PetBirthCalculator.calculate(hatchTime);
        String mbti = deriveMbti(calc);
        String brief = randomBrief();
        String gender = ThreadLocalRandom.current().nextInt(2) == 0 ? "MALE" : "FEMALE";
        String bloodType = new String[]{"A", "B", "O", "AB"}[ThreadLocalRandom.current().nextInt(4)];
        String avatarUrl = randomAvatarUrl(pet.getPrototype());

        // 回填宠物破壳档案（需在 agent 创建前写 gender/bloodType 以便模板渲染）
        pet.setHatchStatus(HATCH_STATUS_HATCHED);
        pet.setHatchedAt(now);
        pet.setBirthDate(now);
        pet.setBazi(calc.bazi());
        pet.setWuxing(calc.wuxing());
        pet.setZodiac(calc.zodiac());
        pet.setMbti(mbti);
        pet.setPersonalityBrief(brief);
        pet.setGender(gender);
        pet.setBloodType(bloodType);
        pet.setAvatarUrl(avatarUrl);
        pet.setUpdater(userId);

        // agent 个性注入：使用模板渲染系统提示词
        AgentCreateDTO agentDto = new AgentCreateDTO();
        agentDto.setAgentName(StringUtils.isBlank(pet.getNickname()) ? pet.getPrototype() : pet.getNickname());
        String agentId = agentService.createAgent(agentDto);
        String systemPrompt = renderSystemPrompt(pet, now, calc, mbti);
        agentService.update(null, new UpdateWrapper<AgentEntity>()
                .eq("id", agentId)
                .set("system_prompt", systemPrompt));

        // 手动建蛋设备：macAddress 必须等于 id，否则 OTA 查不到
        DeviceEntity device = new DeviceEntity();
        String deviceId = IdUtil.simpleUUID();
        device.setId(deviceId);
        device.setMacAddress(deviceId);
        device.setUserId(userId);
        device.setBoard(BOARD_WECHAT_EGG);
        device.setAlias(pet.getNickname());
        device.setAgentId(agentId);
        device.setAppVersion("1.0.0");
        device.setAutoUpdate(0);
        device.setCreator(userId);
        deviceDao.insert(device);

        // 回填设备ID
        pet.setDeviceId(deviceId);
        petDao.updateById(pet);

        eventPublisher.publishEvent(new CollectionCardGenerationEvent(pet.getId()));

        log.info("蛋破壳 userId={}, petId={}, deviceId={}, agentId={}", userId, petId, deviceId, agentId);
        return toVO(pet);
    }

    /**
     * 按原型取头像：合并默认池配置与扩展池配置（均为分号分隔），随机取一个；池空走兜底。
     */
    private String randomAvatarUrl(String prototype) {
        String defaultsRaw = PROTOTYPE_RABBIT.equals(prototype) ? rabbitDefaultsRaw : koiDefaultsRaw;
        String extraRaw = PROTOTYPE_RABBIT.equals(prototype) ? rabbitAvatarRaw : koiAvatarRaw;

        List<String> pool = new ArrayList<>();
        splitAndAdd(pool, defaultsRaw);
        splitAndAdd(pool, extraRaw);

        if (pool.isEmpty()) {
            return DEFAULT_AVATAR_URL;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private void splitAndAdd(List<String> pool, String raw) {
        if (raw == null) return;
        for (String part : raw.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                pool.add(trimmed);
            }
        }
    }

    /**
     * 性格卡片语：内置一组不同卡片，随机取，不调 LLM，不绑 MBTI。
     */
    private String randomBrief() {
        return PERSONALITY_BRIEF_POOL.get(ThreadLocalRandom.current().nextInt(PERSONALITY_BRIEF_POOL.size()));
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

    private String renderSystemPrompt(PetEntity pet, Date birthDate, PetBirthCalculator.BirthResult calc, String mbti) {
        String nickname = StringUtils.isBlank(pet.getNickname()) ? pet.getPrototype() : pet.getNickname();
        return PetSystemPromptTemplate.render(
                nickname,
                birthDate,
                calc.bazi(),
                calc.wuxing(),
                calc.zodiac(),
                mbti,
                pet.getPrototype(),
                pet.getGender(),
                pet.getBloodType()
        );
    }

    private void updateAgentSystemPrompt(String agentId, PetEntity pet, Date birthDate,
                                         PetBirthCalculator.BirthResult calc, String mbti) {
        if (StringUtils.isBlank(agentId)) {
            log.warn("宠物无关联 agent，跳过角色设定更新，petId={}", pet.getId());
            return;
        }
        String systemPrompt = renderSystemPrompt(pet, birthDate, calc, mbti);
        agentService.update(null, new UpdateWrapper<AgentEntity>()
                .eq("id", agentId)
                .set("system_prompt", systemPrompt));
    }

    @Override
    public PetVO toVO(PetEntity pet) {
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
        vo.setHatchStatus(pet.getHatchStatus());
        vo.setHatchStartTime(pet.getHatchStartTime());
        vo.setExpectedHatchTime(pet.getExpectedHatchTime());
        vo.setHatchedAt(pet.getHatchedAt());
        vo.setAcceleratedMinutes(pet.getAcceleratedMinutes());
        vo.setAvatarUrl(pet.getAvatarUrl());
        vo.setPrototype(pet.getPrototype());
        vo.setGender(pet.getGender());
        vo.setBloodType(pet.getBloodType());
        vo.setPersonalityBrief(pet.getPersonalityBrief());
        vo.setTodayMoodDate(pet.getTodayMoodDate());
        vo.setTodayMoodSentence(pet.getTodayMoodSentence());
        vo.setCreateDate(pet.getCreateDate());
        return vo;
    }

    @Override
    public void refreshTodayMood(PetEntity pet) {
        if (pet == null) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of(MOOD_ZONE_ID));
        if (today.equals(pet.getTodayMoodDate()) && pet.getTodayMood() != null) {
            return;
        }

        long now = System.currentTimeMillis();
        long baselineMs = MoodDecider.baseline(pet, now);
        TodayMood mood = MoodDecider.decide(pet, baselineMs, now);
        String sentence = generateMoodSentence(pet, mood, today);

        // 幂等写回：仅当今日未生成时更新，防并发双写
        UpdateWrapper<PetEntity> uw = new UpdateWrapper<>();
        uw.eq("id", pet.getId())
                .and(w -> w.isNull("today_mood_date").or().ne("today_mood_date", today))
                .set("today_mood", mood.getLabel())
                .set("today_mood_date", today)
                .set("today_mood_sentence", sentence);
        petDao.update(null, uw);

        // 本地反射，保证本次返回的 VO 一致
        pet.setTodayMood(mood.getLabel());
        pet.setTodayMoodDate(today);
        pet.setTodayMoodSentence(sentence);
    }

    /**
     * 生成今日心情一句话：LLM 生成，失败/不可用则用静态文案池兜底（PRD §8.4）。
     */
    private String generateMoodSentence(PetEntity pet, TodayMood mood, LocalDate today) {
        boolean hatched = HATCH_STATUS_HATCHED.equals(pet.getHatchStatus());
        String stage = hatched ? "破壳后" : "孵化期";
        String personality = StringUtils.isNotBlank(pet.getPersonality())
                ? pet.getPersonality()
                : (StringUtils.isNotBlank(pet.getMbti()) ? pet.getMbti() : "未知");
        String identity = StringUtils.isNotBlank(pet.getNickname())
                ? pet.getNickname()
                : (StringUtils.isNotBlank(pet.getPrototype()) ? pet.getPrototype() : "蛋宝宝");

        try {
            if (llmService.isAvailable()) {
                String prompt = String.format(MOOD_SENTENCE_PROMPT, stage, mood.getLabel(), personality, identity);
                String resp = llmService.generateSummary("", prompt);
                if (resp != null && !resp.isBlank()) {
                    String s = resp.trim().replaceAll("[\"“”‘’]", "");
                    if (s.length() > 30) {
                        s = s.substring(0, 30);
                    }
                    return s;
                }
            }
        } catch (Exception e) {
            log.warn("LLM生成今日心情文案失败，使用静态兜底", e);
        }
        return MoodLinePool.pick(hatched, mood, today.toString());
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
    public UserProfileVO getUserProfileByDeviceId(String deviceId) {
        // 构建查询条件 - 查询最新的一条用户画像
        QueryWrapper<UserProfileEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", deviceId)
                .orderByDesc("created_at")
                .last("LIMIT 1");

        // 执行查询
        UserProfileEntity entity = userProfileDao.selectOne(wrapper);

        // 如果没有找到，返回null
        if (entity == null) {
            return null;
        }

        // 转换为VO
        return toUserProfileVO(entity);
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
