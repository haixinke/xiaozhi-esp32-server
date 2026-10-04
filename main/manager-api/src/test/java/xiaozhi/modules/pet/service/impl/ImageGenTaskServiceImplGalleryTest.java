package xiaozhi.modules.pet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.pet.dao.ImageGenTaskDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.ImageGenTaskEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.vo.ImageGenGalleryVO;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.service.WechatMediaCheckService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageGenTaskService 写真集分页测试")
class ImageGenTaskServiceImplGalleryTest {

    private static final Long USER_ID = 1001L;
    private static final String PET_ID = "pet-1";

    /** 构造 Asia/Shanghai 时区的确定时刻，避免 CI 机器默认时区影响分组断言 */
    private static Date shanghaiTime(int year, int month, int day, int hour) {
        return Date.from(java.time.LocalDateTime.of(year, month, day, hour, 0)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
    }

    @Mock
    private ImageGenTaskDao taskDao;
    @Mock
    private PetDao petDao;
    @Mock
    private WechatUserDao wechatUserDao;
    @Mock
    private WechatMediaCheckService mediaCheckService;
    @Mock
    private AliyunOssProperties ossProperties;
    @Mock
    private ImageGenExecutor imageGenExecutor;

    private ImageGenTaskServiceImpl service;

    @BeforeAll
    static void initMessageSource() {
        // RenException(int) 构造会经 MessageUtils 做 i18n 查找，需注入 mock 上下文
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        lenient().when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        service = new ImageGenTaskServiceImpl(petDao, wechatUserDao, mediaCheckService, ossProperties,
                imageGenExecutor);
        ReflectionTestUtils.setField(service, "baseDao", taskDao);
        ReflectionTestUtils.setField(service, "dailyLimit", 3);
        // lenient：未登录用例在宠物校验前短路
        lenient().when(petDao.selectOne(any())).thenReturn(pet());
    }

    private static PetEntity pet() {
        PetEntity pet = new PetEntity();
        pet.setId(PET_ID);
        pet.setUserId(USER_ID);
        return pet;
    }

    @Test
    @DisplayName("gallery - 未登录拒绝")
    void gallery_notLogin_rejected() {
        assertThatThrownBy(() -> service.gallery(null, PET_ID, 1, 10))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.USER_NOT_LOGIN);
    }

    @Test
    @DisplayName("gallery - petId 越权/不存在/已删除统一抛 PET_NOT_FOUND")
    void gallery_petNotOwned_rejected() {
        when(petDao.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.gallery(USER_ID, "other-pet", 1, 10))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.PET_NOT_FOUND);
    }

    @Test
    @DisplayName("gallery - 无写真返回空列表")
    void gallery_empty_returnsEmpty() {
        when(taskDao.selectMaps(any())).thenReturn(List.of(Map.of("cnt", 0L)), List.of());

        ImageGenGalleryVO vo = service.gallery(USER_ID, PET_ID, 1, 10);

        assertThat(vo.getTotal()).isZero();
        assertThat(vo.getList()).isEmpty();
        assertThat(vo.getPage()).isEqualTo(1);
        assertThat(vo.getLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("gallery - 三段查询都带 pet_id 条件（多宠物写真隔离）")
    void gallery_allQueriesFilterByPetId() {
        when(taskDao.selectMaps(any())).thenReturn(List.of(Map.of("cnt", 0L)), List.of());

        service.gallery(USER_ID, PET_ID, 1, 10);

        ArgumentCaptor<QueryWrapper<ImageGenTaskEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(taskDao, atLeastOnce()).selectMaps(captor.capture());
        captor.getAllValues().forEach(wrapper -> assertThat(wrapper.getSqlSegment()).contains("pet_id ="));
    }

    @Test
    @DisplayName("gallery - 按天分组倒序，日内照片倒序，只含成功任务")
    void gallery_groupsByDayDesc() {
        when(taskDao.selectMaps(any())).thenReturn(
                List.of(Map.of("cnt", 2L)),
                List.of(Map.of("day", java.sql.Date.valueOf("2026-10-03")),
                        Map.of("day", java.sql.Date.valueOf("2026-10-01"))),
                List.of(
                        photoRow(3L, shanghaiTime(2026, 10, 3, 20), "2026-10-03"),
                        photoRow(2L, shanghaiTime(2026, 10, 3, 10), "2026-10-03"),
                        photoRow(1L, shanghaiTime(2026, 10, 1, 12), "2026-10-01")));

        ImageGenGalleryVO vo = service.gallery(USER_ID, PET_ID, 1, 10);

        assertThat(vo.getTotal()).isEqualTo(2);
        assertThat(vo.getList()).hasSize(2);
        assertThat(vo.getList().get(0).getDate()).isEqualTo("2026-10-03");
        assertThat(vo.getList().get(0).getPhotos()).extracting(ImageGenGalleryVO.PhotoVO::getTaskId)
                .containsExactly("3", "2");
        assertThat(vo.getList().get(1).getDate()).isEqualTo("2026-10-01");
        assertThat(vo.getList().get(1).getPhotos()).extracting(ImageGenGalleryVO.PhotoVO::getTaskId)
                .containsExactly("1");
        assertThat(vo.getList().get(0).getPhotos().get(0).getResultUrl())
                .isEqualTo("https://oss.eggbabe.com/ai-gen/1001/3.jpg");
        assertThat(vo.getList().get(0).getPhotos().get(0).getCreateTime())
                .isEqualTo("2026-10-03 20:00:00");
    }

    @Test
    @DisplayName("gallery - limit 超过上限收敛到 50，页码小于 1 收敛到 1")
    void gallery_pageParamsClamped() {
        when(taskDao.selectMaps(any())).thenReturn(List.of(Map.of("cnt", 0L)), List.of());

        ImageGenGalleryVO vo = service.gallery(USER_ID, PET_ID, 0, 999);

        assertThat(vo.getPage()).isEqualTo(1);
        assertThat(vo.getLimit()).isEqualTo(50);
    }

    /** 模拟照片行查询结果：day 列为数据库 DATE(create_date) 返回值；create_date 按真实行为映射为 LocalDateTime */
    private static Map<String, Object> photoRow(Long id, Date createDate, String day) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", id);
        row.put("result_url", "https://oss.eggbabe.com/ai-gen/1001/" + id + ".jpg");
        row.put("create_date", createDate.toInstant().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime());
        row.put("day", java.sql.Date.valueOf(day));
        return row;
    }
}
