package xiaozhi.modules.companion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.agent.dto.AgentCreateDTO;
import xiaozhi.modules.agent.dto.ContextProviderDTO;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentContextProviderEntity;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentContextProviderService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.companion.dao.CompanionDao;
import xiaozhi.modules.companion.dto.CompanionCreateDTO;
import xiaozhi.modules.companion.dto.CompanionSetupDTO;
import xiaozhi.modules.companion.dto.CompanionUpdateDTO;
import xiaozhi.modules.companion.entity.CompanionEntity;
import xiaozhi.modules.companion.service.CompanionService;
import xiaozhi.modules.companion.util.CharacterAge;
import xiaozhi.modules.companion.util.IntimacyLevel;
import xiaozhi.modules.companion.util.IntimacyRule;
import xiaozhi.modules.companion.util.CompanionBirthCalculator;
import xiaozhi.modules.companion.util.CompanionLabels;
import xiaozhi.modules.companion.util.CompanionMood;
import xiaozhi.modules.companion.util.MenstrualCycleUtil;
import xiaozhi.modules.companion.util.MenstrualPhase;
import xiaozhi.modules.companion.util.ReshapeVoucherRule;
import xiaozhi.modules.companion.vo.CompanionIntimacyVO;
import xiaozhi.modules.companion.vo.CompanionSetupVO;
import xiaozhi.modules.companion.vo.CompanionVO;
import xiaozhi.modules.device.dto.DeviceReportReqDTO;
import xiaozhi.modules.device.dto.DeviceReportRespDTO;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.item.enums.ConsumeBizType;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.security.user.SecurityUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class CompanionServiceImpl extends BaseServiceImpl<CompanionDao, CompanionEntity> implements CompanionService {

    private final CompanionDao companionDao;
    private final AgentService agentService;
    private final AgentContextProviderService agentContextProviderService;
    private final DeviceService deviceService;
    private final PlatformTransactionManager transactionManager;
    private final ItemService itemService;
    private final AiAgentChatHistoryDao chatHistoryDao;

    @Override
    public CompanionVO create(CompanionCreateDTO dto) {
        // 1. 获取当前登录用户
        Long userId = SecurityUser.getUserId();

        // 2. 校验设备是否已有伴侣
        QueryWrapper<CompanionEntity> existWrapper = new QueryWrapper<>();
        existWrapper.eq("device_id", dto.getDeviceId());
        CompanionEntity existing = companionDao.selectOne(existWrapper);
        if (existing != null) {
            throw new RenException(ErrorCode.COMPANION_ALREADY_EXISTS);
        }

        // 3. 根据角色计算年龄，推算出生日期（北京时间）
        int age;
        try {
            age = CharacterAge.getAge(dto.getCharacter());
        } catch (IllegalArgumentException e) {
            throw new RenException(ErrorCode.COMPANION_INVALID_CHARACTER);
        }
        LocalDateTime birthday = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusYears(age);

        // 4. 计算八字、五行、星座、属相
        CompanionBirthCalculator.BirthResult calcResult = CompanionBirthCalculator.calculate(birthday);

        // 5. 创建实体
        CompanionEntity entity = new CompanionEntity();
        entity.setUserId(userId);
        entity.setDeviceId(dto.getDeviceId());
        entity.setType(dto.getType());
        entity.setAvatar(dto.getAvatar());
        entity.setDefaultImage(dto.getDefaultImage());
        entity.setBirthday(Date.from(birthday.atZone(ZoneId.of("Asia/Shanghai")).toInstant()));
        entity.setZodiac(calcResult.zodiac());
        entity.setChineseZodiac(calcResult.chineseZodiac());
        entity.setBazi(calcResult.bazi());
        entity.setWuxing(calcResult.wuxing());
        entity.setCharacter(dto.getCharacter());
        entity.setOccupation(dto.getOccupation());
        entity.setVoice(dto.getVoice());
        entity.setQuirksText(dto.getQuirksText());
        entity.setSoulTraits(dto.getSoulTraits());
        entity.setSoulQuirk(dto.getSoulQuirk());
        entity.setRelationType(dto.getRelationType());
        entity.setPetType(dto.getPetType());
        entity.setPetName(dto.getPetName());
        entity.setMood(CompanionMood.JOY.name());
        entity.setPastLifeSecret(dto.getPastLifeSecret());
        entity.setIntimacy(deriveIntimacy(dto.getRelationType()));
        initializeMenstrualCycle(entity);
        entity.setCreatedBy(userId);

        companionDao.insert(entity);
        log.info("伴侣创建成功，deviceId={}, type={}, character={}", dto.getDeviceId(), dto.getType(), dto.getCharacter());

        // 6. 如果设备已绑定 agent，立即同步包含今日心情的提示词
        String agentId = deviceService.getAgentIdByDeviceId(dto.getDeviceId());
        if (agentId != null && !agentId.isBlank()) {
            try {
                doSyncPromptToAgent(agentId, entity);
            } catch (Exception e) {
                log.warn("创建伴侣后同步提示词失败，deviceId={}: {}", dto.getDeviceId(), e.getMessage());
            }
        }

        return CompanionVO.toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanionVO update(CompanionUpdateDTO dto) {
        QueryWrapper<CompanionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", dto.getDeviceId());
        CompanionEntity entity = companionDao.selectOne(wrapper);
        if (entity == null) {
            throw new RenException(ErrorCode.COMPANION_NOT_FOUND);
        }
        if (!entity.getUserId().equals(SecurityUser.getUserId())) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }

        // 扣券决策抽到纯函数 ReshapeVoucherRule（便于单测）。扣减在写库前集中完成，
        // 事务内后续任何异常都会让扣减一起回滚。
        ReshapeVoucherRule.After after = ReshapeVoucherRule.after(
                dto.getCharacter(), dto.getOccupation(), dto.getSoulTraits(), dto.getSoulQuirk(), dto.getVoice());
        Set<String> consumeSkus = ReshapeVoucherRule.decide(entity, after);
        for (String sku : consumeSkus) {
            String bizType = switch (sku) {
                case ReshapeVoucherRule.ROLE_CHANGE -> ConsumeBizType.ROLE_CHANGE;
                case ReshapeVoucherRule.OCCUPATION_CHANGE -> ConsumeBizType.OCCUPATION_CHANGE;
                case ReshapeVoucherRule.SOUL_QUIRK_CHANGE -> ConsumeBizType.SOUL_QUIRK_CHANGE;
                case ReshapeVoucherRule.VOICE_CHANGE -> ConsumeBizType.VOICE_CHANGE;
                default -> sku;
            };
            itemService.consume(entity.getUserId(), sku, 1, bizType, entity.getDeviceId());
        }
        boolean characterChanged = consumeSkus.contains(ReshapeVoucherRule.ROLE_CHANGE);
        boolean occupationChanged = consumeSkus.contains(ReshapeVoucherRule.OCCUPATION_CHANGE);
        boolean soulChanged = consumeSkus.contains(ReshapeVoucherRule.SOUL_QUIRK_CHANGE);
        boolean voiceChanged = consumeSkus.contains(ReshapeVoucherRule.VOICE_CHANGE);

        boolean needRecalcBirth = false;

        if (dto.getType() != null) entity.setType(dto.getType());
        if (dto.getAvatar() != null) entity.setAvatar(dto.getAvatar());
        if (dto.getDefaultImage() != null) entity.setDefaultImage(dto.getDefaultImage());
        if (occupationChanged) {
            entity.setOccupation(dto.getOccupation());
        }
        if (voiceChanged) {
            entity.setVoice(dto.getVoice());
        }
        if (dto.getQuirksText() != null) entity.setQuirksText(dto.getQuirksText());
        if (dto.getPetType() != null) entity.setPetType(dto.getPetType());
        if (dto.getPetName() != null) entity.setPetName(dto.getPetName());
        if (dto.getMood() != null) {
            validateMood(dto.getMood());
            entity.setMood(dto.getMood().toUpperCase());
        }
        if (dto.getPastLifeSecret() != null) entity.setPastLifeSecret(dto.getPastLifeSecret());

        if (characterChanged) {
            entity.setCharacter(dto.getCharacter());
            needRecalcBirth = true;
        }
        if (soulChanged) {
            if (dto.getSoulTraits() != null) {
                entity.setSoulTraits(dto.getSoulTraits());
            }
            if (dto.getSoulQuirk() != null) {
                entity.setSoulQuirk(dto.getSoulQuirk());
            }
        }
        if (dto.getRelationType() != null) {
            entity.setRelationType(dto.getRelationType());
        }

        if (needRecalcBirth) {
            int age;
            try {
                age = CharacterAge.getAge(entity.getCharacter());
            } catch (IllegalArgumentException e) {
                throw new RenException(ErrorCode.COMPANION_INVALID_CHARACTER);
            }
            LocalDateTime birthday = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusYears(age);
            CompanionBirthCalculator.BirthResult calcResult = CompanionBirthCalculator.calculate(birthday);
            entity.setBirthday(Date.from(birthday.atZone(ZoneId.of("Asia/Shanghai")).toInstant()));
            entity.setZodiac(calcResult.zodiac());
            entity.setChineseZodiac(calcResult.chineseZodiac());
            entity.setBazi(calcResult.bazi());
            entity.setWuxing(calcResult.wuxing());
        }

        companionDao.updateById(entity);
        log.info("伴侣信息已更新，deviceId={}", dto.getDeviceId());

        // 重塑相关字段变化 -> 同步 agent 系统提示词与 TTS 音色（与扣券/写库同事务）。
        if (ReshapeVoucherRule.needsAgentSync(consumeSkus)) {
            String agentId = deviceService.getAgentIdByDeviceId(entity.getDeviceId());
            if (agentId != null && !agentId.isBlank()) {
                syncPromptToAgent(agentId, entity.getId());
            } else {
                log.warn("重塑后未找到 agent，跳过同步，deviceId={}", entity.getDeviceId());
            }
        }

        return CompanionVO.toVO(entity);
    }

    @Override
    public CompanionVO getByDeviceId(String deviceId) {
        QueryWrapper<CompanionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        CompanionEntity entity = companionDao.selectOne(wrapper);
        if (entity == null) {
            throw new RenException(ErrorCode.COMPANION_NOT_FOUND);
        }
        if (!entity.getUserId().equals(SecurityUser.getUserId())) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }
        return CompanionVO.toVO(entity);
    }

    @Override
    public CompanionIntimacyVO getIntimacyInfo(String deviceId) {
        QueryWrapper<CompanionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        CompanionEntity companion = companionDao.selectOne(wrapper);
        if (companion == null) {
            throw new RenException(ErrorCode.COMPANION_NOT_FOUND);
        }
        if (!companion.getUserId().equals(SecurityUser.getUserId())) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }
        float intimacy = companion.getIntimacy() != null
                ? companion.getIntimacy()
                : IntimacyRule.startValue(companion.getRelationType());
        IntimacyLevel level = IntimacyLevel.of(intimacy);
        int streak = companion.getActiveStreak() != null ? companion.getActiveStreak() : 0;
        String lastActive = companion.getLastActiveDate() != null
                ? companion.getLastActiveDate().toString() : null;
        IntimacyLevel nextLevel = level.next();
        String nextLevelName = nextLevel == level ? null : nextLevel.getLabel();
        return new CompanionIntimacyVO(
                intimacy,
                level.getLevel(),
                level.getLabel(),
                level.progressWithin(intimacy),
                nextLevelName,
                streak,
                lastActive);
    }

    private void validateMood(String mood) {
        boolean valid = Arrays.stream(CompanionMood.values())
                .anyMatch(m -> m.name().equals(mood.toUpperCase()));
        if (!valid) {
            throw new RenException(ErrorCode.PARAM_TYPE_INVALID);
        }
    }

    private static float deriveIntimacy(String relationType) {
        return IntimacyRule.startValue(relationType);
    }

    private void initializeMenstrualCycle(CompanionEntity entity) {
        if (!"gf".equals(entity.getType())) {
            return;
        }

        int cycleLength = deriveCycleLength(entity);
        int periodLength = derivePeriodLength(entity);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        int maxOffset = cycleLength - 1;
        int offset = Math.abs(Objects.hash(entity.getCharacter(), entity.getZodiac(), entity.getCreatedAt())) % (maxOffset + 1);
        LocalDate startDate = today.minusDays(offset);

        entity.setMenstrualCycleStart(Date.from(startDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        entity.setMenstrualCycleLength(cycleLength);
        entity.setMenstrualPeriodLength(periodLength);
    }

    private Map<CompanionMood, Integer> computeMoodAdjustments(CompanionEntity companion) {
        if (!"gf".equals(companion.getType())
                || companion.getMenstrualCycleStart() == null
                || companion.getMenstrualCycleLength() == null
                || companion.getMenstrualPeriodLength() == null) {
            return null;
        }

        LocalDate start = companion.getMenstrualCycleStart().toInstant()
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        MenstrualPhase phase = MenstrualCycleUtil.computePhase(
                start, companion.getMenstrualCycleLength(), companion.getMenstrualPeriodLength(), today);

        if (phase == MenstrualPhase.MENSTRUATION) {
            return Map.of(
                    CompanionMood.EXCITEMENT, -5,
                    CompanionMood.CURIOSITY, -5,
                    CompanionMood.FATIGUE, 5,
                    CompanionMood.ANXIETY, 5,
                    CompanionMood.CARE, 5
            );
        }
        return null;
    }

    private int deriveCycleLength(CompanionEntity entity) {
        int hash = Objects.hash(entity.getCharacter(), entity.getZodiac(), entity.getUserId());
        return 26 + (Math.abs(hash) % 7);
    }

    private int derivePeriodLength(CompanionEntity entity) {
        int hash = Objects.hash(entity.getOccupation(), entity.getSoulQuirk(), entity.getUserId());
        return 4 + (Math.abs(hash) % 3);
    }

    @Override
    public void refreshAllMoods() {
        long pageSize = 500L;
        long page = 1L;
        long totalSuccess = 0;
        long totalFailed = 0;

        log.info("开始刷新伴侣今日心情");
        while (true) {
            Page<CompanionEntity> pageResult = companionDao.selectPage(new Page<>(page, pageSize), null);
            List<CompanionEntity> companions = pageResult.getRecords();
            if (companions == null || companions.isEmpty()) {
                break;
            }

            for (CompanionEntity companion : companions) {
                try {
                    Map<CompanionMood, Integer> adjustments = computeMoodAdjustments(companion);
                    companion.setMood(CompanionMood.random(adjustments).name());
                    companionDao.updateById(companion);

                    // 心情已改由实时上下文每轮注入，无需重建提示词；仅确保实时上下文源已登记
                    // （幂等，用于给 ③ 上线前创建的老 agent 补登记）。
                    String agentId = deviceService.getAgentIdByDeviceId(companion.getDeviceId());
                    if (agentId != null && !agentId.isBlank()) {
                        ensureCompanionContextProvider(agentId);
                    }
                    totalSuccess++;
                } catch (Exception e) {
                    totalFailed++;
                    log.warn("刷新伴侣心情失败，companionId={}: {}", companion.getId(), e.getMessage());
                }
            }

            if (pageResult.getCurrent() * pageSize >= pageResult.getTotal()) {
                break;
            }
            page++;
        }

        log.info("伴侣今日心情刷新完成，成功={}，失败={}", totalSuccess, totalFailed);
    }

    private static final DateTimeFormatter CHAT_TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @Override
    public void refreshAllIntimacy() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        String windowStart = yesterday.atStartOfDay(zone).format(CHAT_TS_FORMAT);
        String windowEnd = today.atStartOfDay(zone).format(CHAT_TS_FORMAT);

        Map<String, Integer> msgByAgent = queryYesterdayUserMsgCounts(windowStart, windowEnd);

        long pageSize = 500L;
        long page = 1L;
        long success = 0;
        long failed = 0;
        log.info("开始刷新伴侣亲密度，日窗 [{}, {})", windowStart, windowEnd);

        while (true) {
            Page<CompanionEntity> pageResult = companionDao.selectPage(new Page<>(page, pageSize), null);
            List<CompanionEntity> companions = pageResult.getRecords();
            if (companions == null || companions.isEmpty()) {
                break;
            }
            for (CompanionEntity companion : companions) {
                try {
                    if (today.equals(companion.getIntimacyUpdatedDate())) {
                        continue; // 幂等：同日已处理
                    }
                    applyDailyIntimacy(companion, msgByAgent, yesterday, today);
                    companionDao.updateById(companion);
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.warn("刷新伴侣亲密度失败，companionId={}: {}", companion.getId(), e.getMessage());
                }
            }
            if (pageResult.getCurrent() * pageSize >= pageResult.getTotal()) {
                break;
            }
            page++;
        }
        log.info("伴侣亲密度刷新完成，成功={}，失败={}", success, failed);
    }

    private void applyDailyIntimacy(CompanionEntity companion, Map<String, Integer> msgByAgent,
                                    LocalDate yesterday, LocalDate today) {
        float intimacy = companion.getIntimacy() != null
                ? companion.getIntimacy()
                : IntimacyRule.startValue(companion.getRelationType());
        int streak = companion.getActiveStreak() != null ? companion.getActiveStreak() : 0;
        LocalDate lastActive = companion.getLastActiveDate();

        String agentId = deviceService.getAgentIdByDeviceId(companion.getDeviceId());
        int userMsgs = (agentId != null) ? msgByAgent.getOrDefault(agentId, 0) : 0;

        if (userMsgs > 0) {
            boolean consecutive = lastActive != null && lastActive.equals(yesterday.minusDays(1));
            int newStreak = IntimacyRule.nextStreak(streak, true, consecutive);
            intimacy = IntimacyRule.grow(intimacy, userMsgs, newStreak);
            companion.setActiveStreak(newStreak);
            companion.setLastActiveDate(yesterday);
        } else {
            if (lastActive != null) {
                int daysSince = (int) ChronoUnit.DAYS.between(lastActive, yesterday);
                intimacy = IntimacyRule.decay(intimacy, daysSince);
            }
            companion.setActiveStreak(0);
        }
        companion.setIntimacy(intimacy);
        companion.setIntimacyUpdatedDate(today);
    }

    private Map<String, Integer> queryYesterdayUserMsgCounts(String windowStart, String windowEnd) {
        List<Map<String, Object>> rows = chatHistoryDao.selectMaps(
                new QueryWrapper<AgentChatHistoryEntity>()
                        .select("agent_id AS agentId", "COUNT(*) AS userMsgs")
                        .eq("chat_type", 1)
                        .ge("created_at", windowStart)
                        .lt("created_at", windowEnd)
                        .groupBy("agent_id"));
        Map<String, Integer> map = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                Object agent = r.get("agentId");
                Object count = r.get("userMsgs");
                if (agent != null && count instanceof Number) {
                    map.put(agent.toString(), ((Number) count).intValue());
                }
            }
        }
        return map;
    }

    @Override
    public void syncPromptToAgent(String agentId, Long companionId) {
        Long userId = SecurityUser.getUserId();

        // 查询伴侣
        CompanionEntity companion = this.selectById(companionId);
        if (companion == null) {
            throw new RenException(ErrorCode.COMPANION_NOT_FOUND);
        }
        if (!companion.getUserId().equals(userId)) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }

        // 查询智能体
        AgentEntity agent = agentService.selectById(agentId);
        if (agent == null) {
            throw new RenException(ErrorCode.AGENT_NOT_FOUND);
        }
        if (!agent.getUserId().equals(userId)) {
            throw new RenException(ErrorCode.NO_PERMISSION);
        }

        doSyncPromptToAgent(agentId, companion);
    }

    /**
     * 执行实际的提示词构建与同步，不包含权限校验，供内部和定时任务使用。
     */
    private void doSyncPromptToAgent(String agentId, CompanionEntity companion) {
        // 解析标签
        String characterLabel = CompanionLabels.getLabel(CompanionLabels.CHARACTER, companion.getCharacter());
        String occupationLabel = CompanionLabels.getLabel(CompanionLabels.OCCUPATION, companion.getOccupation());
        String relationTypeLabel = CompanionLabels.getLabel(CompanionLabels.RELATION_TYPE, companion.getRelationType());
        String petTypeLabel = CompanionLabels.getLabel(CompanionLabels.PET_TYPE, companion.getPetType());
        String petNameLabel = companion.getPetName() != null ? companion.getPetName() : "";
        String soulTraitsLabel = CompanionLabels.getSoulTraitsLabels(companion.getSoulTraits());
        String soulQuirkLabel = CompanionLabels.getLabel(CompanionLabels.SOUL_QUIRK, companion.getSoulQuirk());
        String birthdayLabel = companion.getBirthday() != null
                ? new java.text.SimpleDateFormat("yyyy年MM月dd日").format(companion.getBirthday()) : "未知";

        // 心情/亲密度/生理状态已改由 xiaozhi-server 每轮通过 /config/companion-context 实时注入，
        // 不再烤进静态提示词，避免与实时值冲突，也省去每日重建提示词。
        // 替换模板变量
        String prompt = CompanionLabels.SYSTEM_PROMPT_TEMPLATE
                .replace("{{character}}", characterLabel)
                .replace("{{occupation}}", occupationLabel)
                .replace("{{relationType}}", relationTypeLabel)
                .replace("{{petType}}", petTypeLabel)
                .replace("{{petName}}", petNameLabel)
                .replace("{{soulTraits}}", soulTraitsLabel)
                .replace("{{soulQuirk}}", soulQuirkLabel)
                .replace("{{birthday}}", birthdayLabel);

        // 查询并更新智能体系统提示词和音色
        AgentEntity agent = agentService.selectById(agentId);
        if (agent == null) {
            throw new RenException(ErrorCode.AGENT_NOT_FOUND);
        }
        agent.setSystemPrompt(prompt);
        agent.setTtsVoiceId(companion.getVoice());
        agentService.updateById(agent);

        ensureCompanionContextProvider(agentId);

        log.info("伴侣系统提示词已同步, companionId={}, agentId={}", companion.getId(), agentId);
    }

    /** 内部实时上下文端点的相对路径，由 xiaozhi-server 用 manager-api 基址与密钥补全后每轮调用 */
    private static final String COMPANION_CONTEXT_URL = "/config/companion-context";

    /**
     * 为伴侣智能体登记一条实时上下文源（幂等）。已存在则跳过，不覆盖其他 provider。
     */
    private void ensureCompanionContextProvider(String agentId) {
        try {
            AgentContextProviderEntity entity = agentContextProviderService.getByAgentId(agentId);
            List<ContextProviderDTO> providers = (entity != null && entity.getContextProviders() != null)
                    ? new ArrayList<>(entity.getContextProviders())
                    : new ArrayList<>();
            boolean exists = providers.stream().anyMatch(p -> COMPANION_CONTEXT_URL.equals(p.getUrl()));
            if (exists) {
                return;
            }
            ContextProviderDTO provider = new ContextProviderDTO();
            provider.setUrl(COMPANION_CONTEXT_URL);
            provider.setHeaders(Collections.emptyMap());
            providers.add(provider);
            if (entity == null) {
                entity = new AgentContextProviderEntity();
                entity.setAgentId(agentId);
            }
            entity.setContextProviders(providers);
            agentContextProviderService.saveOrUpdateByAgentId(entity);
            log.info("已为伴侣智能体登记实时上下文源, agentId={}", agentId);
        } catch (Exception e) {
            log.warn("登记伴侣实时上下文源失败, agentId={}: {}", agentId, e.getMessage());
        }
    }

    @Override
    public Map<String, String> buildRealtimeContext(String deviceId) {
        Map<String, String> ctx = new LinkedHashMap<>();
        if (deviceId == null || deviceId.isEmpty()) {
            return ctx;
        }
        CompanionEntity companion = companionDao.selectOne(
                new QueryWrapper<CompanionEntity>().eq("device_id", deviceId).last("limit 1"));
        if (companion == null) {
            return ctx;
        }
        ctx.put("当前时段", currentPeriodLabel());
        ctx.put("此刻心情", CompanionMood.fromCode(companion.getMood()).getLabel());
        ctx.put("关系亲密度", renderIntimacy(companion));
        String menstrual = renderMenstrualState(companion);
        if (menstrual != null && !menstrual.isEmpty()) {
            ctx.put("生理状态", menstrual);
        }
        return ctx;
    }

    /** 按本地时钟给出当前时段的自然口语标签 */
    private String currentPeriodLabel() {
        int h = LocalTime.now(ZoneId.of("Asia/Shanghai")).getHour();
        if (h < 5) {
            return "深夜";
        } else if (h < 9) {
            return "清晨";
        } else if (h < 12) {
            return "上午";
        } else if (h < 14) {
            return "午间";
        } else if (h < 18) {
            return "下午";
        } else if (h < 23) {
            return "夜晚";
        } else {
            return "深夜";
        }
    }

    /**
     * 将亲密度渲染成关系阶段描述，供系统提示词/实时上下文使用。
     * 亲密度为空时按 0（初识）处理。
     */
    private String renderIntimacy(CompanionEntity companion) {
        float value = companion.getIntimacy() != null ? companion.getIntimacy() : 0f;
        return IntimacyLevel.of(value).getPromptDescription();
    }

    private String renderMenstrualState(CompanionEntity companion) {
        if (!"gf".equals(companion.getType())
                || companion.getMenstrualCycleStart() == null
                || companion.getMenstrualCycleLength() == null
                || companion.getMenstrualPeriodLength() == null) {
            return "";
        }

        LocalDate start = companion.getMenstrualCycleStart().toInstant()
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        MenstrualPhase phase = MenstrualCycleUtil.computePhase(
                start, companion.getMenstrualCycleLength(), companion.getMenstrualPeriodLength(), today);

        if (phase != MenstrualPhase.MENSTRUATION) {
            return "";
        }

        int day = MenstrualCycleUtil.cycleDay(start, companion.getMenstrualCycleLength(), today);
        return "你正在经期第 " + day + " 天，小腹有点不舒服，容易累，可能会想向用户撒娇求关心。可以自然地说'今天肚子好难受，你哄哄我嘛'。";
    }

    @Override
    public CompanionSetupVO setup(CompanionSetupDTO dto) {
        // Phase 1: 事务性操作（伴侣创建 + 智能体确保 + 提示词同步）
        // 使用 TransactionTemplate 确保事务生效（避免同类调用绕过AOP代理）
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        SetupPhase1Result phase1 = txTemplate.execute(status -> setupPhase1InTx(dto));

        // Phase 2: 设备绑定（涉及Redis，在事务提交后执行）
        CompanionSetupVO vo = new CompanionSetupVO();
        vo.setCompanion(phase1.companion());
        vo.setAgentId(phase1.agentId());

        try {
            bindDevice(dto.getDeviceId(), phase1.agentId(), vo);
        } catch (Exception e) {
            log.warn("设备绑定失败（伴侣和智能体已创建成功，可重试绑定）, deviceId={}: {}",
                    dto.getDeviceId(), e.getMessage());
        }

        return vo;
    }

    private SetupPhase1Result setupPhase1InTx(CompanionSetupDTO dto) {
        // Step 1: 创建伴侣（CompanionSetupDTO 继承 CompanionCreateDTO，直接传入）
        CompanionVO companion = create(dto);

        // Step 2: 确保智能体存在
        String agentId = dto.getAgentId();
        if (agentId == null || agentId.isBlank()) {
            String agentName = SecurityUser.getUser().getUsername();
            AgentCreateDTO agentCreateDTO = new AgentCreateDTO();
            agentCreateDTO.setAgentName(agentName);
            agentId = agentService.createAgent(agentCreateDTO);
            // 仅按已知的 agentId 更新 chat_history_conf=0。
            // 此时设备尚未绑定到新 agent（绑定在 Phase 2 才发生），会查不到或改到别的 agent 上。
            agentService.update(null, new UpdateWrapper<AgentEntity>()
                    .eq("id", agentId)
                    .set("chat_history_conf", 0));
            log.info("聚合接口创建智能体, agentId={}, agentName={}", agentId, agentName);
        } else {
            // 校验已有智能体归属权
            AgentEntity agent = agentService.selectById(agentId);
            if (agent == null) {
                throw new RenException(ErrorCode.AGENT_NOT_FOUND);
            }
            if (!agent.getUserId().equals(SecurityUser.getUserId())) {
                throw new RenException(ErrorCode.NO_PERMISSION);
            }
        }

        // Step 3: 同步提示词到智能体（复用现有逻辑）
        syncPromptToAgent(agentId, companion.getId());

        return new SetupPhase1Result(companion, agentId);
    }

    private void bindDevice(String deviceId, String agentId, CompanionSetupVO vo) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }

        // 构造设备上报请求（模拟小程序端调用）
        DeviceReportReqDTO reportReq = new DeviceReportReqDTO();
        DeviceReportReqDTO.Application app = new DeviceReportReqDTO.Application();
        app.setName("xiaozhi-miniprogram");
        app.setVersion("1.0.0");
        reportReq.setApplication(app);
        DeviceReportReqDTO.BoardInfo board = new DeviceReportReqDTO.BoardInfo();
        board.setType("wechat-miniprogram");
        reportReq.setBoard(board);

        DeviceReportRespDTO otaResp = deviceService.checkDeviceActive(deviceId, "wechat-miniprogram", reportReq);

        if (otaResp.getActivation() != null && otaResp.getActivation().getCode() != null) {
            // 设备未绑定，执行绑定
            String code = otaResp.getActivation().getCode();
            deviceService.deviceActivation(agentId, code);
            log.info("聚合接口绑定设备成功, deviceId={}, agentId={}", deviceId, agentId);

            // 绑定后重新获取WebSocket信息
            DeviceReportRespDTO finalResp = deviceService.checkDeviceActive(deviceId, "wechat-miniprogram", reportReq);
            if (finalResp.getWebsocket() != null) {
                vo.setDeviceBound(true);
                vo.setWsUrl(finalResp.getWebsocket().getUrl());
                vo.setWsToken(finalResp.getWebsocket().getToken());
            }
        } else if (otaResp.getWebsocket() != null) {
            // 设备已绑定，直接使用WebSocket信息
            vo.setDeviceBound(true);
            vo.setWsUrl(otaResp.getWebsocket().getUrl());
            vo.setWsToken(otaResp.getWebsocket().getToken());
        }
    }

    private record SetupPhase1Result(CompanionVO companion, String agentId) {
    }
}
