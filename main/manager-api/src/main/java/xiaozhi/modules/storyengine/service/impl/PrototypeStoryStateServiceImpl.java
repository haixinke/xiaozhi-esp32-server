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

/**
 * 原型级共享故事状态服务实现。
 * 并发语义：原型行锁（FOR UPDATE）+ last_evaluated_hour 时槽标记，二者共同保证
 * 多实例下每个原型、每个整点时槽最多完成一次有效计算。概率未命中也提交时槽标记，
 * 避免其他实例在同一小时获得额外抽取机会。
 */
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
        // 行锁读取，保证多实例串行处理同一原型
        PetStoryStateEntity current = stateDao.selectByPrototypeForUpdate(prototype);
        if (current == null) {
            throw new IllegalStateException("缺少宠物原型故事状态占位行");
        }

        // 时槽幂等：本整点已计算过（含更晚的水位）则跳过
        Date hourSlot = Date.from(evaluatedAt.truncatedTo(ChronoUnit.HOURS).toInstant());
        Date lastEvaluatedHour = current.getLastEvaluatedHour();
        if (lastEvaluatedHour != null && !lastEvaluatedHour.before(hourSlot)) {
            return StoryEvaluationResult.SKIPPED_ALREADY_EVALUATED;
        }

        StoryRuntimeStatus runtimeStatus = requireRuntimeStatus(current.getRuntimeStatus());
        if (runtimeStatus == StoryRuntimeStatus.ACTIVE) {
            validateActiveSnapshot(current);
        }
        StoryPeriodContext period = periodResolver.resolve(evaluatedAt);
        StoryEvaluationResult result = evaluateDueState(current, runtimeStatus, period, prototype, evaluatedAt);
        current.setLastEvaluatedHour(hourSlot);
        stateDao.updateById(current);
        return result;
    }

    private StoryEvaluationResult evaluateDueState(PetStoryStateEntity current, StoryRuntimeStatus runtimeStatus,
                                                   StoryPeriodContext period, String prototype,
                                                   ZonedDateTime evaluatedAt) {
        Date now = Date.from(evaluatedAt.toInstant());
        // 动作未到期则保持，不加载候选内容
        if (runtimeStatus == StoryRuntimeStatus.ACTIVE && current.getExpectedEndAt().after(now)) {
            return StoryEvaluationResult.KEPT_NOT_DUE;
        }

        List<StorySceneCandidate> candidates = contentLoader.load(prototype, period);
        StorySelectionResult selection = switch (runtimeStatus) {
            case UNINITIALIZED -> selector.selectInitial(candidates);
            case ACTIVE -> selector.selectTransition(candidates);
        };
        return applySelection(current, runtimeStatus, selection, prototype, period, now);
    }

    private StoryEvaluationResult applySelection(PetStoryStateEntity current, StoryRuntimeStatus runtimeStatus,
                                                 StorySelectionResult selection, String prototype,
                                                 StoryPeriodContext period, Date now) {
        if (selection == null) {
            throw invalidSelection("result");
        }
        StorySelectionResultType type = selection.type();
        if (type == null) {
            throw invalidSelection("type");
        }
        return switch (type) {
            case REMAIN -> StoryEvaluationResult.KEPT_REMAINDER;
            case INVALID_CONFIGURATION -> StoryEvaluationResult.KEPT_INVALID_CONFIGURATION;
            case SELECTED -> applySelected(current, runtimeStatus, selection.state(), prototype, period, now);
        };
    }

    private StoryEvaluationResult applySelected(PetStoryStateEntity current, StoryRuntimeStatus runtimeStatus,
                                                SelectedStoryState selected, String prototype,
                                                StoryPeriodContext period, Date now) {
        if (selected == null) {
            throw invalidSelection("state");
        }
        return switch (runtimeStatus) {
            case UNINITIALIZED -> {
                // 首次初始化：直接激活占位行，不写历史
                activate(current, selected, prototype, period, now);
                yield StoryEvaluationResult.INITIALIZED;
            }
            case ACTIVE -> {
                // 普通切换：先归档旧快照，再写入新状态，同一事务提交
                historyDao.insert(snapshot(current, now));
                activate(current, selected, prototype, period, now);
                yield StoryEvaluationResult.SWITCHED;
            }
        };
    }

    private StoryRuntimeStatus requireRuntimeStatus(String value) {
        if (value == null) {
            throw invalidRuntimeStatus();
        }
        try {
            return StoryRuntimeStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidRuntimeStatus();
        }
    }

    private void validateActiveSnapshot(PetStoryStateEntity current) {
        requireActiveText(current.getPetPrototype(), "petPrototype");
        requireActiveText(current.getBigSceneId(), "bigSceneId");
        requireActiveText(current.getBigSceneName(), "bigSceneName");
        requireActiveText(current.getSmallSceneId(), "smallSceneId");
        requireActiveText(current.getSmallSceneName(), "smallSceneName");
        requireActiveText(current.getActionId(), "actionId");
        requireActiveText(current.getActionName(), "actionName");
        requireActiveText(current.getActionImageId(), "actionImageId");
        requireActiveText(current.getWeightPeriod(), "weightPeriod");
        requireActiveText(current.getImageTimeOfDay(), "imageTimeOfDay");
        requireActiveText(current.getImageUrl(), "imageUrl");
        if (current.getDurationHours() == null || current.getDurationHours() < 1) {
            throw invalidActiveSnapshot("durationHours");
        }
        if (current.getStartedAt() == null) {
            throw invalidActiveSnapshot("startedAt");
        }
        if (current.getExpectedEndAt() == null) {
            throw invalidActiveSnapshot("expectedEndAt");
        }
    }

    private void requireActiveText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalidActiveSnapshot(fieldName);
        }
    }

    private IllegalStateException invalidRuntimeStatus() {
        return new IllegalStateException("故事状态字段无效: runtimeStatus");
    }

    private IllegalStateException invalidActiveSnapshot(String fieldName) {
        return new IllegalStateException("ACTIVE 故事状态快照字段无效: " + fieldName);
    }

    private IllegalStateException invalidSelection(String fieldName) {
        return new IllegalStateException("故事选择结果字段无效: " + fieldName);
    }

    /** 复制当前状态为历史快照，archivedAt 取实际被替换时间（连续未命中时可超过原预计结束时间） */
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

    /** 把选中的新状态写入当前行；expectedEndAt = startedAt + durationHours 小时 */
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
