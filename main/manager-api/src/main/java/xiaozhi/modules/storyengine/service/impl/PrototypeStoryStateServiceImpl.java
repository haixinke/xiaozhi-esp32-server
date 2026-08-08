package xiaozhi.modules.storyengine.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xiaozhi.modules.storyengine.constant.StoryRuntimeStatus;
import xiaozhi.modules.storyengine.dao.PetStoryHistoryDao;
import xiaozhi.modules.storyengine.dao.PetStoryStateDao;
import xiaozhi.modules.storyengine.entity.PetStoryHistoryEntity;
import xiaozhi.modules.storyengine.entity.PetStoryStateEntity;
import xiaozhi.modules.storyengine.model.SelectedStoryState;
import xiaozhi.modules.storyengine.model.StoryEvaluationResult;
import xiaozhi.modules.storyengine.model.StoryPeriodContext;
import xiaozhi.modules.storyengine.model.StorySceneCandidate;
import xiaozhi.modules.storyengine.model.StorySelectionResult;
import xiaozhi.modules.storyengine.model.StorySelectionResultType;
import xiaozhi.modules.storyengine.service.PrototypeStoryStateService;
import xiaozhi.modules.storyengine.service.StoryContentLoader;
import xiaozhi.modules.storyengine.service.StoryPeriodResolver;
import xiaozhi.modules.storyengine.service.StoryStateSelector;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class PrototypeStoryStateServiceImpl implements PrototypeStoryStateService {
    private final PetStoryStateDao stateDao;
    private final PetStoryHistoryDao historyDao;
    private final StoryPeriodResolver periodResolver;
    private final StoryContentLoader contentLoader;
    private final StoryStateSelector selector;
    private final TransactionTemplate transactionTemplate;

    public PrototypeStoryStateServiceImpl(PetStoryStateDao stateDao, PetStoryHistoryDao historyDao,
                                          StoryPeriodResolver periodResolver, StoryContentLoader contentLoader,
                                          StoryStateSelector selector, PlatformTransactionManager transactionManager) {
        this.stateDao = stateDao;
        this.historyDao = historyDao;
        this.periodResolver = periodResolver;
        this.contentLoader = contentLoader;
        this.selector = selector;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public StoryEvaluationResult evaluate(String prototype, ZonedDateTime evaluatedAt) {
        return transactionTemplate.execute(status -> evaluateLocked(prototype, evaluatedAt));
    }

    private StoryEvaluationResult evaluateLocked(String prototype, ZonedDateTime evaluatedAt) {
        PetStoryStateEntity current = stateDao.selectByPrototypeForUpdate(prototype);
        if (current == null) {
            throw new IllegalStateException("缺少宠物原型故事状态占位行");
        }

        Date hourSlot = Date.from(evaluatedAt.truncatedTo(ChronoUnit.HOURS).toInstant());
        if (hourSlot.equals(current.getLastEvaluatedHour())) {
            return StoryEvaluationResult.SKIPPED_ALREADY_EVALUATED;
        }

        StoryPeriodContext period = periodResolver.resolve(evaluatedAt);
        StoryEvaluationResult result = evaluateDueState(current, period, prototype, evaluatedAt);
        current.setLastEvaluatedHour(hourSlot);
        stateDao.updateById(current);
        return result;
    }

    private StoryEvaluationResult evaluateDueState(PetStoryStateEntity current, StoryPeriodContext period,
                                                   String prototype, ZonedDateTime evaluatedAt) {
        Date now = Date.from(evaluatedAt.toInstant());
        if (StoryRuntimeStatus.ACTIVE.name().equals(current.getRuntimeStatus())
                && current.getExpectedEndAt() != null
                && current.getExpectedEndAt().after(now)) {
            return StoryEvaluationResult.KEPT_NOT_DUE;
        }

        List<StorySceneCandidate> candidates = contentLoader.load(prototype, period);
        boolean uninitialized = StoryRuntimeStatus.UNINITIALIZED.name().equals(current.getRuntimeStatus());
        StorySelectionResult selection = uninitialized
                ? selector.selectInitial(candidates)
                : selector.selectTransition(candidates);
        if (selection.type() == StorySelectionResultType.REMAIN) {
            return StoryEvaluationResult.KEPT_REMAINDER;
        }
        if (selection.type() == StorySelectionResultType.INVALID_CONFIGURATION) {
            return StoryEvaluationResult.KEPT_INVALID_CONFIGURATION;
        }

        if (uninitialized) {
            activate(current, selection.state(), prototype, period, now);
            return StoryEvaluationResult.INITIALIZED;
        }

        historyDao.insert(snapshot(current, now));
        activate(current, selection.state(), prototype, period, now);
        return StoryEvaluationResult.SWITCHED;
    }

    private PetStoryHistoryEntity snapshot(PetStoryStateEntity source, Date archivedAt) {
        PetStoryHistoryEntity history = new PetStoryHistoryEntity();
        history.setPetPrototype(source.getPetPrototype());
        history.setBigSceneId(source.getBigSceneId());
        history.setBigSceneName(source.getBigSceneName());
        history.setSmallSceneId(source.getSmallSceneId());
        history.setSmallSceneName(source.getSmallSceneName());
        history.setActionId(source.getActionId());
        history.setActionName(source.getActionName());
        history.setActionImageId(source.getActionImageId());
        history.setWeightPeriod(source.getWeightPeriod());
        history.setImageTimeOfDay(source.getImageTimeOfDay());
        history.setImageUrl(source.getImageUrl());
        history.setCaption(source.getCaption());
        history.setDurationHours(source.getDurationHours());
        history.setStartedAt(source.getStartedAt());
        history.setExpectedEndAt(source.getExpectedEndAt());
        history.setArchivedAt(archivedAt);
        return history;
    }

    private void activate(PetStoryStateEntity target, SelectedStoryState selected, String prototype,
                          StoryPeriodContext period, Date startedAt) {
        target.setPetPrototype(prototype);
        target.setRuntimeStatus(StoryRuntimeStatus.ACTIVE.name());
        target.setBigSceneId(selected.bigSceneId());
        target.setBigSceneName(selected.bigSceneName());
        target.setSmallSceneId(selected.smallSceneId());
        target.setSmallSceneName(selected.smallSceneName());
        target.setActionId(selected.actionId());
        target.setActionName(selected.actionName());
        target.setActionImageId(selected.actionImageId());
        target.setWeightPeriod(period.weightPeriod().name());
        target.setImageTimeOfDay(period.imageTimeOfDay().databaseValue());
        target.setImageUrl(selected.imageUrl());
        target.setCaption(selected.caption());
        target.setDurationHours(selected.durationHours());
        target.setStartedAt(startedAt);
        target.setExpectedEndAt(Date.from(startedAt.toInstant().plus(selected.durationHours(), ChronoUnit.HOURS)));
    }
}
