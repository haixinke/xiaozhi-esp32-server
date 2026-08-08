package xiaozhi.modules.storyengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.storyengine.dao.ActionDao;
import xiaozhi.modules.storyengine.dao.ActionImageDao;
import xiaozhi.modules.storyengine.dto.ActionDTO;
import xiaozhi.modules.storyengine.entity.ActionEntity;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;
import xiaozhi.modules.storyengine.service.StoryActionImageService;
import xiaozhi.modules.storyengine.service.StoryActionService;
import xiaozhi.modules.storyengine.vo.ActionImageVO;
import xiaozhi.modules.storyengine.vo.ActionVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class StoryActionServiceImpl extends BaseServiceImpl<ActionDao, ActionEntity> implements StoryActionService {

    private final ActionDao actionDao;
    private final ActionImageDao actionImageDao;
    private final StoryActionImageService actionImageService;

    @Override
    public List<ActionVO> listBySmallSceneId(String smallSceneId) {
        if (StringUtils.isBlank(smallSceneId)) {
            throw new RenException("小场景ID不能为空");
        }
        QueryWrapper<ActionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("small_scene_id", smallSceneId)
                .orderByAsc("sort_order")
                .orderByAsc("id");
        List<ActionEntity> actions = actionDao.selectList(wrapper);
        if (actions.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<ActionImageVO>> imagesByActionId = loadImages(
                actions.stream().map(ActionEntity::getId).toList());

        List<ActionVO> list = new ArrayList<>(actions.size());
        for (ActionEntity action : actions) {
            ActionVO vo = ActionVO.toVO(action);
            vo.setImages(imagesByActionId.getOrDefault(action.getId(), new ArrayList<>()));
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ActionDTO dto) {
        if (StringUtils.isBlank(dto.getSmallSceneId())) {
            throw new RenException("所属小场景ID不能为空");
        }
        validateDuration(dto);

        ActionEntity entity = new ActionEntity();
        entity.setSmallSceneId(dto.getSmallSceneId());
        entity.setName(dto.getName());
        entity.setDurationMin(dto.getDurationMin() == null ? 1 : dto.getDurationMin());
        entity.setDurationMax(dto.getDurationMax() == null ? 2 : dto.getDurationMax());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        actionDao.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ActionDTO dto) {
        if (StringUtils.isBlank(dto.getId())) {
            throw new RenException("动作ID不能为空");
        }
        ActionEntity existing = actionDao.selectById(dto.getId());
        if (existing == null) {
            throw new RenException("动作不存在");
        }
        validateDuration(dto);

        existing.setName(dto.getName());
        if (dto.getDurationMin() != null) {
            existing.setDurationMin(dto.getDurationMin());
        }
        if (dto.getDurationMax() != null) {
            existing.setDurationMax(dto.getDurationMax());
        }
        if (dto.getSortOrder() != null) {
            existing.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus());
        }
        actionDao.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ActionEntity existing = actionDao.selectById(id);
        if (existing == null) {
            throw new RenException("动作不存在");
        }
        actionImageService.deleteByActionId(id);
        actionDao.deleteById(id);
    }

    /**
     * 一次性查出所有动作的图片，按动作ID分组，避免逐个动作查询。
     */
    private Map<String, List<ActionImageVO>> loadImages(List<String> actionIds) {
        QueryWrapper<ActionImageEntity> wrapper = new QueryWrapper<>();
        wrapper.in("action_id", actionIds)
                .orderByAsc("pet_prototype")
                .orderByAsc("time_of_day")
                .orderByAsc("sort_order");
        return actionImageDao.selectList(wrapper).stream()
                .map(ActionImageVO::toVO)
                .collect(Collectors.groupingBy(ActionImageVO::getActionId));
    }

    private void validateDuration(ActionDTO dto) {
        if (dto.getDurationMin() != null && dto.getDurationMax() != null
                && dto.getDurationMin() > dto.getDurationMax()) {
            throw new RenException("最短时长不能大于最长时长");
        }
    }
}
