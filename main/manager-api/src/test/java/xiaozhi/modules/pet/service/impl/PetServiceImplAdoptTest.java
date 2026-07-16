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

import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.invite.service.InviteService;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.config.PetAvatarProperties;
import xiaozhi.modules.pet.config.PetCollectionCardProperties;
import xiaozhi.modules.pet.dao.MemoryDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.UserProfileDao;
import xiaozhi.modules.pet.dto.PetAdoptDTO;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.vo.PetVO;

import java.util.Locale;
import java.util.Set;

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
@DisplayName("PetService.adopt 领养蛋测试")
class PetServiceImplAdoptTest {

    private static final Set<String> PROTOTYPES = Set.of("锦鲤", "玉兔");

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
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private xiaozhi.modules.pet.service.PetCollectionCardService petCollectionCardService;
    @Mock private xiaozhi.modules.pet.config.PetSceneProperties petSceneProperties;

    private PetServiceImpl petService;

    @BeforeEach
    void setUp() {
        PetAvatarProperties avatarProperties = buildAvatarProperties();
        PetCollectionCardProperties collectionCardProperties = buildCollectionCardProperties();
        petService = new PetServiceImpl(petDao, deviceDao, llmService, chatHistoryDao,
                memoryDao, userProfileDao, inviteService, agentService, eventPublisher, avatarProperties, collectionCardProperties, petCollectionCardService, petSceneProperties);
        when(petCollectionCardService.listByPetId(anyString())).thenReturn(java.util.List.of());
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

    @Test
    @DisplayName("有效邀请码 - 创建EGG态蛋并核销邀请码，不生成任何破壳档案")
    void adopt_validInviteCode_createsEggPetAndConsumesCode() {
        // Arrange
        Long userId = 1001L;
        PetAdoptDTO dto = new PetAdoptDTO();
        dto.setInviteCode("EGG-ABCD-1");

        // Act
        PetVO result = petService.adopt(userId, dto);

        // Assert: 蛋已插入
        ArgumentCaptor<PetEntity> captor = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).insert(captor.capture());
        PetEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getHatchStatus()).isEqualTo("EGG");
        assertThat(saved.getDeviceId()).isNull();
        assertThat(saved.getAcceleratedMinutes()).isZero();
        assertThat(PROTOTYPES).contains(saved.getPrototype());
        // 不生成任何破壳档案
        assertThat(saved.getMbti()).isNull();
        assertThat(saved.getPersonality()).isNull();
        assertThat(saved.getBazi()).isNull();
        assertThat(saved.getWuxing()).isNull();
        assertThat(saved.getZodiac()).isNull();
        assertThat(saved.getAvatarUrl()).isNull();
        assertThat(saved.getGender()).isNull();
        assertThat(saved.getBloodType()).isNull();
        assertThat(saved.getHatchedAt()).isNull();
        // Model X: adopt 即为破壳时间基线，已写 hatchStartTime/expectedHatchTime(now+7d)
        assertThat(saved.getHatchStartTime()).isNotNull();
        assertThat(saved.getExpectedHatchTime()).isNotNull();
        long sevenDaysMs = 7L * 24 * 60 * 60 * 1000;
        long span = saved.getExpectedHatchTime().getTime() - saved.getHatchStartTime().getTime();
        assertThat(span).isEqualTo(sevenDaysMs);

        // 邀请码已核销
        verify(inviteService).consume(eq("EGG-ABCD-1"), eq(userId));

        // 返回 VO 与实体一致
        assertThat(result.getHatchStatus()).isEqualTo("EGG");
        assertThat(result.getPrototype()).isEqualTo(saved.getPrototype());
    }

    @Test
    @DisplayName("邀请码前后空白被 trim 后核销")
    void adopt_inviteCodeWithSpaces_isTrimmedBeforeConsume() {
        Long userId = 1002L;
        PetAdoptDTO dto = new PetAdoptDTO();
        dto.setInviteCode("  EGG-XYZ-2  ");

        petService.adopt(userId, dto);

        verify(inviteService).consume(eq("EGG-XYZ-2"), eq(userId));
    }

    @Test
    @DisplayName("prototype 由后端随机，落在锦鲤/玉兔白名单内")
    void adopt_prototype_randomWithinWhitelist() {
        PetAdoptDTO dto = new PetAdoptDTO();
        dto.setInviteCode("CODE-1");

        for (int i = 0; i < 20; i++) {
            PetVO result = petService.adopt(1L, dto);
            assertThat(PROTOTYPES).contains(result.getPrototype());
        }
    }

    @Test
    @DisplayName("无效邀请码 - 异常向上抛出（运行期由 @Transactional 回滚蛋，不产生孤儿蛋）")
    void adopt_invalidInviteCode_propagatesAndNoSafePet() {
        Long userId = 1003L;
        PetAdoptDTO dto = new PetAdoptDTO();
        dto.setInviteCode("BAD-CODE");
        when(inviteService.consume(eq("BAD-CODE"), eq(userId)))
                .thenThrow(new RenException("邀请码无效"));

        // Assert: 异常向上抛出（运行期外层事务将回滚 petDao.insert）
        assertThatThrownBy(() -> petService.adopt(userId, dto))
                .isInstanceOf(RenException.class);

        verify(petDao).insert(any(PetEntity.class));
        // 邀请码核销确实被调用了（失败发生在其内部）
        verify(inviteService).consume(eq("BAD-CODE"), eq(userId));
    }

    @Test
    @DisplayName("userId 为空 - 抛 USER_NOT_LOGIN，不建蛋不核销")
    void adopt_nullUserId_throwsAndDoesNothing() {
        PetAdoptDTO dto = new PetAdoptDTO();
        dto.setInviteCode("CODE-2");

        assertThatThrownBy(() -> petService.adopt(null, dto))
                .isInstanceOf(RenException.class);

        verify(petDao, never()).insert(any(PetEntity.class));
        verify(inviteService, never()).consume(any(), any());
    }
}
