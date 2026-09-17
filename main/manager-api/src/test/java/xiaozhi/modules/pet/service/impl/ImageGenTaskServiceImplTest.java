package xiaozhi.modules.pet.service.impl;

import java.util.List;

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

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import xiaozhi.common.config.AliyunOssProperties;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pet.constant.ImageGenTaskStatus;
import xiaozhi.modules.pet.dao.ImageGenTaskDao;
import xiaozhi.modules.pet.dao.PetDao;
import xiaozhi.modules.pet.entity.ImageGenTaskEntity;
import xiaozhi.modules.pet.entity.PetEntity;
import xiaozhi.modules.pet.vo.ImageGenTaskVO;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.service.WechatMediaCheckService;

import xiaozhi.common.utils.SpringContextUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageGenTaskService 任务编排测试")
class ImageGenTaskServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final String PHOTO_URL = "https://oss.eggbabe.com/ai-gen/1001/abc.jpg";

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
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        service = new ImageGenTaskServiceImpl(petDao, wechatUserDao, mediaCheckService, ossProperties,
                imageGenExecutor);
        ReflectionTestUtils.setField(service, "baseDao", taskDao);
        ReflectionTestUtils.setField(service, "dailyLimit", 3);
        // lenient：回调路由类用例不走 URL 前缀校验
        lenient().when(ossProperties.getPublicUrl()).thenReturn("https://oss.eggbabe.com");
    }

    @Test
    @DisplayName("createTask - 照片URL非本bucket ai-gen前缀时拒绝")
    void createTask_foreignPhotoUrl_rejected() {
        assertThatThrownBy(() -> service.createTask(USER_ID, "https://evil.example.com/x.jpg"))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.IMAGE_GEN_PHOTO_URL_INVALID);
    }

    @Test
    @DisplayName("createTask - 当日配额已用完时拒绝")
    void createTask_quotaExceeded_rejected() {
        when(petDao.selectOne(any())).thenReturn(pet());
        when(taskDao.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> service.createTask(USER_ID, PHOTO_URL))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.IMAGE_GEN_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("createTask - 锦鲤原型落库IP参考图与文案，跳过审核直接RUNNING并触发生成")
    void createTask_koi_storesIpImageAndCaption() {
        when(petDao.selectOne(any())).thenReturn(pet());
        when(taskDao.selectCount(any())).thenReturn(0L);

        ImageGenTaskVO vo = service.createTask(USER_ID, PHOTO_URL);

        ArgumentCaptor<ImageGenTaskEntity> captor = ArgumentCaptor.forClass(ImageGenTaskEntity.class);
        verify(taskDao).insert(captor.capture());
        ImageGenTaskEntity saved = captor.getValue();
        // TODO(security): 审核临时跳过，创建即 RUNNING；恢复审核后应回到 PENDING + photoTraceId
        assertThat(saved.getStatus()).isEqualTo(ImageGenTaskStatus.RUNNING.name());
        assertThat(saved.getIpImageUrl()).isEqualTo("https://oss.eggbabe.com/default-ip/fish/fish.png");
        assertThat(saved.getCaption()).isIn("好运连连", "锦鲤附体 诸事顺利", "摸鱼也能赢");
        assertThat(saved.getPhotoTraceId()).isNull();
        assertThat(saved.getCounted()).isEqualTo(0);
        assertThat(vo.getStatus()).isEqualTo(ImageGenTaskStatus.RUNNING.name());
        verify(mediaCheckService, never()).mediaCheckAsync(anyString(), anyString());
        verify(imageGenExecutor).generateAsync(any());
    }

    @Test
    @DisplayName("handleMediaCheckResult - 照片审核通过推进RUNNING并触发生成")
    void handleMediaCheckResult_photoPass_triggersGenerate() {
        ImageGenTaskEntity task = task(1L, ImageGenTaskStatus.PENDING.name());
        when(taskDao.selectOne(any())).thenReturn(task);
        when(taskDao.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.handleMediaCheckResult("trace-photo-1", true);

        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskDao).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue(ImageGenTaskStatus.RUNNING.name());
        verify(imageGenExecutor).generateAsync(1L);
    }

    @Test
    @DisplayName("handleMediaCheckResult - 照片审核驳回置FAILED且不触发生成")
    void handleMediaCheckResult_photoReject_failsTask() {
        ImageGenTaskEntity task = task(1L, ImageGenTaskStatus.PENDING.name());
        when(taskDao.selectOne(any())).thenReturn(task);
        when(taskDao.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.handleMediaCheckResult("trace-photo-1", false);

        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskDao).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(ImageGenTaskStatus.FAILED.name())
                .containsValue("图片未通过审核，换一张试试");
        verify(imageGenExecutor, never()).generateAsync(any());
    }

    @Test
    @DisplayName("handleMediaCheckResult - 微信重推时条件更新影响0行，幂等跳过")
    void handleMediaCheckResult_duplicatePush_skipped() {
        ImageGenTaskEntity task = task(1L, ImageGenTaskStatus.PENDING.name());
        when(taskDao.selectOne(any())).thenReturn(task);
        when(taskDao.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        service.handleMediaCheckResult("trace-photo-1", true);

        verify(imageGenExecutor, never()).generateAsync(any());
    }

    @Test
    @DisplayName("handleMediaCheckResult - 结果图审核通过置SUCCEEDED并计入配额")
    void handleMediaCheckResult_resultPass_succeedsAndCounted() {
        // 第一次 selectOne（photo_trace_id 查询）未命中，第二次（result_trace_id）命中
        ImageGenTaskEntity task = task(2L, ImageGenTaskStatus.REVIEWING.name());
        when(taskDao.selectOne(any())).thenReturn(null, task);
        when(taskDao.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.handleMediaCheckResult("trace-result-1", true);

        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskDao).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(ImageGenTaskStatus.SUCCEEDED.name())
                .containsValue(1);
    }

    @Test
    @DisplayName("handleMediaCheckResult - 未知traceId不产生任何更新")
    void handleMediaCheckResult_unknownTrace_noop() {
        when(taskDao.selectOne(any())).thenReturn(null, null);

        service.handleMediaCheckResult("trace-unknown", true);

        verify(taskDao, never()).update(any(), any());
    }

    @Test
    @DisplayName("getTask - REVIEWING 态不下发结果图URL，SUCCEEDED 态才下发")
    void getTask_reviewing_masksResultUrl() {
        ImageGenTaskEntity task = task(3L, ImageGenTaskStatus.REVIEWING.name());
        task.setResultUrl("https://oss.eggbabe.com/ai-gen/1001/3.png");
        when(taskDao.selectOne(any())).thenReturn(task);

        ImageGenTaskVO reviewing = service.getTask(USER_ID, 3L);
        assertThat(reviewing.getResultUrl()).isNull();

        task.setStatus(ImageGenTaskStatus.SUCCEEDED.name());
        ImageGenTaskVO succeeded = service.getTask(USER_ID, 3L);
        assertThat(succeeded.getResultUrl()).isEqualTo("https://oss.eggbabe.com/ai-gen/1001/3.png");
    }

    private static PetEntity pet() {
        PetEntity pet = new PetEntity();
        pet.setId("pet-1");
        pet.setUserId(USER_ID);
        pet.setPrototype("锦鲤");
        return pet;
    }

    private static ImageGenTaskEntity task(Long id, String status) {
        ImageGenTaskEntity task = new ImageGenTaskEntity();
        task.setId(id);
        task.setUserId(USER_ID);
        task.setPetId("pet-1");
        task.setStatus(status);
        return task;
    }
}
