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
import org.springframework.context.MessageSource;

import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.PetCollectionCardService;
import xiaozhi.modules.pet.vo.PetVO;

import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetService updatePet 返回最新 PetVO 测试")
class PetServiceImplUpdatePetTest {

    private static final Long USER_ID = 1001L;
    private static final String PET_ID = "pet-egg-1";

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
    @Mock private PetCollectionCardService petCollectionCardService;

    private PetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PetServiceImpl(petDao, null, null, null, null, null, null, null, null,
                null, null, null, petCollectionCardService, null);
    }

    private PetEntity eggPet() {
        PetEntity pet = new PetEntity();
        pet.setId(PET_ID);
        pet.setUserId(USER_ID);
        pet.setHatchStatus("EGG");
        pet.setHatchStartTime(new Date());
        return pet;
    }

    @Test
    @DisplayName("updatePet - 更新昵称后返回最新 PetVO")
    void updatePet_validNickname_returnsUpdatedPetVO() {
        // Arrange
        PetEntity pet = eggPet();
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        // Act
        PetVO vo = service.updatePet(USER_ID, PET_ID, "小蛋壳");

        // Assert
        assertThat(vo).isNotNull();
        assertThat(vo.getNickname()).isEqualTo("小蛋壳");
        assertThat(vo.getId()).isEqualTo(PET_ID);
    }

    @Test
    @DisplayName("updatePet - 宠物不存在抛异常")
    void updatePet_petNotFound_throws() {
        // Arrange
        when(petDao.selectById(PET_ID)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> service.updatePet(USER_ID, PET_ID, "小蛋壳"))
                .isInstanceOf(RenException.class);
    }

    @Test
    @DisplayName("updatePet - 无权限抛异常")
    void updatePet_noPermission_throws() {
        // Arrange
        PetEntity pet = eggPet();
        pet.setUserId(9999L);
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        // Act & Assert
        assertThatThrownBy(() -> service.updatePet(USER_ID, PET_ID, "小蛋壳"))
                .isInstanceOf(RenException.class);
    }
}
