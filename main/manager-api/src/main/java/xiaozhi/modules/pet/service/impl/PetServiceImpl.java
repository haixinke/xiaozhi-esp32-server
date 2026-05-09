package xiaozhi.modules.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.util.MbtiParser;
import xiaozhi.modules.pet.util.PetBirthCalculator;
import xiaozhi.modules.pet.util.PetNicknameGenerator;
import xiaozhi.modules.pet.vo.PetVO;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PetServiceImpl extends BaseServiceImpl<PetDao, PetEntity> implements PetService {

    private final PetDao petDao;
    private final DeviceDao deviceDao;
    private final LLMService llmService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MBTI_PROMPT = """
            根据以下八字和五行信息，推算这个AI宠物的MBTI人格类型。

            八字：年柱-%s，月柱-%s，日柱-%s，时柱-%s
            五行：%s

            请只回复四个字母的MBTI类型，不要其他内容。""";

    @Override
    public PetVO birth(String deviceId) {
        // 1. 如果该设备已有宠物，直接返回
        QueryWrapper<PetEntity> existWrapper = new QueryWrapper<>();
        existWrapper.eq("device_id", deviceId);
        PetEntity existingPet = petDao.selectOne(existWrapper);
        if (existingPet != null) {
            return toVO(existingPet);
        }

        // 2. 校验设备存在且已绑定用户
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null || device.getUserId() == null) {
            throw new RenException(ErrorCode.PET_DEVICE_NOT_FOUND);
        }

        // 3. 使用当前时间作为出生时间
        LocalDateTime birthTime = LocalDateTime.now();

        // 4. 计算八字、五行、星座
        PetBirthCalculator.BirthResult calcResult = PetBirthCalculator.calculate(birthTime);

        // 5. 调用 LLM 推算 MBTI
        String mbti = deriveMbti(calcResult);

        // 6. 随机分配昵称
        String nickname = PetNicknameGenerator.generate();

        // 7. 创建宠物实体
        PetEntity pet = new PetEntity();
        pet.setUserId(device.getUserId());
        pet.setDeviceId(deviceId);
        pet.setNickname(nickname);
        pet.setBirthDate(Date.from(birthTime.atZone(ZoneId.systemDefault()).toInstant()));
        pet.setBazi(calcResult.bazi());
        pet.setWuxing(calcResult.wuxing());
        pet.setZodiac(calcResult.zodiac());
        pet.setMbti(mbti);
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
        vo.setCreateDate(pet.getCreateDate());
        return vo;
    }
}
