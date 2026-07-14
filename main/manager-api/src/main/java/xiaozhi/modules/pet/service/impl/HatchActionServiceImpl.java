package xiaozhi.modules.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pet.constant.HatchActionType;
import xiaozhi.modules.pet.dao.HatchActionDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dto.HatchActionDTO;
import xiaozhi.modules.pet.entity.HatchActionEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.HatchActionService;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.HatchActionResultVO;
import xiaozhi.modules.pet.vo.HatchActionVO;
import xiaozhi.modules.pet.vo.PetVO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class HatchActionServiceImpl implements HatchActionService {

    private static final String HATCH_STATUS_EGG = "EGG";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long ONE_MINUTE_MS = 60L * 1000;

    /** 昵称违禁词：仅做轻量过滤，非完整合规方案。 */
    private static final Set<String> NICKNAME_FORBIDDEN = Set.of("违法", "诈骗", "赌博");

    private final PetDao petDao;
    private final HatchActionDao hatchActionDao;
    private final PetService petService;
    private final ObjectMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HatchActionResultVO recordHatchAction(Long userId, String petId, HatchActionDTO dto) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }

        PetEntity pet = petDao.selectById(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        if (!HATCH_STATUS_EGG.equals(pet.getHatchStatus())) {
            throw new RenException(ErrorCode.PET_ALREADY_HATCHED);
        }

        HatchActionType type = HatchActionType.from(dto.getType())
                .orElseThrow(() -> new RenException("不支持的动作类型"));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        QueryWrapper<HatchActionEntity> qw = new QueryWrapper<HatchActionEntity>()
                .eq("pet_id", petId)
                .eq("action_type", type.name());
        boolean alreadyDone;
        if (type.oneTime()) {
            alreadyDone = hatchActionDao.selectCount(qw) > 0;
        } else {
            alreadyDone = hatchActionDao.selectCount(qw.eq("action_date", today)) > 0;
        }

        int added = 0;
        if (!alreadyDone) {
            int minutes = type.minutes();

            // NICKNAME 昵称校验先行(违规即抛，避免后续无谓重算)
            if (type == HatchActionType.NICKNAME) {
                pet.setNickname(extractValidNickname(dto.getPayload()));
            }

            // Model X: adopt 已设 hatchStartTime/expectedHatchTime。动作不再写起点，
            // 只累加 acceleratedMinutes 并重算 expectedHatchTime = hatchStartTime + 7d - acc(clamp >= hatchStartTime)
            int acc = (pet.getAcceleratedMinutes() == null ? 0 : pet.getAcceleratedMinutes()) + minutes;
            pet.setAcceleratedMinutes(acc);
            long startTs = pet.getHatchStartTime().getTime();
            long base = startTs + SEVEN_DAYS_MS - acc * ONE_MINUTE_MS;
            pet.setExpectedHatchTime(new Date(Math.max(base, startTs)));

            pet.setUpdater(userId);
            petDao.updateById(pet);

            HatchActionEntity act = new HatchActionEntity();
            act.setPetId(petId);
            act.setActionType(type.name());
            act.setPayload(serializePayload(dto.getPayload()));
            act.setActionDate(today);
            act.setAcceleratedMinutes(minutes);
            act.setCreator(userId);
            hatchActionDao.insert(act);

            added = minutes;
        }

        boolean readyToHatch = pet.getExpectedHatchTime() != null
                && !new Date().before(pet.getExpectedHatchTime());

        petService.refreshTodayMood(pet);
        PetVO vo = petService.toVO(pet);
        HatchActionResultVO result = new HatchActionResultVO();
        result.setAddedMinutes(added);
        result.setAlreadyDone(alreadyDone);
        result.setReadyToHatch(readyToHatch);
        result.setPet(vo);
        return result;
    }

    @Override
    public List<HatchActionVO> listByPetId(Long userId, String petId) {
        if (userId == null) {
            throw new RenException(ErrorCode.USER_NOT_LOGIN);
        }
        PetEntity pet = petDao.selectById(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (!userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }

        QueryWrapper<HatchActionEntity> wrapper = new QueryWrapper<HatchActionEntity>()
                .eq("pet_id", petId)
                .orderByDesc("create_date");
        List<HatchActionEntity> list = hatchActionDao.selectList(wrapper);
        return list.stream().map(this::toVO).toList();
    }

    private String extractValidNickname(Map<String, Object> payload) {
        String nickname = null;
        if (payload != null && payload.get("nickname") instanceof String s) {
            nickname = s;
        }
        if (nickname == null || nickname.isBlank()) {
            throw new RenException("昵称不能为空");
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > 10) {
            throw new RenException("昵称长度不能超过10个字符");
        }
        for (String word : NICKNAME_FORBIDDEN) {
            if (trimmed.contains(word)) {
                throw new RenException("昵称包含违规内容");
            }
        }
        return trimmed;
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("hatch action payload 序列化失败, 已存 null: {}", e.getMessage());
            return null;
        }
    }

    private HatchActionVO toVO(HatchActionEntity entity) {
        HatchActionVO vo = new HatchActionVO();
        vo.setId(entity.getId());
        vo.setPetId(entity.getPetId());
        vo.setActionType(entity.getActionType());
        vo.setPayload(entity.getPayload());
        vo.setActionDate(entity.getActionDate());
        vo.setAcceleratedMinutes(entity.getAcceleratedMinutes());
        vo.setCreateDate(entity.getCreateDate());
        return vo;
    }
}
