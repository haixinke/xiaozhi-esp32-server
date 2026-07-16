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

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.config.PetAvatarProperties;
import xiaozhi.modules.pet.config.PetCollectionCardProperties;
import xiaozhi.modules.pet.dao.MemoryDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.UserProfileDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.CollectionCardImageService;
import xiaozhi.modules.pet.vo.PetVO;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetService.hatch 破壳测试")
class PetServiceImplHatchTest {

    private static final Long USER_ID = 1001L;
    private static final String PET_ID = "pet-egg-1";
    private static final String AGENT_ID = "agent-uuid-1";

    @BeforeAll
    static void initMessageSource() {
        // RenException(int) 构造会经 MessageUtils 做 i18n 查找，需注入 mock 上下文
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
    @Mock private CollectionCardImageService collectionCardImageService;
    @Mock private xiaozhi.modules.pet.service.PetCollectionCardService petCollectionCardService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PetServiceImpl petService;

    @BeforeEach
    void setUp() {
        PetAvatarProperties avatarProperties = buildAvatarProperties();
        PetCollectionCardProperties collectionCardProperties = buildCollectionCardProperties();
        petService = new PetServiceImpl(petDao, deviceDao, llmService, chatHistoryDao,
                memoryDao, userProfileDao, inviteService, agentService, eventPublisher, avatarProperties, collectionCardProperties, petCollectionCardService);
        when(petCollectionCardService.listByPetId(anyString())).thenReturn(java.util.List.of());
        // LLM 不可用 → deriveMbti 走兜底(INFP)，不调 LLM
        when(llmService.isAvailable()).thenReturn(false);
        when(agentService.createAgent(any())).thenReturn(AGENT_ID);
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

    private PetEntity eggPetReady() {
        PetEntity pet = new PetEntity();
        pet.setId(PET_ID);
        pet.setUserId(USER_ID);
        pet.setHatchStatus("EGG");
        pet.setPrototype("锦鲤");
        pet.setNickname("小金鱼");
        pet.setAcceleratedMinutes(0);
        pet.setCreateDate(new Date(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000));
        // adopt 已设的时间基线：起点为过去，到点时间为过去(可破)
        Date hatchStart = new Date(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000);
        pet.setHatchStartTime(hatchStart);
        pet.setExpectedHatchTime(new Date(System.currentTimeMillis() - 1000));
        return pet;
    }

    @Test
    @DisplayName("happy path - EGG且到点: 破壳成功, 建 agent+设备, 回填档案, 返回 HATCHED")
    void hatch_eggReached_success() {
        PetEntity pet = eggPetReady();
        when(petDao.selectByIdForUpdate(PET_ID)).thenReturn(pet);

        PetVO result = petService.hatch(USER_ID, PET_ID);

        // agent: createAgent 被调, system_prompt 为模板渲染结果
        ArgumentCaptor<UpdateWrapper<AgentEntity>> agentUpdateCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(agentService).createAgent(any());
        verify(agentService).update(eq(null), agentUpdateCaptor.capture());
        String injectedSystemPrompt = extractSystemPrompt(agentUpdateCaptor.getValue());
        assertThat(injectedSystemPrompt)
                .contains("你是小金鱼")
                .contains("锦鲤")
                .contains("INFP");

        // 设备: macAddress==id, agentId 非空, board, autoUpdate=0
        ArgumentCaptor<DeviceEntity> deviceCaptor = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(deviceDao).insert(deviceCaptor.capture());
        DeviceEntity device = deviceCaptor.getValue();
        assertThat(device.getId()).isNotBlank();
        assertThat(device.getMacAddress()).isEqualTo(device.getId());
        assertThat(device.getAgentId()).isEqualTo(AGENT_ID);
        assertThat(device.getBoard()).isEqualTo("wechat-egg-miniprogram");
        assertThat(device.getAutoUpdate()).isEqualTo(0);
        assertThat(device.getUserId()).isEqualTo(USER_ID);
        assertThat(device.getAppVersion()).isEqualTo("1.0.0");
        assertThat(device.getCreator()).isEqualTo(USER_ID);

        // pet 回填: hatchStatus=HATCHED, deviceId/档案已设, personality 不再赋值
        ArgumentCaptor<PetEntity> petCaptor = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).updateById(petCaptor.capture());
        PetEntity updated = petCaptor.getValue();
        assertThat(updated.getHatchStatus()).isEqualTo("HATCHED");
        assertThat(updated.getDeviceId()).isEqualTo(device.getId());
        assertThat(updated.getHatchedAt()).isNotNull();
        assertThat(updated.getBirthDate()).isNotNull();
        assertThat(updated.getBazi()).isNotNull();
        assertThat(updated.getWuxing()).isNotNull();
        assertThat(updated.getZodiac()).isNotNull();
        assertThat(updated.getMbti()).isEqualTo("INFP");
        assertThat(updated.getPersonality()).isNull();
        assertThat(updated.getPersonalityBrief()).isNotNull();
        assertThat(updated.getAvatarUrl())
                .isNotNull()
                .matches("^https://oss\\.eggbabe\\.com/default-avatar/(fish|rabbit)/(fish|rabbit)-\\d+\\.png$");
        assertThat(updated.getGender()).isIn("MALE", "FEMALE");
        assertThat(updated.getBloodType()).isIn("A", "B", "O", "AB");
        assertThat(updated.getUpdater()).isEqualTo(USER_ID);

        // 创建破壳首卡
        verify(petCollectionCardService).createCard(eq(PET_ID), anyString(), anyString(), eq("HATCH"));

        // 返回 VO
        assertThat(result.getHatchStatus()).isEqualTo("HATCHED");
        assertThat(result.getDeviceId()).isEqualTo(device.getId());
        assertThat(result.getMbti()).isEqualTo("INFP");

        verify(collectionCardImageService, never()).generate(any(PetEntity.class));
        // CollectionCardGenerationEvent 已暂时禁用，不再发布事件
        verify(eventPublisher, never()).publishEvent(any());
    }

    private String extractSystemPrompt(UpdateWrapper<AgentEntity> wrapper) {
        Map<String, Object> pairs = wrapper.getParamNameValuePairs();
        assertThat(pairs)
                .as("UpdateWrapper 应只设置一个参数")
                .hasSize(1);
        return pairs.values().iterator().next().toString();
    }

    @Test
    @DisplayName("已破壳 - 抛 PET_ALREADY_HATCHED, 不建agent不建设备")
    void hatch_alreadyHatched_throws() {
        PetEntity pet = eggPetReady();
        pet.setHatchStatus("HATCHED");
        when(petDao.selectByIdForUpdate(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> petService.hatch(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_ALREADY_HATCHED));

        verify(agentService, never()).createAgent(any());
        verify(deviceDao, never()).insert(any(DeviceEntity.class));
        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("未到破壳时间 - 抛 PET_HATCH_TIME_NOT_REACHED, 不建agent不建设备")
    void hatch_timeNotReached_throws() {
        PetEntity pet = eggPetReady();
        pet.setExpectedHatchTime(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000));
        when(petDao.selectByIdForUpdate(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> petService.hatch(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_HATCH_TIME_NOT_REACHED));

        verify(agentService, never()).createAgent(any());
        verify(deviceDao, never()).insert(any(DeviceEntity.class));
        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("非属主 - 抛 PET_NO_PERMISSION, 不建agent不建设备")
    void hatch_notOwner_throws() {
        PetEntity pet = eggPetReady();
        pet.setUserId(9999L);
        when(petDao.selectByIdForUpdate(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> petService.hatch(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_NO_PERMISSION));

        verify(agentService, never()).createAgent(any());
        verify(deviceDao, never()).insert(any(DeviceEntity.class));
        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("pet不存在 - 抛 PET_NOT_FOUND, 不建agent不建设备")
    void hatch_notFound_throws() {
        when(petDao.selectByIdForUpdate(PET_ID)).thenReturn(null);

        assertThatThrownBy(() -> petService.hatch(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));

        verify(agentService, never()).createAgent(any());
        verify(deviceDao, never()).insert(any(DeviceEntity.class));
        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("userId 为空 - 抛 USER_NOT_LOGIN, 不建agent不建设备")
    void hatch_nullUserId_throwsAndDoesNothing() {
        assertThatThrownBy(() -> petService.hatch(null, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.USER_NOT_LOGIN));

        verify(agentService, never()).createAgent(any());
        verify(deviceDao, never()).insert(any(DeviceEntity.class));
        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("并发重复破壳 - 第二次看到HATCHED时抛PET_ALREADY_HATCHED, 不再创建agent/device")
    void hatch_concurrentDuplicateSecondCallSeesHatched_throws() {
        PetEntity pet = eggPetReady();
        when(petDao.selectByIdForUpdate(PET_ID))
                .thenReturn(pet)
                .thenReturn(hatchedPetFrom(pet));

        // 第一次成功
        petService.hatch(USER_ID, PET_ID);

        // 第二次并发请求在锁释放后读到已破壳状态
        assertThatThrownBy(() -> petService.hatch(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_ALREADY_HATCHED));

        verify(agentService).createAgent(any());
        verify(deviceDao).insert(any(DeviceEntity.class));
    }

    private PetEntity hatchedPetFrom(PetEntity eggPet) {
        PetEntity hatched = eggPetReady();
        hatched.setId(eggPet.getId());
        hatched.setHatchStatus("HATCHED");
        hatched.setDeviceId("existing-device-id");
        hatched.setHatchedAt(new Date());
        return hatched;
    }
}
