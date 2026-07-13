package xiaozhi.modules.pet.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.config.PetAvatarProperties;
import xiaozhi.modules.pet.constant.MoodLinePool;
import xiaozhi.modules.pet.constant.TodayMood;
import xiaozhi.modules.pet.dao.MemoryDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.UserProfileDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.util.MoodDecider;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetService 今日心情懒生成")
class PetServiceImplTodayMoodTest {

    private static final String MOOD_ZONE = "Asia/Shanghai";

    @BeforeAll
    static void initMessageSource() {
        // RenException(int) 经 MessageUtils i18n 查找需注入 mock 上下文
        ApplicationContext applicationContext = org.mockito.Mockito.mock(ApplicationContext.class);
        MessageSource messageSource = org.mockito.Mockito.mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PetDao petDao;
    @Mock private DeviceDao deviceDao;
    @Mock private LLMService llmService;
    @Mock private AiAgentChatHistoryDao chatHistoryDao;
    @Mock private MemoryDao memoryDao;
    @Mock private UserProfileDao userProfileDao;
    @Mock private InviteService inviteService;
    @Mock private AgentService agentService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PetServiceImpl petService;

    @BeforeEach
    void setUp() {
        PetAvatarProperties avatarProperties = buildAvatarProperties();
        petService = new PetServiceImpl(petDao, deviceDao, llmService, chatHistoryDao,
                memoryDao, userProfileDao, inviteService, agentService, eventPublisher, avatarProperties);
    }

    private PetAvatarProperties buildAvatarProperties() {
        PetAvatarProperties properties = new PetAvatarProperties();
        properties.setFallbackUrl("https://oss.eggbabe.com/default-avatar/fish/fish-0.png");

        PetAvatarProperties.Prototype koi = new PetAvatarProperties.Prototype();
        koi.setBaseUrl("https://oss.eggbabe.com/default-avatar/fish/");
        koi.setPrefix("fish");
        koi.setCount(22);
        properties.setKoi(koi);

        PetAvatarProperties.Prototype rabbit = new PetAvatarProperties.Prototype();
        rabbit.setBaseUrl("https://oss.eggbabe.com/default-avatar/rabbit/");
        rabbit.setPrefix("rabbit");
        rabbit.setCount(22);
        properties.setRabbit(rabbit);

        return properties;
    }

    private PetEntity hatchedPet() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-1");
        pet.setUserId(1001L);
        pet.setHatchStatus("HATCHED");
        pet.setHatchedAt(new Date(System.currentTimeMillis() - 6L * 60 * 60 * 1000)); // 6小时前破壳
        pet.setMbti("INFP");
        pet.setPersonality("安静温柔，喜欢陪伴。");
        pet.setNickname("小金");
        pet.setPrototype("锦鲤");
        return pet;
    }

    @Test
    @DisplayName("今日已生成(today_mood_date==今日) → 不重生、不写库、不调LLM")
    void refresh_alreadyToday_noOp() {
        PetEntity pet = hatchedPet();
        LocalDate today = LocalDate.now(ZoneId.of(MOOD_ZONE));
        pet.setTodayMoodDate(today);
        pet.setTodayMood("开心");
        pet.setTodayMoodSentence("它把快乐摆在了脸上。");

        petService.refreshTodayMood(pet);

        verify(petDao, never()).update(any(), any());
        verify(llmService, never()).generateSummary(anyString(), anyString());
        assertThat(pet.getTodayMood()).isEqualTo("开心");
    }

    @Test
    @DisplayName("首次生成(无今日心情) + LLM可用 → 用LLM文案并幂等写回")
    void refresh_firstGen_llmAvailable_writesBack() {
        PetEntity pet = hatchedPet(); // todayMoodDate=null
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.generateSummary(eq(""), anyString())).thenReturn("它今天把想你藏得不太好。");

        petService.refreshTodayMood(pet);

