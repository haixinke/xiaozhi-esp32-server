package xiaozhi.modules.storyengine.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;
import xiaozhi.modules.storyengine.dao.ActionDao;
import xiaozhi.modules.storyengine.dao.ActionImageDao;
import xiaozhi.modules.storyengine.dao.BigSceneDao;
import xiaozhi.modules.storyengine.dao.SmallSceneDao;
import xiaozhi.modules.storyengine.entity.ActionEntity;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;
import xiaozhi.modules.storyengine.entity.BigSceneEntity;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;
import xiaozhi.modules.storyengine.model.StoryActionCandidate;
import xiaozhi.modules.storyengine.model.StoryImageCandidate;
import xiaozhi.modules.storyengine.model.StoryPeriodContext;
import xiaozhi.modules.storyengine.model.StorySceneCandidate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StoryContentLoader {
    private static final int ENABLED = 1;

    private final BigSceneDao bigSceneDao;
    private final SmallSceneDao smallSceneDao;
    private final ActionDao actionDao;
    private final ActionImageDao actionImageDao;

    public List<StorySceneCandidate> load(String prototype, StoryPeriodContext period) {
        List<BigSceneEntity> bigScenes = loadBigScenes();
        if (bigScenes.isEmpty()) {
            return List.of();
        }

        List<SmallSceneEntity> smallScenes = loadSmallScenes(bigScenes.stream().map(BigSceneEntity::getId).toList());
        if (smallScenes.isEmpty()) {
            return List.of();
        }

        List<ActionEntity> actions = loadActions(smallScenes.stream().map(SmallSceneEntity::getId).toList());
        Map<String, List<ActionImageEntity>> imagesByActionId = actions.isEmpty()
                ? Map.of()
                : loadMatchingImages(actions.stream().map(ActionEntity::getId).toList(), prototype,
                        period.imageTimeOfDay().databaseValue());
        return assemble(bigScenes, smallScenes, actions, imagesByActionId, period.weightPeriod());
    }

    private List<BigSceneEntity> loadBigScenes() {
        QueryWrapper<BigSceneEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status", ENABLED).orderByAsc("sort_order").orderByAsc("id");
        return bigSceneDao.selectList(wrapper).stream()
                .filter(this::isEnabled)
                .sorted(bySortOrderAndId(BigSceneEntity::getSortOrder, BigSceneEntity::getId))
                .toList();
    }

    private List<SmallSceneEntity> loadSmallScenes(List<String> bigSceneIds) {
        QueryWrapper<SmallSceneEntity> wrapper = new QueryWrapper<>();
        wrapper.in("big_scene_id", bigSceneIds).eq("status", ENABLED)
                .orderByAsc("sort_order").orderByAsc("id");
        return smallSceneDao.selectList(wrapper).stream()
                .filter(this::isEnabled)
                .sorted(bySortOrderAndId(SmallSceneEntity::getSortOrder, SmallSceneEntity::getId))
                .toList();
    }

    private List<ActionEntity> loadActions(List<String> smallSceneIds) {
        QueryWrapper<ActionEntity> wrapper = new QueryWrapper<>();
        wrapper.in("small_scene_id", smallSceneIds).eq("status", ENABLED)
                .orderByAsc("sort_order").orderByAsc("id");
        return actionDao.selectList(wrapper).stream()
                .filter(this::isEnabled)
                .sorted(bySortOrderAndId(ActionEntity::getSortOrder, ActionEntity::getId))
                .toList();
    }

    private Map<String, List<ActionImageEntity>> loadMatchingImages(List<String> actionIds, String prototype,
                                                                      String imageTimeOfDay) {
        QueryWrapper<ActionImageEntity> wrapper = new QueryWrapper<>();
        wrapper.in("action_id", actionIds)
                .eq("pet_prototype", prototype)
                .eq("time_of_day", imageTimeOfDay)
                .orderByAsc("sort_order").orderByAsc("id");
        return actionImageDao.selectList(wrapper).stream()
                .filter(image -> prototype.equals(image.getPetPrototype()))
                .filter(image -> imageTimeOfDay.equals(image.getTimeOfDay()))
                .sorted(bySortOrderAndId(ActionImageEntity::getSortOrder, ActionImageEntity::getId))
                .collect(Collectors.groupingBy(ActionImageEntity::getActionId));
    }

    private List<StorySceneCandidate> assemble(List<BigSceneEntity> bigScenes, List<SmallSceneEntity> smallScenes,
                                               List<ActionEntity> actions,
                                               Map<String, List<ActionImageEntity>> imagesByActionId,
                                               StoryWeightPeriod weightPeriod) {
        Map<String, List<SmallSceneEntity>> scenesByBigSceneId = smallScenes.stream()
                .collect(Collectors.groupingBy(SmallSceneEntity::getBigSceneId));
        Map<String, List<ActionEntity>> actionsBySmallSceneId = actions.stream()
                .collect(Collectors.groupingBy(ActionEntity::getSmallSceneId));

        return bigScenes.stream()
                .flatMap(bigScene -> scenesByBigSceneId.getOrDefault(bigScene.getId(), List.of()).stream()
                        .map(smallScene -> toCandidate(bigScene, smallScene,
                                actionsBySmallSceneId.getOrDefault(smallScene.getId(), List.of()),
                                imagesByActionId, weightPeriod)))
                .toList();
    }

    private StorySceneCandidate toCandidate(BigSceneEntity bigScene, SmallSceneEntity smallScene,
                                            List<ActionEntity> actions,
                                            Map<String, List<ActionImageEntity>> imagesByActionId,
                                            StoryWeightPeriod weightPeriod) {
        List<StoryActionCandidate> candidates = actions.stream()
                .map(action -> toCandidate(action, imagesByActionId.getOrDefault(action.getId(), List.of())))
                .filter(candidate -> !candidate.images().isEmpty())
                .toList();
        return new StorySceneCandidate(bigScene.getId(), bigScene.getName(), smallScene.getId(), smallScene.getName(),
                weightOf(smallScene, weightPeriod), candidates);
    }

    private StoryActionCandidate toCandidate(ActionEntity action, List<ActionImageEntity> images) {
        return new StoryActionCandidate(action.getId(), action.getName(), valueOrZero(action.getDurationMin()),
                valueOrZero(action.getDurationMax()), images.stream().map(this::toCandidate).toList());
    }

    private StoryImageCandidate toCandidate(ActionImageEntity image) {
        return new StoryImageCandidate(image.getId(), image.getImageUrl(), image.getCaptions());
    }

    private int weightOf(SmallSceneEntity scene, StoryWeightPeriod period) {
        Integer weight = switch (period) {
            case NIGHT -> scene.getWeightNight();
            case MORNING -> scene.getWeightMorning();
            case AFTERNOON -> scene.getWeightAfternoon();
            case EVENING -> scene.getWeightEvening();
        };
        return valueOrZero(weight);
    }

    private boolean isEnabled(BigSceneEntity entity) {
        return Integer.valueOf(ENABLED).equals(entity.getStatus());
    }

    private boolean isEnabled(SmallSceneEntity entity) {
        return Integer.valueOf(ENABLED).equals(entity.getStatus());
    }

    private boolean isEnabled(ActionEntity entity) {
        return Integer.valueOf(ENABLED).equals(entity.getStatus());
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static <T> Comparator<T> bySortOrderAndId(Function<T, Integer> sortOrder, Function<T, String> id) {
        return Comparator.comparing(sortOrder, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(id, Comparator.nullsFirst(Comparator.naturalOrder()));
    }
}
