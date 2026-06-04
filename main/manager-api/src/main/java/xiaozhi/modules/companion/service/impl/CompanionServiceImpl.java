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
import xiaozhi.modules.security.user.SecurityUser;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

@Slf4j
@Service
@AllArgsConstructor
public class CompanionServiceImpl extends BaseServiceImpl<CompanionDao, CompanionEntity> implements CompanionService {

    private final CompanionDao companionDao;

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
        entity.setBirthday(birthday);
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
        if (dto.getPastLifeSecret() != null) entity.setPastLifeSecret(dto.getPastLifeSecret());

        if (dto.getCharacter() != null && !dto.getCharacter().equals(entity.getCharacter())) {
            entity.setCharacter(dto.getCharacter());
            needRecalcBirth = true;
        }
        if (dto.getSoulTraits() != null) entity.setSoulTraits(dto.getSoulTraits());
        if (dto.getSoulQuirk() != null) entity.setSoulQuirk(dto.getSoulQuirk());
        if (dto.getRelationType() != null) entity.setRelationType(dto.getRelationType());

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
            case "青梅竹马" -> 0.7f;
            case "一见钟情" -> 0.6f;
            case "欢喜冤家" -> 0.5f;
            default -> 0.5f;
        };
    }
}
