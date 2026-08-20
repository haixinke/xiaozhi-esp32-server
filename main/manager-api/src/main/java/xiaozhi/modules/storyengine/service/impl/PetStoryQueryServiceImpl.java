package xiaozhi.modules.storyengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.storyengine.constant.StoryRuntimeStatus;
import xiaozhi.modules.storyengine.dao.PetStoryHistoryDao;
import xiaozhi.modules.storyengine.dao.PetStoryStateDao;
import xiaozhi.modules.storyengine.entity.PetStoryHistoryEntity;
import xiaozhi.modules.storyengine.entity.PetStoryStateEntity;
import xiaozhi.modules.storyengine.service.PetStoryQueryService;
import xiaozhi.modules.storyengine.vo.PetStoryHistoryVO;
import xiaozhi.modules.storyengine.vo.PetStoryStateVO;

import java.util.List;
import java.util.Map;

/**
 * 宠物故事只读查询实现。先校验宠物归属与破壳状态，再按原型读取共享数据。
 */
@Service
@RequiredArgsConstructor
public class PetStoryQueryServiceImpl implements PetStoryQueryService {

    private static final String HATCHED = "HATCHED";
    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_LIMIT = 10L;
    private static final long MAX_LIMIT = 100L;
    private static final String PAGE_ERROR_MESSAGE = "页码必须是整数";
    private static final String LIMIT_ERROR_MESSAGE = "分页大小必须在1到100之间";

    private final PetDao petDao;
    private final PetStoryStateDao stateDao;
    private final PetStoryHistoryDao historyDao;

    @Override
    public PetStoryStateVO getCurrent(Long userId, String petId) {
        PetEntity pet = ownedPet(userId, petId);
        if (!isHatched(pet)) {
            return null;
        }
        return getCurrentByPrototype(pet.getPrototype());
    }

    @Override
    public PetStoryStateVO getCurrentByPrototype(String petPrototype) {
        if (petPrototype == null || petPrototype.isBlank()) {
            return null;
        }

        QueryWrapper<PetStoryStateEntity> query = new QueryWrapper<>();
        query.eq("pet_prototype", petPrototype)
                .eq("runtime_status", StoryRuntimeStatus.ACTIVE.name());
        PetStoryStateEntity state = stateDao.selectOne(query);
        if (state == null || !StoryRuntimeStatus.ACTIVE.name().equals(state.getRuntimeStatus())) {
            return null;
        }
        return toStateVO(state);
    }

    @Override
    public PageData<PetStoryHistoryVO> getHistory(
            Long userId, String petId, Map<String, Object> params) {
        PetEntity pet = ownedPet(userId, petId);
        if (!isHatched(pet)) {
            return new PageData<>(List.of(), 0);
        }

        long pageNumber = Math.max(
                parseParam(params, Constant.PAGE, DEFAULT_PAGE, PAGE_ERROR_MESSAGE), DEFAULT_PAGE);
        long limit = parseParam(params, Constant.LIMIT, DEFAULT_LIMIT, LIMIT_ERROR_MESSAGE);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new RenException(LIMIT_ERROR_MESSAGE);
        }

        Page<PetStoryHistoryEntity> page = new Page<>(pageNumber, limit);
        QueryWrapper<PetStoryHistoryEntity> query = new QueryWrapper<>();
        query.eq("pet_prototype", pet.getPrototype())
                .orderByDesc("started_at")
                .orderByDesc("id");
        Page<PetStoryHistoryEntity> result = historyDao.selectPage(page, query);
        List<PetStoryHistoryVO> records = result.getRecords().stream()
                .map(this::toHistoryVO)
                .toList();
        return new PageData<>(records, result.getTotal());
    }

    /** 校验宠物存在且归属当前用户，否则抛出对应业务异常 */
    private PetEntity ownedPet(Long userId, String petId) {
        PetEntity pet = petDao.selectById(petId);
        if (pet == null) {
            throw new RenException(ErrorCode.PET_NOT_FOUND);
        }
        if (userId == null || !userId.equals(pet.getUserId())) {
            throw new RenException(ErrorCode.PET_NO_PERMISSION);
        }
        return pet;
    }

    private boolean isHatched(PetEntity pet) {
        return HATCHED.equals(pet.getHatchStatus());
    }

    private long parseParam(
            Map<String, Object> params, String key, long defaultValue, String errorMessage) {
        if (params == null || params.get(key) == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(params.get(key).toString());
        } catch (NumberFormatException exception) {
            throw new RenException(errorMessage);
        }
    }

    private PetStoryStateVO toStateVO(PetStoryStateEntity entity) {
        PetStoryStateVO vo = new PetStoryStateVO();
        copySnapshot(entity.getPetPrototype(), entity.getBigSceneId(), entity.getBigSceneName(),
                entity.getSmallSceneId(), entity.getSmallSceneName(), entity.getActionId(),
                entity.getActionName(), entity.getActionImageId(), entity.getWeightPeriod(),
                entity.getImageTimeOfDay(), entity.getImageUrl(), entity.getTagImageUrl(),
                entity.getCaption(), entity.getDurationHours(), entity.getStartedAt(),
                entity.getExpectedEndAt(), vo);
        return vo;
    }

    private PetStoryHistoryVO toHistoryVO(PetStoryHistoryEntity entity) {
        PetStoryHistoryVO vo = new PetStoryHistoryVO();
        copySnapshot(entity.getPetPrototype(), entity.getBigSceneId(), entity.getBigSceneName(),
                entity.getSmallSceneId(), entity.getSmallSceneName(), entity.getActionId(),
                entity.getActionName(), entity.getActionImageId(), entity.getWeightPeriod(),
                entity.getImageTimeOfDay(), entity.getImageUrl(), null,
                entity.getCaption(), entity.getDurationHours(), entity.getStartedAt(),
                entity.getExpectedEndAt(), vo);
        vo.setArchivedAt(entity.getArchivedAt());
        return vo;
    }

    private void copySnapshot(String petPrototype, String bigSceneId, String bigSceneName,
            String smallSceneId, String smallSceneName, String actionId, String actionName,
            String actionImageId, String weightPeriod, String imageTimeOfDay, String imageUrl,
            String tagImageUrl, String caption, Integer durationHours, java.util.Date startedAt,
            java.util.Date expectedEndAt, PetStoryStateVO vo) {
        vo.setPetPrototype(petPrototype);
        vo.setBigSceneId(bigSceneId);
        vo.setBigSceneName(bigSceneName);
        vo.setSmallSceneId(smallSceneId);
        vo.setSmallSceneName(smallSceneName);
        vo.setActionId(actionId);
        vo.setActionName(actionName);
        vo.setActionImageId(actionImageId);
        vo.setWeightPeriod(weightPeriod);
        vo.setImageTimeOfDay(imageTimeOfDay);
        vo.setImageUrl(imageUrl);
        vo.setTagImageUrl(tagImageUrl);
        vo.setCaption(caption);
        vo.setDurationHours(durationHours);
        vo.setStartedAt(startedAt);
        vo.setExpectedEndAt(expectedEndAt);
    }
}
