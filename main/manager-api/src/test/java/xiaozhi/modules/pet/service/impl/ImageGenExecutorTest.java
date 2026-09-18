package xiaozhi.modules.pet.service.impl;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.aliyun.oss.model.CannedAccessControlList;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.service.ArkService;

import xiaozhi.common.oss.OssService;
import xiaozhi.modules.pet.config.SeedreamProperties;
import xiaozhi.modules.pet.constant.ImageGenTaskStatus;
import xiaozhi.modules.pet.dao.ImageGenTaskDao;
import xiaozhi.modules.pet.entity.ImageGenTaskEntity;
import xiaozhi.modules.wechat.dao.WechatUserDao;
import xiaozhi.modules.wechat.entity.WechatUserEntity;
import xiaozhi.modules.wechat.service.WechatMediaCheckService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageGenExecutor 异步生成测试")
class ImageGenExecutorTest {

    private static final String GENERATED_URL = "https://seedream.example.com/gen.png?sig=abc";
    private static final String RESULT_OSS_URL = "https://oss.eggbabe.com/ai-gen/1001/7.png";

    @Mock
    private ImageGenTaskDao taskDao;
    @Mock
    private WechatUserDao wechatUserDao;
    @Mock
    private ArkService arkService;
    @Mock
    private ObjectProvider<ArkService> arkServiceProvider;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private OssService ossService;
    @Mock
    private WechatMediaCheckService mediaCheckService;

    private ImageGenExecutor executor;

    @BeforeEach
    void setUp() {
        SeedreamProperties seedreamProperties = new SeedreamProperties();
        seedreamProperties.setKey("test-key");
        seedreamProperties.setModel("doubao-seedream-test");
        seedreamProperties.setSize("2K");

        executor = new ImageGenExecutor(taskDao, wechatUserDao, seedreamProperties, arkServiceProvider,
                restTemplate, ossService, mediaCheckService);

        // lenient：非 RUNNING 态等用例不走到这些桩
        lenient().when(arkServiceProvider.getIfAvailable()).thenReturn(arkService);
        lenient().when(ossService.isEnabled()).thenReturn(true);
        lenient().when(ossService.buildPublicUrl(anyString()))
                .thenAnswer(inv -> "https://oss.eggbabe.com/" + inv.getArgument(0));
    }

    @Test
    @DisplayName("generate - 多图参考调用Seedream，结果上传OSS并推进REVIEWING")
    void generate_happyPath_reviewsResult() {
        ImageGenTaskEntity task = task(ImageGenTaskStatus.RUNNING.name());
        when(taskDao.selectById(7L)).thenReturn(task);

        ImagesResponse response = new ImagesResponse();
        ImagesResponse.Image image = new ImagesResponse.Image();
        image.setUrl(GENERATED_URL);
        response.setData(List.of(image));
        when(arkService.generateImages(any(GenerateImagesRequest.class))).thenReturn(response);

        byte[] bytes = { 1, 2, 3 };
        when(restTemplate.exchange(eq(URI.create(GENERATED_URL)), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(byte[].class))).thenReturn(ResponseEntity.ok(bytes));

        WechatUserEntity wechatUser = new WechatUserEntity();
        wechatUser.setOpenid("openid-1");
        when(wechatUserDao.selectOne(any())).thenReturn(wechatUser);
        when(mediaCheckService.mediaCheckAsync(eq(RESULT_OSS_URL), eq("openid-1"))).thenReturn("trace-result-1");
        when(taskDao.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        executor.generate(7L);

        ArgumentCaptor<GenerateImagesRequest> requestCaptor = ArgumentCaptor.forClass(GenerateImagesRequest.class);
        verify(arkService).generateImages(requestCaptor.capture());
        GenerateImagesRequest request = requestCaptor.getValue();
        assertThat(request.getImage()).containsExactly(task.getPhotoUrl(), task.getIpImageUrl());
        assertThat(request.getPrompt()).contains("好运连连");

        verify(ossService).upload("ai-gen/1001/7.png", bytes, CannedAccessControlList.PublicRead);

        ArgumentCaptor<UpdateWrapper> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskDao).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getParamNameValuePairs())
                .containsValue(ImageGenTaskStatus.REVIEWING.name())
                .containsValue(RESULT_OSS_URL)
                .containsValue("trace-result-1");
    }

    @Test
    @DisplayName("generate - 任务不在RUNNING态时跳过")
    void generate_notRunning_skipped() {
        when(taskDao.selectById(7L)).thenReturn(task(ImageGenTaskStatus.SUCCEEDED.name()));

        executor.generate(7L);

        verify(arkService, never()).generateImages(any());
    }

    @Test
    @DisplayName("generate - Seedream异常时任务置FAILED")
    void generate_seedreamThrows_failsTask() {
        when(taskDao.selectById(7L)).thenReturn(task(ImageGenTaskStatus.RUNNING.name()));
        when(arkService.generateImages(any(GenerateImagesRequest.class)))
                .thenThrow(new RuntimeException("ark down"));
        when(taskDao.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        executor.generate(7L);

        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(taskDao).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(ImageGenTaskStatus.FAILED.name());
    }

    private static ImageGenTaskEntity task(String status) {
        ImageGenTaskEntity task = new ImageGenTaskEntity();
        task.setId(7L);
        task.setUserId(1001L);
        task.setPetId("pet-1");
        task.setStatus(status);
        task.setPhotoUrl("https://oss.eggbabe.com/ai-gen/1001/photo.jpg");
        task.setIpImageUrl("https://oss.eggbabe.com/default-ip/fish/fish.png");
        task.setCaption("好运连连");
        return task;
    }
}
