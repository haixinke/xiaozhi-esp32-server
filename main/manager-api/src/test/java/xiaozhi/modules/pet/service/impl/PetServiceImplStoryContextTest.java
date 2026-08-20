package xiaozhi.modules.pet.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.storyengine.service.PetStoryQueryService;
import xiaozhi.modules.storyengine.vo.PetStoryStateVO;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetService 故事状态实时上下文注入")
class PetServiceImplStoryContextTest {

    private static final String DEVICE_ID = "device-1";
    private static final String CONTEXT_KEY = "当前状态";

    @Mock
    private PetDao petDao;
    @Mock
    private PetStoryQueryService petStoryQueryService;

    private PetServiceImpl petService;

    @BeforeEach
    void setUp() {
        petService = new PetServiceImpl(petDao, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, petStoryQueryService);
    }

    /** 今日心情已生成(refreshTodayMood 直接短路),避免依赖 LLM */
    private PetEntity hatchedPet() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-1");
        pet.setUserId(1001L);
        pet.setHatchStatus("HATCHED");
        pet.setPrototype("锦鲤");
        pet.setTodayMood("开心");
        pet.setTodayMoodDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        return pet;
    }

    private PetStoryStateVO activeStoryState() {
        PetStoryStateVO vo = new PetStoryStateVO();
        vo.setPetPrototype("锦鲤");
        vo.setBigSceneName("在家");
        vo.setSmallSceneName("卧室");
        vo.setActionName("小憩");
        return vo;
    }

    @Test
    @DisplayName("ACTIVE 故事状态 → 注入 当前状态=大场景·小场景：动作")
    void context_activeStoryState_injectsFormattedText() {
        when(petDao.selectOne(any())).thenReturn(hatchedPet());
        when(petStoryQueryService.getCurrentByPrototype("锦鲤")).thenReturn(activeStoryState());

        Map<String, String> ctx = petService.buildRealtimeContext(DEVICE_ID);

        assertThat(ctx).containsEntry(CONTEXT_KEY, "在家·卧室：小憩");
    }

    @Test
    @DisplayName("故事状态与今日心情同时注入")
    void context_activeStoryState_coexistsWithMood() {
        when(petDao.selectOne(any())).thenReturn(hatchedPet());
        when(petStoryQueryService.getCurrentByPrototype("锦鲤")).thenReturn(activeStoryState());

        Map<String, String> ctx = petService.buildRealtimeContext(DEVICE_ID);

        assertThat(ctx).containsKey("今日心情").containsKey(CONTEXT_KEY);
    }

    @Test
    @DisplayName("无 ACTIVE 故事状态(查询返回 null) → 不注入当前状态")
    void context_noActiveStoryState_skipsInjection() {
        when(petDao.selectOne(any())).thenReturn(hatchedPet());
        when(petStoryQueryService.getCurrentByPrototype("锦鲤")).thenReturn(null);

        Map<String, String> ctx = petService.buildRealtimeContext(DEVICE_ID);

        assertThat(ctx).doesNotContainKey(CONTEXT_KEY);
    }

    @Test
    @DisplayName("场景字段为空 → 不注入当前状态")
    void context_blankSceneField_skipsInjection() {
        PetStoryStateVO vo = activeStoryState();
        vo.setSmallSceneName(" ");
        when(petDao.selectOne(any())).thenReturn(hatchedPet());
        when(petStoryQueryService.getCurrentByPrototype("锦鲤")).thenReturn(vo);

        Map<String, String> ctx = petService.buildRealtimeContext(DEVICE_ID);

        assertThat(ctx).doesNotContainKey(CONTEXT_KEY);
    }

    @Test
    @DisplayName("故事查询抛异常 → 静默降级,今日心情仍保留")
    void context_storyQueryThrows_moodSurvives() {
        when(petDao.selectOne(any())).thenReturn(hatchedPet());
        when(petStoryQueryService.getCurrentByPrototype(anyString()))
                .thenThrow(new RuntimeException("story engine down"));

        Map<String, String> ctx = petService.buildRealtimeContext(DEVICE_ID);

        assertThat(ctx).doesNotContainKey(CONTEXT_KEY);
        assertThat(ctx).containsKey("今日心情");
    }

    @Test
    @DisplayName("deviceId 为空 → 空上下文,不查询故事状态")
    void context_blankDeviceId_emptyMapNoStoryQuery() {
        Map<String, String> ctx = petService.buildRealtimeContext(" ");

        assertThat(ctx).isEmpty();
        verify(petStoryQueryService, never()).getCurrentByPrototype(anyString());
    }

    @Test
    @DisplayName("宠物不存在 → 空上下文,不查询故事状态")
    void context_petNotFound_emptyMapNoStoryQuery() {
        when(petDao.selectOne(any())).thenReturn(null);

        Map<String, String> ctx = petService.buildRealtimeContext(DEVICE_ID);

        assertThat(ctx).isEmpty();
        verify(petStoryQueryService, never()).getCurrentByPrototype(anyString());
    }
}
