package xiaozhi.modules.pet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.service.PetCollectionCardService;
import xiaozhi.modules.pet.vo.PetVO;

/**
 * 宠物逻辑删除测试：已删除=不存在（列表过滤、重复删除报错、越权不泄露存在性）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetService 逻辑删除测试")
class PetServiceImplDeleteTest {

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
                null, null, null, petCollectionCardService, null, null, null);
    }

    /** 构造一只未删除的蛋（deletedAt=0），todayMoodDate 置为今天以跳过今日心情懒刷新 */
    private PetEntity alivePet() {
        PetEntity pet = new PetEntity();
        pet.setId(PET_ID);
        pet.setUserId(USER_ID);
        pet.setHatchStatus("EGG");
        pet.setDeletedAt(0L);
        pet.setTodayMoodDate(LocalDate.now());
        return pet;
    }

    @Test
    @DisplayName("deleteByUserId - 本人删除成功，写入删除时间戳而非物理删除")
    void deleteByUserId_owner_marksDeletedAt() {
        // Arrange
        PetEntity pet = alivePet();
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        // Act
        service.deleteByUserId(USER_ID, PET_ID);

        // Assert：逻辑删除——updateById 写回 deletedAt>0，绝不调用物理删除
        ArgumentCaptor<PetEntity> captor = ArgumentCaptor.forClass(PetEntity.class);
        verify(petDao).updateById(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isGreaterThan(0L);
        verify(petDao, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("deleteByUserId - 宠物不存在抛 PET_NOT_FOUND")
    void deleteByUserId_petNotFound_throws() {
        // Arrange
        when(petDao.selectById(PET_ID)).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> service.deleteByUserId(USER_ID, PET_ID))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
    }

    @Test
    @DisplayName("deleteByUserId - 重复删除已删除宠物抛 PET_NOT_FOUND")
    void deleteByUserId_alreadyDeleted_throws() {
        // Arrange：deletedAt>0 表示已删除
        PetEntity pet = alivePet();
        pet.setDeletedAt(System.currentTimeMillis());
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        // Act & Assert
        assertThatThrownBy(() -> service.deleteByUserId(USER_ID, PET_ID))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("deleteByUserId - 非本人删除按 PET_NOT_FOUND 处理，不泄露他人宠物存在性")
    void deleteByUserId_notOwner_throwsPetNotFound() {
        // Arrange
        PetEntity pet = alivePet();
        pet.setUserId(9999L);
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        // Act & Assert
        assertThatThrownBy(() -> service.deleteByUserId(USER_ID, PET_ID))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
        verify(petDao, never()).updateById(any(PetEntity.class));
    }

    @Test
    @DisplayName("listByUserId - 查询条件排除已删除宠物")
    void listByUserId_queryFiltersDeletedPets() {
        // Arrange
        when(petDao.selectList(any(QueryWrapper.class))).thenReturn(List.of(alivePet()));

        // Act
        List<PetVO> result = service.listByUserId(USER_ID);

        // Assert：查询必须带 deleted_at = 0 条件（已删除=不存在）
        ArgumentCaptor<QueryWrapper<PetEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(petDao).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("deleted_at =");
        assertThat(result).hasSize(1);
    }

    /** 构造一只已删除宠物（deletedAt>0），归属当前用户 */
    private PetEntity deletedPet() {
        PetEntity pet = alivePet();
        pet.setDeletedAt(System.currentTimeMillis());
        return pet;
    }

    @Test
    @DisplayName("getById - 已删除宠物按 PET_NOT_FOUND 处理")
    void getById_deletedPet_throwsPetNotFound() {
        when(petDao.selectById(PET_ID)).thenReturn(deletedPet());

        assertThatThrownBy(() -> service.getById(USER_ID, PET_ID))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
    }

    @Test
    @DisplayName("updatePet - 已删除宠物按 PET_NOT_FOUND 处理")
    void updatePet_deletedPet_throwsPetNotFound() {
        when(petDao.selectById(PET_ID)).thenReturn(deletedPet());

        assertThatThrownBy(() -> service.updatePet(USER_ID, PET_ID, "新昵称"))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
    }

    @Test
    @DisplayName("changeScene - 已删除宠物按 PET_NOT_FOUND 处理")
    void changeScene_deletedPet_throwsPetNotFound() {
        when(petDao.selectById(PET_ID)).thenReturn(deletedPet());

        assertThatThrownBy(() -> service.changeScene(USER_ID, PET_ID))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
    }

    @Test
    @DisplayName("hatch - 已删除宠物按 PET_NOT_FOUND 处理")
    void hatch_deletedPet_throwsPetNotFound() {
        when(petDao.selectById(PET_ID)).thenReturn(deletedPet());

        assertThatThrownBy(() -> service.hatch(USER_ID, PET_ID))
                .isInstanceOfSatisfying(RenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PET_NOT_FOUND));
    }
}
