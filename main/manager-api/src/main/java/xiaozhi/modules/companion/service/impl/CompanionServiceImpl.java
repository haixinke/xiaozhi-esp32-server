package xiaozhi.modules.companion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.companion.dao.CompanionDao;
import xiaozhi.modules.companion.dto.CompanionCreateDTO;
import xiaozhi.modules.companion.dto.CompanionUpdateDTO;
import xiaozhi.modules.companion.entity.CompanionEntity;
import xiaozhi.modules.companion.service.CompanionService;
import xiaozhi.modules.companion.util.CharacterAge;
import xiaozhi.modules.companion.util.CompanionBirthCalculator;
import xiaozhi.modules.companion.util.CompanionMood;
import xiaozhi.modules.companion.vo.CompanionVO;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.security.user.SecurityUser;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

@Slf4j
@Service
@AllArgsConstructor
public class CompanionServiceImpl extends BaseServiceImpl<CompanionDao, CompanionEntity> implements CompanionService {

    private final CompanionDao companionDao;
    private final LLMService llmService;

    private static final String PERSONALITY_PROMPT = """
            你是一位擅长刻画人物性格的作家。请根据以下设定，用一段自然生动的文字描述这个角色的性格，要求200字以内：
            - 角色类型：%s
            - 灵魂特质：%s
            - 小任性：%s
            - 与用户的关系：%s

            要求：
            1. 结合角色的类型特点，将灵魂特质和小任性融入性格描述中，让性格鲜活立体
            2. 描述中要体现与用户关系的亲疏感，比如恋人之间会有独占欲和撒娇，朋友之间会有默契和包容
            3. 语言自然流畅，像在描述一个真实存在的人，避免罗列式或模板化的表述
            4. 只输出性格描述文本，不要加标题、引号或其他格式""";

    private static final String DEFAULT_PERSONALITY = "性格温和友善，善解人意，喜欢陪伴在身边。虽然偶尔有点小任性，但总能用温暖的话语让人感到安心。";

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

        // 4. 调用 LLM 生成性格描述
        String characterName = CharacterAge.getName(dto.getCharacter());
        String personality = derivePersonality(
                characterName, dto.getSoulTraits(), dto.getSoulQuirk(), dto.getRelationType()
        );

        // 5. 创建实体
        CompanionEntity entity = new CompanionEntity();
        entity.setUserId(userId);
        entity.setDeviceId(dto.getDeviceId());
        entity.setType(dto.getType());
        entity.setAvatar(dto.getAvatar());
        entity.setDefaultImage(dto.getDefaultImage());
        entity.setBirthday(birthday);
        entity.setZodiac(calcResult.zodiac());
        entity.setChineseZodiac(calcResult.chineseZodiac());
        entity.setBazi(calcResult.bazi());
        entity.setWuxing(calcResult.wuxing());
        entity.setCharacter(dto.getCharacter());
        entity.setOccupation(dto.getOccupation());
        entity.setVoice(dto.getVoice());
        entity.setPersonality(personality);
        entity.setQuirksText(dto.getQuirksText());
        entity.setSoulTraits(dto.getSoulTraits());
        entity.setSoulQuirk(dto.getSoulQuirk());
        entity.setRelationType(dto.getRelationType());
        entity.setPetType(dto.getPetType());
        entity.setPetName(dto.getPetName());
        entity.setMood(CompanionMood.CALM.name());
        entity.setCreatedBy(userId);

        companionDao.insert(entity);
        log.info("伴侣创建成功，deviceId={}, type={}, character={}", dto.getDeviceId(), dto.getType(), dto.getCharacter());

        return CompanionVO.toVO(entity);
    }

    @Override
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

        boolean needRecalcBirth = false;
        boolean needRecalcPersonality = false;

        if (dto.getType() != null) entity.setType(dto.getType());
        if (dto.getAvatar() != null) entity.setAvatar(dto.getAvatar());
        if (dto.getDefaultImage() != null) entity.setDefaultImage(dto.getDefaultImage());
        if (dto.getOccupation() != null) entity.setOccupation(dto.getOccupation());
        if (dto.getVoice() != null) entity.setVoice(dto.getVoice());
        if (dto.getQuirksText() != null) entity.setQuirksText(dto.getQuirksText());
        if (dto.getPetType() != null) entity.setPetType(dto.getPetType());
        if (dto.getPetName() != null) entity.setPetName(dto.getPetName());
        if (dto.getMood() != null) {
            validateMood(dto.getMood());
            entity.setMood(dto.getMood());
        }
        if (dto.getPersonality() != null) entity.setPersonality(dto.getPersonality());

        if (dto.getCharacter() != null && !dto.getCharacter().equals(entity.getCharacter())) {
            entity.setCharacter(dto.getCharacter());
            needRecalcBirth = true;
            needRecalcPersonality = true;
        }
        if (dto.getSoulTraits() != null && !dto.getSoulTraits().equals(entity.getSoulTraits())) {
            entity.setSoulTraits(dto.getSoulTraits());
            needRecalcPersonality = true;
        }
        if (dto.getSoulQuirk() != null && !dto.getSoulQuirk().equals(entity.getSoulQuirk())) {
            entity.setSoulQuirk(dto.getSoulQuirk());
            needRecalcPersonality = true;
        }
        if (dto.getRelationType() != null && !dto.getRelationType().equals(entity.getRelationType())) {
            entity.setRelationType(dto.getRelationType());
            needRecalcPersonality = true;
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
            entity.setBirthday(birthday);
            entity.setZodiac(calcResult.zodiac());
            entity.setChineseZodiac(calcResult.chineseZodiac());
            entity.setBazi(calcResult.bazi());
            entity.setWuxing(calcResult.wuxing());
        }

        if (needRecalcPersonality) {
            String characterName = CharacterAge.getName(entity.getCharacter());
            String personality = derivePersonality(
                    characterName, entity.getSoulTraits(), entity.getSoulQuirk(), entity.getRelationType()
            );
            entity.setPersonality(personality);
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

    private String derivePersonality(String characterName, String soulTraits, String soulQuirk, String relationType) {
        try {
            if (!llmService.isAvailable()) {
                log.warn("LLM服务不可用，使用默认性格描述");
                return DEFAULT_PERSONALITY;
            }

            String prompt = String.format(PERSONALITY_PROMPT, characterName, soulTraits, soulQuirk, relationType);
            log.info("LLM生成性格描述，提示词：{}", prompt);
            String response = llmService.generateSummary("", prompt);

            if (response != null && !response.isBlank()) {
                return response.trim();
            }
            return DEFAULT_PERSONALITY;
        } catch (Exception e) {
            log.error("LLM生成性格描述失败，使用默认值", e);
            return DEFAULT_PERSONALITY;
        }
    }

    private void validateMood(String mood) {
        boolean valid = Arrays.stream(CompanionMood.values())
                .anyMatch(m -> m.name().equals(mood));
        if (!valid) {
            throw new RenException(ErrorCode.PARAM_TYPE_INVALID);
        }
    }
}
