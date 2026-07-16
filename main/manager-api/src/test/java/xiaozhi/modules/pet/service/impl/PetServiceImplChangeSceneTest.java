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

import xiaozhi.common.exception.ErrorCode;
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

import java.util.Date;
import java.util.Locale;

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
@DisplayName("PetService.changeScene 更换场景测试")
class PetServiceImplChangeSceneTest {

    private static final Long USER_ID = 1001L;
    private static final String PET_ID = "pet-hatched-1";
    private static final String OLD_SCENE_URL = "https://oss.eggbabe.com/default-scenes/fish/scenes-fish-0.jpg";
    private static final String NEW_SCENE_URL = "https://oss.eggbabe.com/default-scenes/fish/scenes-fish-3.jpg";

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
        PetAvatarProperties avatarProperties = new PetAvatarProperties();
        PetCollectionCardProperties collectionCardProperties = new PetCollectionCardProperties();
        petService = new PetServiceImpl(petDao, deviceDao, llmService, chatHistoryDao,
                memoryDao, userProfileDao, inviteService, agentService, eventPublisher,
                avatarProperties, collectionCardProperties, petCollectionCardService, petSceneProperties);
        when(petCollectionCardService.listByPetId(anyString())).thenReturn(java.util.List.of());
    }

    private PetEntity hatchedPet() {
        PetEntity pet = new PetEntity();
        pet.setId(PET_ID);
        pet.setUserId(USER_ID);
        pet.setHatchStatus("HATCHED");
        pet.setPrototype("锦鲤");
        pet.setNickname("小金鱼");
        pet.setCollectionCardUrl(OLD_SCENE_URL);
        pet.setHatchedAt(new Date(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000));
        return pet;
    }

    @Test
    @DisplayName("happy path - 已破壳宠物: 更换场景成功, collectionCardUrl 更新, VO.sceneUrl 映射正确")
    void changeScene_hatched_success() {
        PetEntity pet = hatchedPet();
        when(petDao.selectById(PET_ID)).thenReturn(pet);
        when(petSceneProperties.randomSceneUrl("锦鲤")).thenReturn(NEW_SCENE_URL);

        PetVO result = petService.changeScene(USER_ID, PET_ID);

        ArgumentCaptor<PetEntity> petCaptor = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).updateById(petCaptor.capture());
        PetEntity updated = petCaptor.getValue();
        assertThat(updated.getCollectionCardUrl()).isEqualTo(NEW_SCENE_URL);
        assertThat(updated.getUpdater()).isEqualTo(USER_ID);

        assertThat(result.getSceneUrl()).isEqualTo(NEW_SCENE_URL);
        assertThat(result.getHatchStatus()).isEqualTo("HATCHED");
    }

    @Test
    @DisplayName("去重 - 新URL与当前URL相同时重试, 最终取到不同URL")
    void changeScene_sameUrlRetries() {
        PetEntity pet = hatchedPet();
        when(petDao.selectById(PET_ID)).thenReturn(pet);
        // 第一次返回旧URL(与当前相同), 第二次返回新URL
        when(petSceneProperties.randomSceneUrl("锦鲤"))
                .thenReturn(OLD_SCENE_URL)
                .thenReturn(NEW_SCENE_URL);

        PetVO result = petService.changeScene(USER_ID, PET_ID);

        ArgumentCaptor<PetEntity> petCaptor = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).updateById(petCaptor.capture());
        assertThat(petCaptor.getValue().getCollectionCardUrl()).isEqualTo(NEW_SCENE_URL);
        assertThat(result.getSceneUrl()).isEqualTo(NEW_SCENE_URL);
    }

    @Test
    @DisplayName("未破壳 - 抛业务异常, 不更新数据库")
    void changeScene_notHatched_throws() {
        PetEntity pet = hatchedPet();
        pet.setHatchStatus("EGG");
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> petService.changeScene(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("尚未破壳");

        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("非属主 - 抛 PET_NO_PERMISSION, 不更新数据库")
    void changeScene_notOwner_throws() {
        PetEntity pet = hatchedPet();
        pet.setUserId(9999L);
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> petService.changeScene(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_NO_PERMISSION));

        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("宠物不存在 - 抛 PET_NOT_FOUND, 不更新数据库")
    void changeScene_notFound_throws() {
        when(petDao.selectById(PET_ID)).thenReturn(null);

        assertThatThrownBy(() -> petService.changeScene(USER_ID, PET_ID))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));

        verify(petDao, never()).updateById(any(PetEntity.class));
    }
}
