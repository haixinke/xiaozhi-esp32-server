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

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.dao.PetPostcardDao;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.entity.PetPostcardEntity;

import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetPostcardService 明信片服务测试")
class PetPostcardServiceImplTest {

    private static final String PET_ID = "pet-1";
    private static final Long USER_ID = 1001L;
    private static final String SHARE_ID = "A2B3C4D5E6F7";

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Mock private PetPostcardDao petPostcardDao;
    @Mock private PetDao petDao;

    private PetPostcardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PetPostcardServiceImpl(petDao);
        // BaseServiceImpl.baseDao 为 @Autowired 字段，单测无 Spring 容器，反射注入 mock dao
        try {
            java.lang.reflect.Field field =
                    xiaozhi.common.service.impl.BaseServiceImpl.class.getDeclaredField("baseDao");
            field.setAccessible(true);
            field.set(service, petPostcardDao);
        } catch (Exception e) {
            throw new IllegalStateException("注入 baseDao 失败", e);
        }
    }

    private PetPostcardEntity sample() {
        PetPostcardEntity entity = new PetPostcardEntity();
        entity.setPetId(PET_ID);
        entity.setUserId(USER_ID);
        entity.setImageUrl("https://oss.eggbabe.com/ai-gen/x.png");
        entity.setCaption("想你了，看看我今天拍的照片");
        entity.setCreateDate(new Date());
        return entity;
    }

    @Test
    @DisplayName("createPostcard - shareId 缺省自动生成且长度 12、落库成功")
    void createPostcard_generatesShareId() {
        PetPostcardEntity entity = sample();
        service.createPostcard(entity);
        assertThat(entity.getShareId()).hasSize(12);
        assertThat(entity.getStatus()).isEqualTo("SENT");
        verify(petPostcardDao).insert(entity);
    }

    @Test
    @DisplayName("createPostcard - imageUrl/caption/petId/userId 缺失时拒绝（防脏数据进公开页）")
    void createPostcard_missingFields_throws() {
        PetPostcardEntity entity = sample();
        entity.setImageUrl(" ");
        assertThatThrownBy(() -> service.createPostcard(entity))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.NOT_NULL);
    }

    @Test
    @DisplayName("getPublicByShareId - 正常返回公开字段，不含 userId/petId")
    void getPublic_success() {
        PetPostcardEntity entity = sample();
        entity.setShareId(SHARE_ID);
        when(petPostcardDao.selectOne(any())).thenReturn(entity);
        PetEntity pet = new PetEntity();
        pet.setNickname("小金鱼");
        pet.setPrototype("锦鲤");
        when(petDao.selectById(PET_ID)).thenReturn(pet);

        var vo = service.getPublicByShareId(SHARE_ID);

        assertThat(vo.getPetName()).isEqualTo("小金鱼");
        assertThat(vo.getPrototype()).isEqualTo("锦鲤");
        assertThat(vo.getImageUrl()).isEqualTo(entity.getImageUrl());
        assertThat(vo.getCaption()).isEqualTo(entity.getCaption());
        assertThat(vo).hasNoNullFieldsOrPropertiesExcept();
    }

    @Test
    @DisplayName("getPublicByShareId - 不存在统一 POSTCARD_NOT_FOUND（防探测）")
    void getPublic_notFound_throws() {
        when(petPostcardDao.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.getPublicByShareId("NOPE"))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.POSTCARD_NOT_FOUND);
    }

    @Test
    @DisplayName("getPublicByShareId - 宠物已删除时不阻断，署名留空")
    void getPublic_petDeleted_petNameBlank() {
        PetPostcardEntity entity = sample();
        entity.setShareId(SHARE_ID);
        when(petPostcardDao.selectOne(any())).thenReturn(entity);
        when(petDao.selectById(PET_ID)).thenReturn(null);

        var vo = service.getPublicByShareId(SHARE_ID);

        assertThat(vo.getPetName()).isNull();
        assertThat(vo.getImageUrl()).isEqualTo(entity.getImageUrl());
    }
}