        LocalDate today = LocalDate.now(ZoneId.of(MOOD_ZONE));
        assertThat(pet.getTodayMoodDate()).isEqualTo(today);
        assertThat(pet.getTodayMood()).isNotBlank();
        assertThat(pet.getTodayMoodSentence()).isEqualTo("它今天把想你藏得不太好。");
        verify(petDao).update(any(), any());
    }

    @Test
    @DisplayName("LLM文案超30字 → 截断到30字")
    void refresh_llmTooLong_truncated() {
        PetEntity pet = hatchedPet();
        String overlong = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十";
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.generateSummary(eq(""), anyString())).thenReturn(overlong);

        petService.refreshTodayMood(pet);

        assertThat(pet.getTodayMoodSentence()).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("LLM不可用 → 静态文案池兜底(破壳后池)")
    void refresh_llmUnavailable_staticFallback() {
        PetEntity pet = hatchedPet();
        when(llmService.isAvailable()).thenReturn(false);

        petService.refreshTodayMood(pet);

        // 破壳后、INFP、6小时前破壳 → inactive=0 → 12h内有活跃 → 开心
        assertThat(pet.getTodayMood()).isEqualTo("开心");
        // 文案应来自破壳后开心池
        String expected = MoodLinePool.pick(true, TodayMood.HAPPY,
                LocalDate.now(ZoneId.of(MOOD_ZONE)).toString());
        assertThat(pet.getTodayMoodSentence()).isEqualTo(expected);
        verify(petDao).update(any(), any());
    }

    @Test
    @DisplayName("LLM抛异常 → 静态兜底，不向外抛")
    void refresh_llmThrows_staticFallbackNoPropagation() {
        PetEntity pet = hatchedPet();
        when(llmService.isAvailable()).thenReturn(true);
        when(llmService.generateSummary(eq(""), anyString())).thenThrow(new RuntimeException("llm down"));

        petService.refreshTodayMood(pet);

        assertThat(pet.getTodayMood()).isEqualTo("开心");
        assertThat(pet.getTodayMoodSentence()).isNotBlank();
        verify(petDao).update(any(), any());
    }

    @Test
    @DisplayName("EGG 蛋 LLM不可用 → 走孵化期文案池")
    void refresh_eggPet_usesEggPool() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-egg");
        pet.setUserId(1001L);
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(new Date(System.currentTimeMillis() - 6L * 60 * 60 * 1000));
        pet.setExpectedHatchTime(new Date(System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000));
        pet.setCreateDate(new Date(System.currentTimeMillis() - 6L * 60 * 60 * 1000));
        when(llmService.isAvailable()).thenReturn(false);

        petService.refreshTodayMood(pet);

        // 6小时前开始孵化，inactive=0，未临近破壳(5天后) → 12h内活跃 → 开心
        assertThat(pet.getTodayMood()).isEqualTo("开心");
        String expectedEggLine = MoodLinePool.pick(false, TodayMood.HAPPY,
                LocalDate.now(ZoneId.of(MOOD_ZONE)).toString());
        assertThat(pet.getTodayMoodSentence()).isEqualTo(expectedEggLine);
    }

    @Test
    @DisplayName("listByUserId → 对每只蛋调用 refreshTodayMood(写回)")
    void listByUserId_refreshesEachPet() {
        PetEntity pet = hatchedPet();
        when(llmService.isAvailable()).thenReturn(false);
        when(petDao.selectList(any())).thenReturn(List.of(pet));

        petService.listByUserId(1001L);

        // 至少触发一次 update（今日未生成）
        verify(petDao).update(any(), any());
        assertThat(pet.getTodayMood()).isNotBlank();
    }

    @Test
    @DisplayName("getById → 归属校验通过后刷新今日心情")
    void getById_refreshesMoodAfterOwnership() {
        PetEntity pet = hatchedPet();
        when(petDao.selectById("pet-1")).thenReturn(pet);
        when(llmService.isAvailable()).thenReturn(false);

        petService.getById(1001L, "pet-1");

        verify(petDao).update(any(), any());
        assertThat(pet.getTodayMood()).isNotBlank();
    }
}
