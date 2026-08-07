package xiaozhi.modules.pet.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.dao.DuplicateKeyException;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
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

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetService.birth 出生测试")
class PetServiceImplBirthTest {

    private static final Long USER_ID = 1001L;
    private static final String DEVICE_ID = "device-1";

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
        petService = new PetServiceImpl(petDao, deviceDao, llmService, chatHistoryDao,
                memoryDao, userProfileDao, inviteService, agentService, null, eventPublisher,
                new PetAvatarProperties(), new PetCollectionCardProperties(), petCollectionCardService,
                petSceneProperties, null);
        when(llmService.isAvailable()).thenReturn(false);
    }

    @Test
    @DisplayName("账号已有其他设备宠物 - 拒绝出生且不调用 LLM 或创建宠物")
    void birth_accountAlreadyHasPet_throwsAndDoesNotInsert() {
        when(deviceDao.selectById(DEVICE_ID)).thenReturn(boundDevice());
        when(petDao.selectOne(any())).thenReturn(null);
        when(petDao.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> petService.birth(DEVICE_ID))
                .isInstanceOf(RenException.class)
                .satisfies(exception -> assertThat(((RenException) exception).getCode())
                        .isEqualTo(ErrorCode.PET_ALREADY_EXISTS));

        verify(llmService, never()).isAvailable();
        verify(petDao, never()).insert(any(PetEntity.class));
    }

    @Test
    @DisplayName("并发重复创建 - 唯一索引冲突转为账号已有宠物")
    void birth_duplicateUserId_throwsPetAlreadyExists() {
        when(deviceDao.selectById(DEVICE_ID)).thenReturn(boundDevice());
        when(petDao.selectOne(any())).thenReturn(null);
        when(petDao.insert(any(PetEntity.class))).thenThrow(new DuplicateKeyException("duplicate user_id"));

        assertThatThrownBy(() -> petService.birth(DEVICE_ID))
                .isInstanceOf(RenException.class)
                .satisfies(exception -> assertThat(((RenException) exception).getCode())
                        .isEqualTo(ErrorCode.PET_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("同设备已有宠物 - 保留演示更新行为且不新建宠物")
    void birth_sameDevicePet_updatesExistingPetWithoutAccountPrecheck() {
        PetEntity existingPet = new PetEntity();
        existingPet.setId("pet-1");
        existingPet.setUserId(USER_ID);
        existingPet.setDeviceId(DEVICE_ID);
        existingPet.setPrototype("锦鲤");
        existingPet.setNickname("小金鱼");
        when(deviceDao.selectById(DEVICE_ID)).thenReturn(boundDevice());
        when(petDao.selectOne(any())).thenReturn(existingPet);

        petService.birth(DEVICE_ID);

        verify(petDao).updateById(existingPet);
        verify(petDao, never()).exists(any());
        verify(petDao, never()).insert(any(PetEntity.class));
    }

    private DeviceEntity boundDevice() {
        DeviceEntity device = new DeviceEntity();
        device.setId(DEVICE_ID);
        device.setUserId(USER_ID);
        return device;
    }
}
