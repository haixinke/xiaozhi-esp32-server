package xiaozhi.modules.storyengine.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xiaozhi.modules.storyengine.constant.StoryImageTimeOfDay;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;
import xiaozhi.modules.storyengine.dao.ActionDao;
import xiaozhi.modules.storyengine.dao.ActionImageDao;
import xiaozhi.modules.storyengine.dao.BigSceneDao;
import xiaozhi.modules.storyengine.dao.SmallSceneDao;
import xiaozhi.modules.storyengine.entity.ActionEntity;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;
import xiaozhi.modules.storyengine.entity.BigSceneEntity;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;
import xiaozhi.modules.storyengine.model.StoryPeriodContext;
import xiaozhi.modules.storyengine.model.StorySceneCandidate;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryContentLoaderTest {

    @Mock
    private BigSceneDao bigSceneDao;

    @Mock
    private SmallSceneDao smallSceneDao;

    @Mock
    private ActionDao actionDao;

    @Mock
    private ActionImageDao actionImageDao;

    @Test
    void loadsEnabledContentInStableOrderAndKeepsScenesWithoutEligibleActions() {
        when(bigSceneDao.selectList(any())).thenReturn(List.of(
                bigScene("big-b", "B大场景", 1, 1),
                bigScene("big-disabled", "禁用大场景", 0, 0),
                bigScene("big-a", "A大场景", 1, 1)));
        when(smallSceneDao.selectList(any())).thenReturn(List.of(
                smallScene("small-b", "big-b", "B小场景", 1, 2, 10, 20, 30, 40),
                smallScene("small-disabled", "big-a", "禁用小场景", 0, 0, 99, 99, 99, 99),
                smallScene("small-a", "big-a", "无图小场景", 1, 2, 40, 30, 20, 10),
                smallScene("small-before-b", "big-b", "B前小场景", 1, 1, 10, 20, 30, 40)));
        when(actionDao.selectList(any())).thenReturn(List.of(
                action("action-disabled", "small-b", "禁用动作", 0, 0, 1, 2),
                action("action-b", "small-b", "看书", 1, 2, 1, 2),
                action("action-a", "small-b", "散步", 1, 1, 1, 1)));
        when(actionImageDao.selectList(any())).thenReturn(List.of(
                image("image-wrong-prototype", "action-b", "玉兔", "白天", "wrong-prototype", "x", 0),
                image("image-wrong-time", "action-b", "锦鲤", "黑夜", "wrong-time", "x", 0),
                image("image-b", "action-b", "锦鲤", "白天", "day-url", "早安", 0),
                image("image-a", "action-a", "锦鲤", "白天", "walk-url", "散步", 0)));

        List<StorySceneCandidate> result = loader().load("锦鲤",
                new StoryPeriodContext(StoryWeightPeriod.MORNING, StoryImageTimeOfDay.DAY));

        assertThat(result).extracting(StorySceneCandidate::bigSceneName, StorySceneCandidate::smallSceneName,
                        StorySceneCandidate::weight)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A大场景", "无图小场景", 30),
                        org.assertj.core.groups.Tuple.tuple("B大场景", "B前小场景", 20),
                        org.assertj.core.groups.Tuple.tuple("B大场景", "B小场景", 20));
        assertThat(result.get(0).actions()).isEmpty();
        assertThat(result.get(1).actions()).isEmpty();
        assertThat(result.get(2).actions()).extracting(action -> action.id(), action -> action.name())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("action-a", "散步"),
                        org.assertj.core.groups.Tuple.tuple("action-b", "看书"));
        assertThat(result.get(2).actions().get(1).images())
                .extracting(image -> image.id(), image -> image.imageUrl(), image -> image.captions())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("image-b", "day-url", "早安"));
        verify(bigSceneDao, times(1)).selectList(any());
        verify(smallSceneDao, times(1)).selectList(any());
        verify(actionDao, times(1)).selectList(any());
        verify(actionImageDao, times(1)).selectList(any());
    }

    @ParameterizedTest
    @MethodSource("weightPeriods")
    void selectsTheRequestedPeriodWeightAndTreatsNullAsZero(StoryWeightPeriod weightPeriod, int expectedWeight) {
        when(bigSceneDao.selectList(any())).thenReturn(List.of(bigScene("big", "大场景", 1, 0)));
        when(smallSceneDao.selectList(any())).thenReturn(List.of(
                smallScene("small", "big", "小场景", 1, 0, 11, 22, 33, null)));
        when(actionDao.selectList(any())).thenReturn(List.of());

        List<StorySceneCandidate> result = loader().load("锦鲤",
                new StoryPeriodContext(weightPeriod, StoryImageTimeOfDay.DAY));

        assertThat(result).singleElement().extracting(StorySceneCandidate::weight).isEqualTo(expectedWeight);
        verify(bigSceneDao, times(1)).selectList(any());
        verify(smallSceneDao, times(1)).selectList(any());
        verify(actionDao, times(1)).selectList(any());
        verify(actionImageDao, never()).selectList(any());
    }

    @Test
    void stopsBeforeChildrenWhenNoEnabledBigScenesRemain() {
        when(bigSceneDao.selectList(any())).thenReturn(List.of(bigScene("big", "禁用", 0, 0)));

        assertThat(loader().load("锦鲤", new StoryPeriodContext(StoryWeightPeriod.NIGHT, StoryImageTimeOfDay.NIGHT)))
                .isEmpty();

        verify(bigSceneDao, times(1)).selectList(any());
        verify(smallSceneDao, never()).selectList(any());
        verify(actionDao, never()).selectList(any());
        verify(actionImageDao, never()).selectList(any());
    }

    @Test
    void stopsBeforeActionsWhenNoEnabledSmallScenesRemain() {
        when(bigSceneDao.selectList(any())).thenReturn(List.of(bigScene("big", "大场景", 1, 0)));
        when(smallSceneDao.selectList(any())).thenReturn(List.of(
                smallScene("small", "big", "禁用小场景", 0, 0, 1, 1, 1, 1)));

        assertThat(loader().load("锦鲤", new StoryPeriodContext(StoryWeightPeriod.NIGHT, StoryImageTimeOfDay.NIGHT)))
                .isEmpty();

        verify(bigSceneDao, times(1)).selectList(any());
        verify(smallSceneDao, times(1)).selectList(any());
        verify(actionDao, never()).selectList(any());
        verify(actionImageDao, never()).selectList(any());
    }

    private StoryContentLoader loader() {
        return new StoryContentLoader(bigSceneDao, smallSceneDao, actionDao, actionImageDao);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> weightPeriods() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(StoryWeightPeriod.NIGHT, 11),
                org.junit.jupiter.params.provider.Arguments.of(StoryWeightPeriod.MORNING, 22),
                org.junit.jupiter.params.provider.Arguments.of(StoryWeightPeriod.AFTERNOON, 33),
                org.junit.jupiter.params.provider.Arguments.of(StoryWeightPeriod.EVENING, 0));
    }

    private static BigSceneEntity bigScene(String id, String name, int status, int sortOrder) {
        BigSceneEntity entity = new BigSceneEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setStatus(status);
        entity.setSortOrder(sortOrder);
        return entity;
    }

    private static SmallSceneEntity smallScene(String id, String bigSceneId, String name, int status, int sortOrder,
                                                Integer night, Integer morning, Integer afternoon, Integer evening) {
        SmallSceneEntity entity = new SmallSceneEntity();
        entity.setId(id);
        entity.setBigSceneId(bigSceneId);
        entity.setName(name);
        entity.setStatus(status);
        entity.setSortOrder(sortOrder);
        entity.setWeightNight(night);
        entity.setWeightMorning(morning);
        entity.setWeightAfternoon(afternoon);
        entity.setWeightEvening(evening);
        return entity;
    }

    private static ActionEntity action(String id, String smallSceneId, String name, int status, int sortOrder,
                                       int durationMin, int durationMax) {
        ActionEntity entity = new ActionEntity();
        entity.setId(id);
        entity.setSmallSceneId(smallSceneId);
        entity.setName(name);
        entity.setStatus(status);
        entity.setSortOrder(sortOrder);
        entity.setDurationMin(durationMin);
        entity.setDurationMax(durationMax);
        return entity;
    }

    private static ActionImageEntity image(String id, String actionId, String prototype, String timeOfDay,
                                           String imageUrl, String captions, int sortOrder) {
        ActionImageEntity entity = new ActionImageEntity();
        entity.setId(id);
        entity.setActionId(actionId);
        entity.setPetPrototype(prototype);
        entity.setTimeOfDay(timeOfDay);
        entity.setImageUrl(imageUrl);
        entity.setCaptions(captions);
        entity.setSortOrder(sortOrder);
        return entity;
    }
}
