package xiaozhi.modules.pet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.context.MessageSource;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.constant.HatchActionType;
import xiaozhi.modules.pet.dao.HatchActionDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dto.HatchActionDTO;
import xiaozhi.modules.pet.entity.HatchActionEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.PetService;
import xiaozhi.modules.pet.vo.HatchActionResultVO;
import xiaozhi.modules.pet.vo.PetVO;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

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
@DisplayName("HatchActionService 孵化修炼动作测试")
class HatchActionServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final String PET_ID = "pet-egg-1";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long ONE_MINUTE_MS = 60L * 1000;

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
    @Mock private HatchActionDao hatchActionDao;
    @Mock private PetService petService;

    private HatchActionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HatchActionServiceImpl(petDao, hatchActionDao, petService, new ObjectMapper());
        when(petService.toVO(any(PetEntity.class))).thenReturn(new PetVO());
    }

    private PetEntity eggPet() {
        PetEntity pet = new PetEntity();
        pet.setId(PET_ID);
        pet.setUserId(USER_ID);
        pet.setHatchStatus("EGG");
        pet.setAcceleratedMinutes(0);
        return pet;
    }

    private HatchActionDTO dto(String type, Map<String, Object> payload) {
        HatchActionDTO dto = new HatchActionDTO();
        dto.setType(type);
        dto.setPayload(payload);
        return dto;
    }

    @Test
    @DisplayName("NICKNAME - 基于 adopt 已设的 hatchStartTime 重算 expectedHatchTime(更早), 不写 hatchStartTime, acceleratedMinutes=720")
    void firstNickname_recomputesExpectedWithoutWritingStart() {
        PetEntity pet = eggPet();
        // 模拟 adopt 已设的时间基线
        long startTs = 1_700_000_000_000L;
        Date hatchStart = new Date(startTs);
        pet.setHatchStartTime(hatchStart);
        pet.setExpectedHatchTime(new Date(startTs + SEVEN_DAYS_MS));
        when(petDao.selectById(PET_ID)).thenReturn(pet);
        when(hatchActionDao.selectCount(any())).thenReturn(0L);

        HatchActionDTO dto = dto("NICKNAME", Map.of("nickname", "小金鱼"));

        long originalExpected = pet.getExpectedHatchTime().getTime();
        HatchActionResultVO result = service.recordHatchAction(USER_ID, PET_ID, dto);

        assertThat(result.getAddedMinutes()).isEqualTo(720);
        assertThat(result.isAlreadyDone()).isFalse();

        ArgumentCaptor<PetEntity> petCaptor = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).updateById(petCaptor.capture());
        PetEntity updated = petCaptor.getValue();
        // 动作不写 hatchStartTime(adopt 已设)
        assertThat(updated.getHatchStartTime()).isEqualTo(hatchStart);
        assertThat(updated.getExpectedHatchTime()).isNotNull();
        long span = updated.getExpectedHatchTime().getTime() - updated.getHatchStartTime().getTime();
        // expected = start + 7d - 720min => 提前 12h
        assertThat(span).isEqualTo(SEVEN_DAYS_MS - 720 * ONE_MINUTE_MS);
        assertThat(updated.getAcceleratedMinutes()).isEqualTo(720);
        assertThat(updated.getNickname()).isEqualTo("小金鱼");
        assertThat(updated.getUpdater()).isEqualTo(USER_ID);
        // 比动作前更早(加速使其提前)
        assertThat(updated.getExpectedHatchTime().getTime()).isLessThan(originalExpected);

        ArgumentCaptor<HatchActionEntity> actCaptor = ArgumentCaptor.forClass(HatchActionEntity.class);
        verify(hatchActionDao).insert(actCaptor.capture());
        HatchActionEntity act = actCaptor.getValue();
        assertThat(act.getActionType()).isEqualTo("NICKNAME");
        assertThat(act.getAcceleratedMinutes()).isEqualTo(720);
        assertThat(act.getCreator()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("后续WISH - acceleratedMinutes累加60, expectedHatchTime重算变小, added=60")
    void subsequentWish_accumulatesAndRecomputesExpected() {
        PetEntity pet = eggPet();
        long startTs = 1_700_000_000_000L;
        pet.setHatchStartTime(new Date(startTs));
        pet.setAcceleratedMinutes(720);
        pet.setExpectedHatchTime(new Date(startTs + SEVEN_DAYS_MS - 720 * ONE_MINUTE_MS));
        when(petDao.selectById(PET_ID)).thenReturn(pet);
        when(hatchActionDao.selectCount(any())).thenReturn(0L);

        HatchActionDTO dto = dto("WISH", null);

        long originalExpected = pet.getExpectedHatchTime().getTime();
        HatchActionResultVO result = service.recordHatchAction(USER_ID, PET_ID, dto);

        assertThat(result.getAddedMinutes()).isEqualTo(60);

        ArgumentCaptor<PetEntity> petCaptor = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).updateById(petCaptor.capture());
        PetEntity updated = petCaptor.getValue();
        assertThat(updated.getAcceleratedMinutes()).isEqualTo(780);
        long expected = updated.getExpectedHatchTime().getTime();
        long baseExpected = startTs + SEVEN_DAYS_MS - 780 * ONE_MINUTE_MS;
        assertThat(expected).isEqualTo(Math.max(baseExpected, startTs));
        // 比动作前更早(加速使其提前)
        assertThat(expected).isLessThan(originalExpected);

        verify(hatchActionDao).insert(any(HatchActionEntity.class));
    }

    @Test
    @DisplayName("每日重复WISH(同天) - alreadyDone=true, added=0, 不update不insert")
    void duplicateDailyWish_isIdempotent() {
        PetEntity pet = eggPet();
        pet.setHatchStartTime(new Date());
        when(petDao.selectById(PET_ID)).thenReturn(pet);
        when(hatchActionDao.selectCount(any())).thenReturn(1L);

        HatchActionResultVO result = service.recordHatchAction(USER_ID, PET_ID, dto("WISH", null));

        assertThat(result.isAlreadyDone()).isTrue();
        assertThat(result.getAddedMinutes()).isZero();

        verify(petDao, never()).updateById(any(PetEntity.class));
        verify(hatchActionDao, never()).insert(any(HatchActionEntity.class));
    }

    @Test
    @DisplayName("一次性重复NICKNAME - alreadyDone=true, added=0")
    void duplicateOneTimeNickname_isIdempotent() {
        PetEntity pet = eggPet();
        pet.setHatchStartTime(new Date());
        pet.setNickname("小金鱼");
        when(petDao.selectById(PET_ID)).thenReturn(pet);
        when(hatchActionDao.selectCount(any())).thenReturn(1L);

        HatchActionResultVO result = service.recordHatchAction(USER_ID, PET_ID, dto("NICKNAME", Map.of("nickname", "新名字")));

        assertThat(result.isAlreadyDone()).isTrue();
        assertThat(result.getAddedMinutes()).isZero();

        verify(petDao, never()).updateById(any(PetEntity.class));
        verify(hatchActionDao, never()).insert(any(HatchActionEntity.class));
    }

    @Test
    @DisplayName("已破壳pet - 抛 PET_ALREADY_HATCHED")
    void hatchedPet_throwsAlreadyHatched() {
        PetEntity pet = eggPet();
        pet.setHatchStatus("HATCHED");
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> service.recordHatchAction(USER_ID, PET_ID, dto("WISH", null)))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_ALREADY_HATCHED));

        verify(petDao, never()).updateById(any(PetEntity.class));
        verify(hatchActionDao, never()).insert(any(HatchActionEntity.class));
    }

    @Test
    @DisplayName("非属主 - 抛 PET_NO_PERMISSION")
    void notOwner_throwsNoPermission() {
        PetEntity pet = eggPet();
        pet.setUserId(9999L);
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> service.recordHatchAction(USER_ID, PET_ID, dto("WISH", null)))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.PET_NO_PERMISSION));

        verify(petDao, never()).updateById(any(PetEntity.class));
        verify(hatchActionDao, never()).insert(any(HatchActionEntity.class));
    }

    @Test
    @DisplayName("NICKNAME超10字符 - 抛 RenException, 不update不insert")
    void nicknameTooLong_throws() {
        PetEntity pet = eggPet();
        when(petDao.selectById(PET_ID)).thenReturn(pet);
        when(hatchActionDao.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.recordHatchAction(USER_ID, PET_ID, dto("NICKNAME", Map.of("nickname", "12345678901"))))
                .isInstanceOf(RenException.class);

        verify(petDao, never()).updateById(any(PetEntity.class));
        verify(hatchActionDao, never()).insert(any(HatchActionEntity.class));
    }

    @Test
    @DisplayName("不支持type - 抛 RenException(\"不支持的动作类型\")")
    void unsupportedType_throws() {
        PetEntity pet = eggPet();
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        assertThatThrownBy(() -> service.recordHatchAction(USER_ID, PET_ID, dto("UNKNOWN", null)))
                .isInstanceOf(RenException.class)
                .hasMessage("不支持的动作类型");

        verify(petDao, never()).updateById(any(PetEntity.class));
        verify(hatchActionDao, never()).insert(any(HatchActionEntity.class));
    }

    @Test
    @DisplayName("null userId - 抛 USER_NOT_LOGIN, 不update不insert")
    void nullUserId_throwsAndDoesNothing() {
        assertThatThrownBy(() -> service.recordHatchAction(null, PET_ID, dto("WISH", null)))
                .isInstanceOf(RenException.class)
                .satisfies(ex -> assertThat(((RenException) ex).getCode()).isEqualTo(ErrorCode.USER_NOT_LOGIN));

        verify(petDao, never()).updateById(any(PetEntity.class));
        verify(hatchActionDao, never()).insert(any(HatchActionEntity.class));
    }

    // 静态引用, 防止 IDE 误判 import 未使用
    @SuppressWarnings("unused")
    private HatchActionType unused() {
        return HatchActionType.NICKNAME;
    }
}
