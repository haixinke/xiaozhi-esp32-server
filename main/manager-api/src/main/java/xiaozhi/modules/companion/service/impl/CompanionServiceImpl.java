package xiaozhi.modules.companion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.companion.dao.CompanionDao;
import xiaozhi.modules.companion.dto.CompanionCreateDTO;
import xiaozhi.modules.companion.dto.CompanionSetupDTO;
import xiaozhi.modules.companion.dto.CompanionUpdateDTO;
import xiaozhi.modules.companion.entity.CompanionEntity;
import xiaozhi.modules.companion.service.CompanionService;
import xiaozhi.modules.companion.util.CharacterAge;
import xiaozhi.modules.companion.util.CompanionBirthCalculator;
import xiaozhi.modules.companion.util.CompanionLabels;
import xiaozhi.modules.companion.util.CompanionMood;
import xiaozhi.modules.companion.vo.CompanionSetupVO;
import xiaozhi.modules.companion.vo.CompanionVO;
import xiaozhi.modules.device.dto.DeviceReportReqDTO;
import xiaozhi.modules.device.dto.DeviceReportRespDTO;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.item.enums.ConsumeBizType;
import xiaozhi.modules.item.service.ItemService;
import xiaozhi.modules.security.user.SecurityUser;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

@Slf4j
@Service
@AllArgsConstructor
public class CompanionServiceImpl extends BaseServiceImpl<CompanionDao, CompanionEntity> implements CompanionService {

    private final CompanionDao companionDao;
    private final AgentService agentService;
    private final DeviceService deviceService;
    private final PlatformTransactionManager transactionManager;
    private final ItemService itemService;

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
        entity.setMood(CompanionMood.CALM.name());
        entity.setPastLifeSecret(dto.getPastLifeSecret());
        entity.setIntimacy(deriveIntimacy(dto.getRelationType()));
        entity.setCreatedBy(userId);

        companionDao.insert(entity);
        log.info("伴侣创建成功，deviceId={}, type={}, character={}", dto.getDeviceId(), dto.getType(), dto.getCharacter());

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

        // 门禁校验与道具消耗：都放在实际写入之前集中完成，事务内任何后续异常会让道具扣减一起回滚。
        boolean occupationChanged = dto.getOccupation() != null && !dto.getOccupation().equals(entity.getOccupation());
        boolean soulQuirkChanged = dto.getSoulQuirk() != null && !dto.getSoulQuirk().equals(entity.getSoulQuirk());
        if (occupationChanged) {
            itemService.consume(entity.getUserId(), "occupation_change", 1,
                    ConsumeBizType.OCCUPATION_CHANGE, entity.getDeviceId());
        }
        if (soulQuirkChanged) {
            itemService.consume(entity.getUserId(), "soul_quirk_change", 1,
                    ConsumeBizType.SOUL_QUIRK_CHANGE, entity.getDeviceId());
        }

        boolean needRecalcBirth = false;

        if (dto.getType() != null) entity.setType(dto.getType());
        if (dto.getAvatar() != null) entity.setAvatar(dto.getAvatar());
        if (dto.getDefaultImage() != null) entity.setDefaultImage(dto.getDefaultImage());
        if (occupationChanged) {
            entity.setOccupation(dto.getOccupation());
        }
        if (dto.getVoice() != null) entity.setVoice(dto.getVoice());
        if (dto.getQuirksText() != null) entity.setQuirksText(dto.getQuirksText());
        if (dto.getPetType() != null) entity.setPetType(dto.getPetType());
        if (dto.getPetName() != null) entity.setPetName(dto.getPetName());
        if (dto.getMood() != null) {
            validateMood(dto.getMood());
            entity.setMood(dto.getMood());
        }
        if (dto.getPastLifeSecret() != null) entity.setPastLifeSecret(dto.getPastLifeSecret());

        if (dto.getCharacter() != null && !dto.getCharacter().equals(entity.getCharacter())) {
            entity.setCharacter(dto.getCharacter());
            needRecalcBirth = true;
        }
        if (dto.getSoulTraits() != null) entity.setSoulTraits(dto.getSoulTraits());
        if (soulQuirkChanged) {
            entity.setSoulQuirk(dto.getSoulQuirk());
        }
        if (dto.getRelationType() != null) {
            entity.setRelationType(dto.getRelationType());
            entity.setIntimacy(deriveIntimacy(dto.getRelationType()));
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
        return CompanionVO.toVO(entity);
    }

    private void validateMood(String mood) {
        boolean valid = Arrays.stream(CompanionMood.values())
                .anyMatch(m -> m.name().equals(mood));
        if (!valid) {
            throw new RenException(ErrorCode.PARAM_TYPE_INVALID);
        }
    }

    private static float deriveIntimacy(String relationType) {
        return switch (relationType) {
            case "childhood" -> 0.7f;
            case "loveAtFirst" -> 0.6f;
            case "bickering" -> 0.5f;
            default -> 0.5f;
        };
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

        // 更新智能体系统提示词和音色
        agent.setSystemPrompt(prompt);
        agent.setTtsVoiceId(companion.getVoice());
        agentService.updateById(agent);

        log.info("伴侣系统提示词已同步, companionId={}, agentId={}", companionId, agentId);
        log.info("替换后的系统提示词:\n{}", prompt);
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
