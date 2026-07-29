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
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;

import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.config.PetAvatarProperties;
import xiaozhi.modules.pet.config.PetCollectionCardProperties;
import xiaozhi.modules.pet.config.PetSceneProperties;
import xiaozhi.modules.pet.dao.MemoryDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.UserProfileDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.PetCollectionCardService;
import xiaozhi.modules.pet.vo.PetVO;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetService.createEgg 纯数据库建蛋测试")
class PetServiceImplCreateEggTest {

    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
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
    @Mock private PetCollectionCardService petCollectionCardService;
    @Mock private PetSceneProperties petSceneProperties;

    private PetServiceImpl petService;

    @BeforeEach
    void setUp() {
        PetAvatarProperties avatarProperties = buildAvatarProperties();
        PetCollectionCardProperties collectionCardProperties = buildCollectionCardProperties();
        petService = new PetServiceImpl(petDao, deviceDao, llmService, chatHistoryDao,
                memoryDao, userProfileDao, inviteService, agentService, null, eventPublisher,
                avatarProperties, collectionCardProperties, petCollectionCardService, petSceneProperties);
        when(petCollectionCardService.listByPetId(anyString())).thenReturn(java.util.List.of());
    }

    @Test
    @DisplayName("createEgg 使用指定原型建蛋，不触发任何外部调用")
    void createEggUsesRequestedPrototypeWithoutExternalWork() {
        PetVO result = petService.createEgg(1001L, "锦鲤");

        ArgumentCaptor<PetEntity> pet = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).insert(pet.capture());
        assertThat(pet.getValue().getPrototype()).isEqualTo("锦鲤");
        assertThat(pet.getValue().getHatchStatus()).isEqualTo("EGG");
        assertThat(pet.getValue().getDeviceId()).isNull();
        verifyNoInteractions(llmService, agentService, deviceDao, eventPublisher);
        assertThat(result.getId()).isEqualTo(pet.getValue().getId());
    }

    @Test
    @DisplayName("createEgg 玉兔原型正常创建")
    void createEggWithRabbitPrototype() {
        PetVO result = petService.createEgg(1002L, "玉兔");

        ArgumentCaptor<PetEntity> pet = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).insert(pet.capture());
        assertThat(pet.getValue().getPrototype()).isEqualTo("玉兔");
        assertThat(pet.getValue().getHatchStatus()).isEqualTo("EGG");
        assertThat(result.getPrototype()).isEqualTo("玉兔");
    }

    @Test
    @DisplayName("createEgg 非法原型抛异常")
    void createEggInvalidPrototypeRejected() {
        assertThatThrownBy(() -> petService.createEgg(1003L, "恐龙"))
                .isInstanceOf(RenException.class);
        verifyNoInteractions(petDao);
    }

    @Test
    @DisplayName("createEgg null userId 抛异常不建蛋")
    void createEggNullUserRejected() {
        assertThatThrownBy(() -> petService.createEgg(null, "锦鲤"))
                .isInstanceOf(RenException.class);
        verifyNoInteractions(petDao);
    }

    @Test
    @DisplayName("createEgg 设置 7 天孵化基线")
    void createEggSetsSevenDayBaseline() {
        petService.createEgg(1004L, "锦鲤");

        ArgumentCaptor<PetEntity> pet = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).insert(pet.capture());
        PetEntity saved = pet.getValue();
        assertThat(saved.getHatchStartTime()).isNotNull();
        assertThat(saved.getExpectedHatchTime()).isNotNull();
        long span = saved.getExpectedHatchTime().getTime() - saved.getHatchStartTime().getTime();
        assertThat(span).isEqualTo(SEVEN_DAYS_MS);
    }

    @Test
    @DisplayName("createEgg 不生成任何破壳档案")
    void createEggDoesNotCreateHatchProfile() {
        petService.createEgg(1005L, "锦鲤");

        ArgumentCaptor<PetEntity> pet = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).insert(pet.capture());
        PetEntity saved = pet.getValue();
        assertThat(saved.getMbti()).isNull();
        assertThat(saved.getPersonality()).isNull();
        assertThat(saved.getBazi()).isNull();
        assertThat(saved.getWuxing()).isNull();
        assertThat(saved.getZodiac()).isNull();
        assertThat(saved.getAvatarUrl()).isNull();
        assertThat(saved.getGender()).isNull();
        assertThat(saved.getBloodType()).isNull();
        assertThat(saved.getHatchedAt()).isNull();
        assertThat(saved.getDeviceId()).isNull();
    }

    @Test
    @DisplayName("createEgg acceleratedMinutes 初始为 0")
    void createEggAcceleratedMinutesZero() {
        petService.createEgg(1006L, "玉兔");

        ArgumentCaptor<PetEntity> pet = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).insert(pet.capture());
        assertThat(pet.getValue().getAcceleratedMinutes()).isZero();
    }

    @Test
    @DisplayName("createEgg 不调用 refreshTodayMood")
    void createEggDoesNotRefreshMood() {
        petService.createEgg(1007L, "锦鲤");

        // LLM 不会被调用来生成心情
        verifyNoInteractions(llmService);
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

    private PetCollectionCardProperties buildCollectionCardProperties() {
        PetCollectionCardProperties properties = new PetCollectionCardProperties();
        properties.setFallbackUrl("https://oss.eggbabe.com/default-card/fish/card-fish-0.webp");

        PetCollectionCardProperties.Prototype koi = new PetCollectionCardProperties.Prototype();
        koi.setBaseUrl("https://oss.eggbabe.com/default-card/fish/");
        koi.setPrefix("card-fish");
        koi.setCount(10);
        properties.setKoi(koi);

        PetCollectionCardProperties.Prototype rabbit = new PetCollectionCardProperties.Prototype();
        rabbit.setBaseUrl("https://oss.eggbabe.com/default-card/rabbit/");
        rabbit.setPrefix("card-rabbit");
        rabbit.setCount(10);
        properties.setRabbit(rabbit);

        return properties;
    }
}
