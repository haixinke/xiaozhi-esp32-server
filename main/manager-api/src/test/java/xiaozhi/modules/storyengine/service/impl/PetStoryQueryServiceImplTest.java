package xiaozhi.modules.storyengine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.storyengine.dao.PetStoryHistoryDao;
import xiaozhi.modules.storyengine.dao.PetStoryStateDao;
import xiaozhi.modules.storyengine.entity.PetStoryHistoryEntity;
import xiaozhi.modules.storyengine.entity.PetStoryStateEntity;
import xiaozhi.modules.storyengine.vo.PetStoryHistoryVO;
import xiaozhi.modules.storyengine.vo.PetStoryStateVO;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PetStoryQueryServiceImplTest {

    private PetDao petDao;
    private PetStoryStateDao stateDao;
    private PetStoryHistoryDao historyDao;
    private PetStoryQueryServiceImpl service;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        petDao = mock(PetDao.class);
        stateDao = mock(PetStoryStateDao.class);
        historyDao = mock(PetStoryHistoryDao.class);
        service = new PetStoryQueryServiceImpl(petDao, stateDao, historyDao);
    }

    @Test
    void missingPetThrowsExactNotFoundCodeBeforeStoryQueries() {
        when(petDao.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.getCurrent(7L, "missing"))
                .isInstanceOfSatisfying(RenException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));

        verifyNoInteractions(stateDao, historyDao);
    }

    @Test
    void anotherUsersPetThrowsExactPermissionCodeBeforeStoryQueries() {
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 8L, "HATCHED", "锦鲤"));

        assertThatThrownBy(() -> service.getHistory(7L, "pet-1", Map.of("page", "1", "limit", "10")))
                .isInstanceOfSatisfying(RenException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.PET_NO_PERMISSION));

        verifyNoInteractions(stateDao, historyDao);
    }

    @Test
    void eggReturnsNoCurrentOrHistoryWithoutStoryDaoCalls() {
        when(petDao.selectById("egg-1")).thenReturn(pet("egg-1", 7L, "EGG", "锦鲤"));

        assertThat(service.getCurrent(7L, "egg-1")).isNull();
        PageData<PetStoryHistoryVO> history =
                service.getHistory(7L, "egg-1", Map.of("page", "1", "limit", "10"));

        assertThat(history.getList()).isEmpty();
        assertThat(history.getTotal()).isZero();
        verifyNoInteractions(stateDao, historyDao);
    }

    @Test
    void activeCurrentQueryUsesExactPrototypeAndStatusAndMapsCompleteSnapshot() {
        PetStoryStateEntity state = activeState("锦鲤");
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));
        when(stateDao.selectOne(any())).thenReturn(state);

        PetStoryStateVO result = service.getCurrent(7L, "pet-1");

        assertStateSnapshot(result, state);
        ArgumentCaptor<QueryWrapper<PetStoryStateEntity>> query = queryCaptor();
        verify(stateDao).selectOne(query.capture());
        assertQuery(query.getValue(), List.of("pet_prototype=", "runtime_status="), "锦鲤", "ACTIVE");
    }

    @Test
    void absentOrUninitializedCurrentStateReturnsNull() {
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));
        PetStoryStateEntity uninitialized = activeState("锦鲤");
        uninitialized.setRuntimeStatus("UNINITIALIZED");
        when(stateDao.selectOne(any())).thenReturn(null, uninitialized);

        assertThat(service.getCurrent(7L, "pet-1")).isNull();
        assertThat(service.getCurrent(7L, "pet-1")).isNull();
    }

    @Test
    void samePrototypePetsReadTheSameSharedState() {
        PetStoryStateEntity shared = activeState("锦鲤");
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));
        when(petDao.selectById("pet-2")).thenReturn(pet("pet-2", 7L, "HATCHED", "锦鲤"));
        when(stateDao.selectOne(any())).thenReturn(shared);

        PetStoryStateVO first = service.getCurrent(7L, "pet-1");
        PetStoryStateVO second = service.getCurrent(7L, "pet-2");

        assertThat(first).usingRecursiveComparison().isEqualTo(second);
        ArgumentCaptor<QueryWrapper<PetStoryStateEntity>> queries = queryCaptor();
        verify(stateDao, times(2)).selectOne(queries.capture());
        assertThat(queries.getAllValues()).allSatisfy(
                query -> assertQuery(query, List.of("pet_prototype=", "runtime_status="), "锦鲤", "ACTIVE"));
    }

    @Test
    void differentPrototypesUseSeparateSharedQueries() {
        when(petDao.selectById("fish")).thenReturn(pet("fish", 7L, "HATCHED", "锦鲤"));
        when(petDao.selectById("rabbit")).thenReturn(pet("rabbit", 7L, "HATCHED", "玉兔"));
        when(stateDao.selectOne(any()))
                .thenReturn(activeState("锦鲤"), activeState("玉兔"));

        assertThat(service.getCurrent(7L, "fish").getPetPrototype()).isEqualTo("锦鲤");
        assertThat(service.getCurrent(7L, "rabbit").getPetPrototype()).isEqualTo("玉兔");

        ArgumentCaptor<QueryWrapper<PetStoryStateEntity>> queries = queryCaptor();
        verify(stateDao, times(2)).selectOne(queries.capture());
        assertQuery(queries.getAllValues().get(0),
                List.of("pet_prototype=", "runtime_status="), "锦鲤", "ACTIVE");
        assertQuery(queries.getAllValues().get(1),
                List.of("pet_prototype=", "runtime_status="), "玉兔", "ACTIVE");
    }

    @Test
    void historyClampsPageQueriesPrototypeOrdersStablyAndMapsCompleteSnapshots() {
        PetStoryHistoryEntity stored = historySnapshot("锦鲤");
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));
        doAnswer(invocation -> {
            Page<PetStoryHistoryEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(stored));
            page.setTotal(23);
            return page;
        }).when(historyDao).selectPage(any(Page.class), any(QueryWrapper.class));

        PageData<PetStoryHistoryVO> result =
                service.getHistory(7L, "pet-1", Map.of("page", "-4", "limit", 10));

        assertThat(result.getTotal()).isEqualTo(23);
        assertThat(result.getList()).singleElement().satisfies(vo -> assertHistorySnapshot(vo, stored));

        ArgumentCaptor<Page<PetStoryHistoryEntity>> pageCaptor = pageCaptor();
        ArgumentCaptor<QueryWrapper<PetStoryHistoryEntity>> queryCaptor = queryCaptor();
        verify(historyDao).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
        assertQuery(queryCaptor.getValue(),
                List.of("pet_prototype=", "orderbystarted_atdesc,iddesc"), "锦鲤");
    }

    @Test
    void emptyOrNullParamsUsePageOneAndLimitTen() {
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));
        doAnswer(invocation -> invocation.getArgument(0))
                .when(historyDao).selectPage(any(Page.class), any(QueryWrapper.class));

        service.getHistory(7L, "pet-1", Map.of());
        service.getHistory(7L, "pet-1", null);

        ArgumentCaptor<Page<PetStoryHistoryEntity>> pages = pageCaptor();
        verify(historyDao, times(2)).selectPage(pages.capture(), any(QueryWrapper.class));
        assertThat(pages.getAllValues()).allSatisfy(page -> {
            assertThat(page.getCurrent()).isEqualTo(1);
            assertThat(page.getSize()).isEqualTo(10);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1"})
    void historyRejectsLimitOutsideOneToOneHundred(String limit) {
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));

        assertThatThrownBy(() -> service.getHistory(
                7L, "pet-1", Map.of("page", "1", "limit", limit)))
                .isInstanceOf(RenException.class)
                .hasMessage("分页大小必须在1到100之间");

        verifyNoInteractions(stateDao, historyDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "一百", "999999999999999999999999999"})
    void historyRejectsMalformedOrOverflowingLimitWithValidationError(String limit) {
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));

        assertThatThrownBy(() -> service.getHistory(
                7L, "pet-1", Map.of("page", "1", "limit", limit)))
                .isInstanceOf(RenException.class)
                .hasMessage("分页大小必须在1到100之间");

        verifyNoInteractions(stateDao, historyDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "页", "999999999999999999999999999"})
    void historyRejectsMalformedOrOverflowingPageWithValidationError(String page) {
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));

        assertThatThrownBy(() -> service.getHistory(
                7L, "pet-1", Map.of("page", page, "limit", "10")))
                .isInstanceOf(RenException.class)
                .hasMessage("页码必须是整数");

        verifyNoInteractions(stateDao, historyDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "100"})
    void historyAcceptsInclusiveLimitBoundaries(String limit) {
        when(petDao.selectById("pet-1")).thenReturn(pet("pet-1", 7L, "HATCHED", "锦鲤"));
        doAnswer(invocation -> invocation.getArgument(0))
                .when(historyDao).selectPage(any(Page.class), any(QueryWrapper.class));

        service.getHistory(7L, "pet-1", Map.of("page", 2, "limit", limit));

        ArgumentCaptor<Page<PetStoryHistoryEntity>> page = pageCaptor();
        verify(historyDao).selectPage(page.capture(), any(QueryWrapper.class));
        assertThat(page.getValue().getCurrent()).isEqualTo(2);
        assertThat(page.getValue().getSize()).isEqualTo(Long.parseLong(limit));
    }

    @Test
    void getCurrentByPrototypeReturnsActiveStateWithoutPetLookup() {
        PetStoryStateEntity state = activeState("锦鲤");
        when(stateDao.selectOne(any())).thenReturn(state);

        PetStoryStateVO result = service.getCurrentByPrototype("锦鲤");

        assertStateSnapshot(result, state);
        ArgumentCaptor<QueryWrapper<PetStoryStateEntity>> query = queryCaptor();
        verify(stateDao).selectOne(query.capture());
        assertQuery(query.getValue(), List.of("pet_prototype=", "runtime_status="), "锦鲤", "ACTIVE");
        verifyNoInteractions(petDao, historyDao);
    }

    @Test
    void getCurrentByPrototypeReturnsNullWhenAbsentOrUninitialized() {
        PetStoryStateEntity uninitialized = activeState("锦鲤");
        uninitialized.setRuntimeStatus("UNINITIALIZED");
        when(stateDao.selectOne(any())).thenReturn(null, uninitialized);

        assertThat(service.getCurrentByPrototype("锦鲤")).isNull();
        assertThat(service.getCurrentByPrototype("锦鲤")).isNull();
    }

    @Test
    void getCurrentByPrototypeSkipsQueryWhenPrototypeBlank() {
        assertThat(service.getCurrentByPrototype(null)).isNull();
        assertThat(service.getCurrentByPrototype(" ")).isNull();

        verifyNoInteractions(petDao, stateDao, historyDao);
    }

    private static PetEntity pet(String id, Long userId, String hatchStatus, String prototype) {
        PetEntity pet = new PetEntity();
        pet.setId(id);
        pet.setUserId(userId);
        pet.setHatchStatus(hatchStatus);
        pet.setPrototype(prototype);
        return pet;
    }

    private static PetStoryStateEntity activeState(String prototype) {
        PetStoryStateEntity state = new PetStoryStateEntity();
        state.setPetPrototype(prototype);
        state.setRuntimeStatus("ACTIVE");
        state.setBigSceneId("big-1");
        state.setBigSceneName("书房");
        state.setSmallSceneId("small-1");
        state.setSmallSceneName("窗边");
        state.setActionId("action-1");
        state.setActionName("看书");
        state.setActionImageId("image-1");
        state.setWeightPeriod("MORNING");
        state.setImageTimeOfDay("DAY");
        state.setImageUrl("https://example.test/故事 图.png");
        state.setCaption("读书中，安静一下。");
        state.setDurationHours(3);
        state.setStartedAt(date("2026-08-08T01:00:00Z"));
        state.setExpectedEndAt(date("2026-08-08T04:00:00Z"));
        return state;
    }

    private static PetStoryHistoryEntity historySnapshot(String prototype) {
        PetStoryStateEntity state = activeState(prototype);
        PetStoryHistoryEntity history = new PetStoryHistoryEntity();
        history.setPetPrototype(state.getPetPrototype());
        history.setBigSceneId(state.getBigSceneId());
        history.setBigSceneName(state.getBigSceneName());
        history.setSmallSceneId(state.getSmallSceneId());
        history.setSmallSceneName(state.getSmallSceneName());
        history.setActionId(state.getActionId());
        history.setActionName(state.getActionName());
        history.setActionImageId(state.getActionImageId());
        history.setWeightPeriod(state.getWeightPeriod());
        history.setImageTimeOfDay(state.getImageTimeOfDay());
        history.setImageUrl(state.getImageUrl());
        history.setCaption(state.getCaption());
        history.setDurationHours(state.getDurationHours());
        history.setStartedAt(state.getStartedAt());
        history.setExpectedEndAt(state.getExpectedEndAt());
        history.setArchivedAt(date("2026-08-08T05:00:00Z"));
        return history;
    }

    private static void assertStateSnapshot(PetStoryStateVO vo, PetStoryStateEntity entity) {
        assertThat(vo.getPetPrototype()).isEqualTo(entity.getPetPrototype());
        assertThat(vo.getBigSceneId()).isEqualTo(entity.getBigSceneId());
        assertThat(vo.getBigSceneName()).isEqualTo(entity.getBigSceneName());
        assertThat(vo.getSmallSceneId()).isEqualTo(entity.getSmallSceneId());
        assertThat(vo.getSmallSceneName()).isEqualTo(entity.getSmallSceneName());
        assertThat(vo.getActionId()).isEqualTo(entity.getActionId());
        assertThat(vo.getActionName()).isEqualTo(entity.getActionName());
        assertThat(vo.getActionImageId()).isEqualTo(entity.getActionImageId());
        assertThat(vo.getWeightPeriod()).isEqualTo(entity.getWeightPeriod());
        assertThat(vo.getImageTimeOfDay()).isEqualTo(entity.getImageTimeOfDay());
        assertThat(vo.getImageUrl()).isEqualTo(entity.getImageUrl());
        assertThat(vo.getCaption()).isEqualTo(entity.getCaption());
        assertThat(vo.getDurationHours()).isEqualTo(entity.getDurationHours());
        assertThat(vo.getStartedAt()).isEqualTo(entity.getStartedAt());
        assertThat(vo.getExpectedEndAt()).isEqualTo(entity.getExpectedEndAt());
    }

    private static void assertHistorySnapshot(PetStoryHistoryVO vo, PetStoryHistoryEntity entity) {
        assertThat(vo.getPetPrototype()).isEqualTo(entity.getPetPrototype());
        assertThat(vo.getBigSceneId()).isEqualTo(entity.getBigSceneId());
        assertThat(vo.getBigSceneName()).isEqualTo(entity.getBigSceneName());
        assertThat(vo.getSmallSceneId()).isEqualTo(entity.getSmallSceneId());
        assertThat(vo.getSmallSceneName()).isEqualTo(entity.getSmallSceneName());
        assertThat(vo.getActionId()).isEqualTo(entity.getActionId());
        assertThat(vo.getActionName()).isEqualTo(entity.getActionName());
        assertThat(vo.getActionImageId()).isEqualTo(entity.getActionImageId());
        assertThat(vo.getWeightPeriod()).isEqualTo(entity.getWeightPeriod());
        assertThat(vo.getImageTimeOfDay()).isEqualTo(entity.getImageTimeOfDay());
        assertThat(vo.getImageUrl()).isEqualTo(entity.getImageUrl());
        assertThat(vo.getCaption()).isEqualTo(entity.getCaption());
        assertThat(vo.getDurationHours()).isEqualTo(entity.getDurationHours());
        assertThat(vo.getStartedAt()).isEqualTo(entity.getStartedAt());
        assertThat(vo.getExpectedEndAt()).isEqualTo(entity.getExpectedEndAt());
        assertThat(vo.getArchivedAt()).isEqualTo(entity.getArchivedAt());
    }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentCaptor<QueryWrapper<T>> queryCaptor() {
        return ArgumentCaptor.forClass(QueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentCaptor<Page<T>> pageCaptor() {
        return ArgumentCaptor.forClass(Page.class);
    }

    private static void assertQuery(QueryWrapper<?> wrapper, List<String> sqlFragments, Object... values) {
        String sql = wrapper.getSqlSegment().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        assertThat(sql).contains(sqlFragments);
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactlyInAnyOrder(values);
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }
}
