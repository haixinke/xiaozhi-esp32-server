package xiaozhi.modules.storyengine.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import xiaozhi.modules.storyengine.constant.StoryImageTimeOfDay;
import xiaozhi.modules.storyengine.constant.StoryRuntimeStatus;
import xiaozhi.modules.storyengine.constant.StoryWeightPeriod;
import xiaozhi.modules.storyengine.dao.PetStoryHistoryDao;
import xiaozhi.modules.storyengine.dao.PetStoryStateDao;
import xiaozhi.modules.storyengine.entity.PetStoryHistoryEntity;
import xiaozhi.modules.storyengine.entity.PetStoryStateEntity;
import xiaozhi.modules.storyengine.model.SelectedStoryState;
import xiaozhi.modules.storyengine.model.StoryEvaluationResult;
import xiaozhi.modules.storyengine.model.StoryPeriodContext;
import xiaozhi.modules.storyengine.model.StorySceneCandidate;
import xiaozhi.modules.storyengine.model.StorySelectionResult;
import xiaozhi.modules.storyengine.service.StoryContentLoader;
import xiaozhi.modules.storyengine.service.StoryPeriodResolver;
import xiaozhi.modules.storyengine.service.StoryStateSelector;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PrototypeStoryStateServiceImplTest {
    private static final String PROTOTYPE = "锦鲤";
    private static final StoryPeriodContext MORNING =
            new StoryPeriodContext(StoryWeightPeriod.MORNING, StoryImageTimeOfDay.DAY);

    private PetStoryStateDao stateDao;
    private PetStoryHistoryDao historyDao;
    private StoryPeriodResolver periodResolver;
    private StoryContentLoader contentLoader;
    private StoryStateSelector selector;
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transactionStatus;
    private PrototypeStoryStateServiceImpl service;

    @BeforeEach
    void setUp() {
        stateDao = mock(PetStoryStateDao.class);
        historyDao = mock(PetStoryHistoryDao.class);
        periodResolver = mock(StoryPeriodResolver.class);
        contentLoader = mock(StoryContentLoader.class);
        selector = mock(StoryStateSelector.class);
        transactionManager = mock(PlatformTransactionManager.class);
        transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        service = new PrototypeStoryStateServiceImpl(stateDao, historyDao, periodResolver,
                contentLoader, selector, transactionManager);
    }

    @Test
    void initializesPlaceholderWithoutHistoryAndCommits() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:00:00+08:00");
        PetStoryStateEntity current = uninitialized(PROTOTYPE);
        List<StorySceneCandidate> candidates = List.of();
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);
        when(periodResolver.resolve(evaluatedAt)).thenReturn(MORNING);
        when(contentLoader.load(PROTOTYPE, MORNING)).thenReturn(candidates);
        when(selector.selectInitial(candidates)).thenReturn(StorySelectionResult.selected(selected(2)));

        StoryEvaluationResult result = service.evaluate(PROTOTYPE, evaluatedAt);

        assertThat(result).isEqualTo(StoryEvaluationResult.INITIALIZED);
        assertActivated(current, evaluatedAt, 2);
        assertThat(current.getLastEvaluatedHour()).isEqualTo(date("2026-08-08T10:00:00+08:00"));
        verify(historyDao, never()).insert(any(PetStoryHistoryEntity.class));
        verify(selector).selectInitial(candidates);
        verify(selector, never()).selectTransition(any());
        verify(stateDao).updateById(current);
        verifyCommittedOnce();
    }

    @Test
    void archivesCompleteOldSnapshotBeforeReplacingExpiredStateAndCommits() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:00:00+08:00");
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:59:59+08:00");
        PetStoryStateEntity untouched = copy(current);
        List<StorySceneCandidate> candidates = List.of();
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);
        when(periodResolver.resolve(evaluatedAt)).thenReturn(MORNING);
        when(contentLoader.load(PROTOTYPE, MORNING)).thenReturn(candidates);
        when(selector.selectTransition(candidates)).thenReturn(StorySelectionResult.selected(selected(3)));

        assertThat(service.evaluate(PROTOTYPE, evaluatedAt)).isEqualTo(StoryEvaluationResult.SWITCHED);

        ArgumentCaptor<PetStoryHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PetStoryHistoryEntity.class);
        InOrder writes = inOrder(historyDao, stateDao);
        writes.verify(historyDao).insert(historyCaptor.capture());
        writes.verify(stateDao).updateById(current);
        assertHistorySnapshot(historyCaptor.getValue(), untouched, evaluatedAt);
        assertActivated(current, evaluatedAt, 3);
        assertPersistentMetadataUnchanged(current, untouched);
        assertThat(current.getLastEvaluatedHour()).isEqualTo(date("2026-08-08T10:00:00+08:00"));
        verify(selector).selectTransition(candidates);
        verify(selector, never()).selectInitial(any());
        verifyCommittedOnce();
    }

    @Test
    void expectedEndAtEqualToEvaluationTimeIsDue() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:00:00+08:00");
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T10:00:00+08:00");
        List<StorySceneCandidate> candidates = List.of();
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);
        when(periodResolver.resolve(evaluatedAt)).thenReturn(MORNING);
        when(contentLoader.load(PROTOTYPE, MORNING)).thenReturn(candidates);
        when(selector.selectTransition(candidates)).thenReturn(StorySelectionResult.remain());

        assertThat(service.evaluate(PROTOTYPE, evaluatedAt)).isEqualTo(StoryEvaluationResult.KEPT_REMAINDER);

        verify(contentLoader).load(PROTOTYPE, MORNING);
        verify(selector).selectTransition(candidates);
        verifyCommittedOnce();
    }

    @Test
    void samePrototypeAndNormalizedHourIsEvaluatedOnlyOnceAcrossInstances() {
        PetStoryStateEntity current = active("玉兔", "2026-08-08T09:00:00+08:00");
        ZonedDateTime firstEvaluation = at("2026-08-08T10:15:00+08:00");
        ZonedDateTime secondEvaluation = firstEvaluation.plusMinutes(20);
        List<StorySceneCandidate> candidates = List.of();
        when(stateDao.selectByPrototypeForUpdate("玉兔")).thenReturn(current);
        when(periodResolver.resolve(firstEvaluation)).thenReturn(MORNING);
        when(contentLoader.load("玉兔", MORNING)).thenReturn(candidates);
        when(selector.selectTransition(candidates)).thenReturn(StorySelectionResult.remain());
        PrototypeStoryStateServiceImpl secondInstance = new PrototypeStoryStateServiceImpl(
                stateDao, historyDao, periodResolver, contentLoader, selector, transactionManager);

        assertThat(service.evaluate("玉兔", firstEvaluation)).isEqualTo(StoryEvaluationResult.KEPT_REMAINDER);
        assertThat(secondInstance.evaluate("玉兔", secondEvaluation))
                .isEqualTo(StoryEvaluationResult.SKIPPED_ALREADY_EVALUATED);

        assertThat(current.getLastEvaluatedHour()).isEqualTo(date("2026-08-08T10:00:00+08:00"));
        verify(stateDao, times(2)).selectByPrototypeForUpdate("玉兔");
        verify(stateDao, times(1)).updateById(current);
        verify(periodResolver, times(1)).resolve(firstEvaluation);
        verify(periodResolver, times(1)).resolve(any(ZonedDateTime.class));
        verify(contentLoader, times(1)).load("玉兔", MORNING);
        verify(selector, times(1)).selectTransition(candidates);
        verify(transactionManager, times(2)).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void olderHourNeverRegressesCommittedWatermark() {
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        current.setLastEvaluatedHour(date("2026-08-08T11:00:00+08:00"));
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);

        assertThat(service.evaluate(PROTOTYPE, at("2026-08-08T10:35:00+08:00")))
                .isEqualTo(StoryEvaluationResult.SKIPPED_ALREADY_EVALUATED);

        assertThat(current.getLastEvaluatedHour()).isEqualTo(date("2026-08-08T11:00:00+08:00"));
        verify(stateDao, never()).updateById(any(PetStoryStateEntity.class));
        verifyNoInteractions(periodResolver, contentLoader, selector, historyDao);
        verifyCommittedOnce();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "UNKNOWN")
    void invalidRuntimeStatusRollsBackBeforeLoading(String runtimeStatus) {
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        current.setRuntimeStatus(runtimeStatus);
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, at("2026-08-08T10:00:00+08:00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtimeStatus");

        verifyInvalidStateRolledBackBeforeSelection();
    }

    @Test
    void activeWithoutExpectedEndRollsBackBeforeLoading() {
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        current.setExpectedEndAt(null);

        assertInvalidActiveSnapshot(current, "expectedEndAt");
    }

    @Test
    void activeWithoutRequiredActionImageRollsBackBeforeLoading() {
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        current.setActionImageId(null);

        assertInvalidActiveSnapshot(current, "actionImageId");
    }

    @Test
    void futureExpectedEndKeepsCompleteSnapshotWithoutLoadingAndPersistsSlot() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:22:33+08:00");
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T10:22:34+08:00");
        PetStoryStateEntity untouched = copy(current);
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);
        when(periodResolver.resolve(evaluatedAt)).thenReturn(MORNING);

        assertThat(service.evaluate(PROTOTYPE, evaluatedAt)).isEqualTo(StoryEvaluationResult.KEPT_NOT_DUE);

        assertSnapshotUnchangedExceptSlot(current, untouched, "2026-08-08T10:00:00+08:00");
        verifyNoInteractions(contentLoader, selector, historyDao);
        verify(stateDao).updateById(current);
        verifyCommittedOnce();
    }

    @Test
    void remainderKeepsCompleteSnapshotAndPersistsSlot() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:47:00+08:00");
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        PetStoryStateEntity untouched = copy(current);
        List<StorySceneCandidate> candidates = List.of();
        prepareDueSelection(evaluatedAt, current, candidates, StorySelectionResult.remain());

        assertThat(service.evaluate(PROTOTYPE, evaluatedAt)).isEqualTo(StoryEvaluationResult.KEPT_REMAINDER);

        assertSnapshotUnchangedExceptSlot(current, untouched, "2026-08-08T10:00:00+08:00");
        verify(contentLoader, times(1)).load(PROTOTYPE, MORNING);
        verify(selector, times(1)).selectTransition(candidates);
        verifyNoInteractions(historyDao);
        verifyCommittedOnce();
    }

    @Test
    void invalidConfigurationKeepsCompleteSnapshotAndPersistsSlot() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:58:00+08:00");
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        PetStoryStateEntity untouched = copy(current);
        List<StorySceneCandidate> candidates = List.of();
        prepareDueSelection(evaluatedAt, current, candidates, StorySelectionResult.invalid());

        assertThat(service.evaluate(PROTOTYPE, evaluatedAt))
                .isEqualTo(StoryEvaluationResult.KEPT_INVALID_CONFIGURATION);

        assertSnapshotUnchangedExceptSlot(current, untouched, "2026-08-08T10:00:00+08:00");
        verify(contentLoader, times(1)).load(PROTOTYPE, MORNING);
        verify(selector, times(1)).selectTransition(candidates);
        verifyNoInteractions(historyDao);
        verifyCommittedOnce();
    }

    @Test
    void nullInitialSelectionResultRollsBackWithoutWrites() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:00:00+08:00");
        PetStoryStateEntity current = uninitialized(PROTOTYPE);
        List<StorySceneCandidate> candidates = List.of();
        prepareInitialSelection(evaluatedAt, current, candidates, null);

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, evaluatedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("result");

        verifySelectionFailureRolledBackWithoutWrites();
    }

    @Test
    void selectionWithoutTypeRollsBackWithoutWrites() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:00:00+08:00");
        PetStoryStateEntity current = uninitialized(PROTOTYPE);
        List<StorySceneCandidate> candidates = List.of();
        prepareInitialSelection(evaluatedAt, current, candidates,
                new StorySelectionResult(null, selected(2)));

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, evaluatedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type");

        verifySelectionFailureRolledBackWithoutWrites();
    }

    @Test
    void selectedInitialWithoutStateRollsBackWithoutWrites() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:00:00+08:00");
        PetStoryStateEntity current = uninitialized(PROTOTYPE);
        List<StorySceneCandidate> candidates = List.of();
        prepareInitialSelection(evaluatedAt, current, candidates, StorySelectionResult.selected(null));

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, evaluatedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state");

        verifySelectionFailureRolledBackWithoutWrites();
    }

    @Test
    void selectedTransitionWithoutStateRollsBackBeforeHistoryOrCurrentWrite() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:00:00+08:00");
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        List<StorySceneCandidate> candidates = List.of();
        prepareDueSelection(evaluatedAt, current, candidates, StorySelectionResult.selected(null));

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, evaluatedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state");

        verifySelectionFailureRolledBackWithoutWrites();
    }

    @Test
    void daoExceptionRollsBackWithoutCommitAndAllowsDatabaseRetry() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:15:00+08:00");
        PetStoryStateEntity failedAttempt = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        PetStoryStateEntity restoredDatabaseRow = copy(failedAttempt);
        List<StorySceneCandidate> candidates = List.of();
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE))
                .thenReturn(failedAttempt, restoredDatabaseRow);
        when(periodResolver.resolve(evaluatedAt)).thenReturn(MORNING);
        when(contentLoader.load(PROTOTYPE, MORNING)).thenReturn(candidates);
        when(selector.selectTransition(candidates)).thenReturn(StorySelectionResult.remain());
        doThrow(new IllegalStateException("database unavailable"))
                .doReturn(1)
                .when(stateDao).updateById(any(PetStoryStateEntity.class));

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, evaluatedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        assertThat(restoredDatabaseRow.getLastEvaluatedHour()).isNull();
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);

        assertThat(service.evaluate(PROTOTYPE, evaluatedAt)).isEqualTo(StoryEvaluationResult.KEPT_REMAINDER);
        assertThat(restoredDatabaseRow.getLastEvaluatedHour())
                .isEqualTo(date("2026-08-08T10:00:00+08:00"));
        verify(stateDao, times(2)).updateById(any(PetStoryStateEntity.class));
        verify(transactionManager, times(1)).rollback(transactionStatus);
        verify(transactionManager, times(1)).commit(transactionStatus);
    }

    @Test
    void stateUpdateFailureAfterHistoryInsertRollsBackAndPropagates() {
        ZonedDateTime evaluatedAt = at("2026-08-08T10:15:00+08:00");
        PetStoryStateEntity current = active(PROTOTYPE, "2026-08-08T09:00:00+08:00");
        List<StorySceneCandidate> candidates = List.of();
        prepareDueSelection(evaluatedAt, current, candidates, StorySelectionResult.selected(selected(3)));
        doReturn(1).when(historyDao).insert(any(PetStoryHistoryEntity.class));
        doThrow(new IllegalStateException("state update unavailable"))
                .when(stateDao).updateById(current);

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, evaluatedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("state update unavailable");

        InOrder writes = inOrder(historyDao, stateDao);
        writes.verify(historyDao).insert(any(PetStoryHistoryEntity.class));
        writes.verify(stateDao).updateById(current);
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        assertThat(current.getActionId()).isEqualTo("new-action-id");
    }

    @Test
    void missingSeededPrototypeRowThrowsAndRollsBack() {
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(null);

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, at("2026-08-08T10:00:00+08:00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少宠物原型故事状态占位行");

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
        verify(stateDao, never()).updateById(any(PetStoryStateEntity.class));
        verifyNoInteractions(periodResolver, contentLoader, selector, historyDao);
    }

    private void prepareDueSelection(ZonedDateTime evaluatedAt, PetStoryStateEntity current,
                                     List<StorySceneCandidate> candidates, StorySelectionResult selection) {
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);
        when(periodResolver.resolve(evaluatedAt)).thenReturn(MORNING);
        when(contentLoader.load(PROTOTYPE, MORNING)).thenReturn(candidates);
        when(selector.selectTransition(candidates)).thenReturn(selection);
    }

    private void prepareInitialSelection(ZonedDateTime evaluatedAt, PetStoryStateEntity current,
                                         List<StorySceneCandidate> candidates, StorySelectionResult selection) {
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);
        when(periodResolver.resolve(evaluatedAt)).thenReturn(MORNING);
        when(contentLoader.load(PROTOTYPE, MORNING)).thenReturn(candidates);
        when(selector.selectInitial(candidates)).thenReturn(selection);
    }

    private void assertInvalidActiveSnapshot(PetStoryStateEntity current, String fieldName) {
        when(stateDao.selectByPrototypeForUpdate(PROTOTYPE)).thenReturn(current);

        assertThatThrownBy(() -> service.evaluate(PROTOTYPE, at("2026-08-08T10:00:00+08:00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(fieldName);

        verifyInvalidStateRolledBackBeforeSelection();
    }

    private void verifyInvalidStateRolledBackBeforeSelection() {
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
        verify(stateDao, never()).updateById(any(PetStoryStateEntity.class));
        verifyNoInteractions(periodResolver, contentLoader, selector, historyDao);
    }

    private void verifySelectionFailureRolledBackWithoutWrites() {
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
        verify(stateDao, never()).updateById(any(PetStoryStateEntity.class));
        verifyNoInteractions(historyDao);
    }

    private void verifyCommittedOnce() {
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    private static void assertActivated(PetStoryStateEntity current, ZonedDateTime evaluatedAt, int durationHours) {
        assertThat(current.getPetPrototype()).isEqualTo(PROTOTYPE);
        assertThat(current.getRuntimeStatus()).isEqualTo(StoryRuntimeStatus.ACTIVE.name());
        assertThat(current.getBigSceneId()).isEqualTo("new-big-id");
        assertThat(current.getBigSceneName()).isEqualTo("新大场景");
        assertThat(current.getSmallSceneId()).isEqualTo("new-small-id");
        assertThat(current.getSmallSceneName()).isEqualTo("新小场景");
        assertThat(current.getActionId()).isEqualTo("new-action-id");
        assertThat(current.getActionName()).isEqualTo("新动作");
        assertThat(current.getActionImageId()).isEqualTo("new-image-id");
        assertThat(current.getWeightPeriod()).isEqualTo(StoryWeightPeriod.MORNING.name());
        assertThat(current.getImageTimeOfDay()).isEqualTo(StoryImageTimeOfDay.DAY.databaseValue());
        assertThat(current.getImageUrl()).isEqualTo("https://example.com/new.png");
        assertThat(current.getCaption()).isEqualTo("新文案");
        assertThat(current.getDurationHours()).isEqualTo(durationHours);
        assertThat(current.getStartedAt()).isEqualTo(Date.from(evaluatedAt.toInstant()));
        assertThat(current.getExpectedEndAt()).isEqualTo(Date.from(evaluatedAt.plusHours(durationHours).toInstant()));
    }

    private static void assertHistorySnapshot(PetStoryHistoryEntity history, PetStoryStateEntity source,
                                              ZonedDateTime archivedAt) {
        assertThat(history.getPetPrototype()).isEqualTo(source.getPetPrototype());
        assertThat(history.getBigSceneId()).isEqualTo(source.getBigSceneId());
        assertThat(history.getBigSceneName()).isEqualTo(source.getBigSceneName());
        assertThat(history.getSmallSceneId()).isEqualTo(source.getSmallSceneId());
        assertThat(history.getSmallSceneName()).isEqualTo(source.getSmallSceneName());
        assertThat(history.getActionId()).isEqualTo(source.getActionId());
        assertThat(history.getActionName()).isEqualTo(source.getActionName());
        assertThat(history.getActionImageId()).isEqualTo(source.getActionImageId());
        assertThat(history.getWeightPeriod()).isEqualTo(source.getWeightPeriod());
        assertThat(history.getImageTimeOfDay()).isEqualTo(source.getImageTimeOfDay());
        assertThat(history.getImageUrl()).isEqualTo(source.getImageUrl());
        assertThat(history.getCaption()).isEqualTo(source.getCaption());
        assertThat(history.getDurationHours()).isEqualTo(source.getDurationHours());
        assertThat(history.getStartedAt()).isEqualTo(source.getStartedAt());
        assertThat(history.getExpectedEndAt()).isEqualTo(source.getExpectedEndAt());
        assertThat(history.getArchivedAt()).isEqualTo(Date.from(archivedAt.toInstant()));
    }

    private static void assertSnapshotUnchangedExceptSlot(PetStoryStateEntity actual, PetStoryStateEntity before,
                                                          String expectedSlot) {
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("lastEvaluatedHour")
                .isEqualTo(before);
        assertThat(actual.getLastEvaluatedHour()).isEqualTo(date(expectedSlot));
    }

    private static void assertPersistentMetadataUnchanged(PetStoryStateEntity actual,
                                                          PetStoryStateEntity before) {
        assertThat(actual.getId()).isEqualTo(before.getId());
        assertThat(actual.getCreator()).isEqualTo(before.getCreator());
        assertThat(actual.getCreateDate()).isEqualTo(before.getCreateDate());
        assertThat(actual.getUpdater()).isEqualTo(before.getUpdater());
        assertThat(actual.getUpdateDate()).isEqualTo(before.getUpdateDate());
    }

    private static PetStoryStateEntity uninitialized(String prototype) {
        PetStoryStateEntity state = new PetStoryStateEntity();
        state.setId("state-id");
        state.setPetPrototype(prototype);
        state.setRuntimeStatus(StoryRuntimeStatus.UNINITIALIZED.name());
        return state;
    }

    private static PetStoryStateEntity active(String prototype, String expectedEndAt) {
        PetStoryStateEntity state = new PetStoryStateEntity();
        state.setId("state-id");
        state.setPetPrototype(prototype);
        state.setRuntimeStatus(StoryRuntimeStatus.ACTIVE.name());
        state.setBigSceneId("old-big-id");
        state.setBigSceneName("旧大场景");
        state.setSmallSceneId("old-small-id");
        state.setSmallSceneName("旧小场景");
        state.setActionId("old-action-id");
        state.setActionName("旧动作");
        state.setActionImageId("old-image-id");
        state.setWeightPeriod(StoryWeightPeriod.NIGHT.name());
        state.setImageTimeOfDay(StoryImageTimeOfDay.NIGHT.databaseValue());
        state.setImageUrl("https://example.com/old.png");
        state.setCaption("旧文案");
        state.setDurationHours(4);
        state.setStartedAt(date("2026-08-08T06:00:00+08:00"));
        state.setExpectedEndAt(date(expectedEndAt));
        state.setCreator(7L);
        state.setCreateDate(date("2026-08-01T00:00:00+08:00"));
        state.setUpdater(8L);
        state.setUpdateDate(date("2026-08-08T09:00:00+08:00"));
        return state;
    }

    private static PetStoryStateEntity copy(PetStoryStateEntity source) {
        PetStoryStateEntity copy = new PetStoryStateEntity();
        copy.setId(source.getId());
        copy.setPetPrototype(source.getPetPrototype());
        copy.setRuntimeStatus(source.getRuntimeStatus());
        copy.setBigSceneId(source.getBigSceneId());
        copy.setBigSceneName(source.getBigSceneName());
        copy.setSmallSceneId(source.getSmallSceneId());
        copy.setSmallSceneName(source.getSmallSceneName());
        copy.setActionId(source.getActionId());
        copy.setActionName(source.getActionName());
        copy.setActionImageId(source.getActionImageId());
        copy.setWeightPeriod(source.getWeightPeriod());
        copy.setImageTimeOfDay(source.getImageTimeOfDay());
        copy.setImageUrl(source.getImageUrl());
        copy.setCaption(source.getCaption());
        copy.setDurationHours(source.getDurationHours());
        copy.setStartedAt(source.getStartedAt());
        copy.setExpectedEndAt(source.getExpectedEndAt());
        copy.setLastEvaluatedHour(source.getLastEvaluatedHour());
        copy.setCreator(source.getCreator());
        copy.setCreateDate(source.getCreateDate());
        copy.setUpdater(source.getUpdater());
        copy.setUpdateDate(source.getUpdateDate());
        return copy;
    }

    private static SelectedStoryState selected(int durationHours) {
        return new SelectedStoryState("new-big-id", "新大场景", "new-small-id", "新小场景",
                "new-action-id", "新动作", "new-image-id", "https://example.com/new.png",
                "新文案", durationHours);
    }

    private static ZonedDateTime at(String value) {
        return ZonedDateTime.parse(value);
    }

    private static Date date(String value) {
        return Date.from(at(value).toInstant());
    }
}
