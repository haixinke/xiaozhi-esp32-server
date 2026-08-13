package xiaozhi.modules.storyengine.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.web.multipart.MultipartFile;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.storyengine.dao.ActionImageDao;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryActionImageServiceImplTest {

    private ActionImageDao actionImageDao;
    private OssService ossService;
    private StoryActionImageServiceImpl service;

    @BeforeAll
    static void initMessageSource() {
        // RenException 通过 SpringContextUtils 解析 i18n 消息，单测环境用 mock 兜底
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        SpringContextUtils.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        actionImageDao = mock(ActionImageDao.class);
        ossService = mock(OssService.class);
        service = new StoryActionImageServiceImpl(actionImageDao, ossService);
    }

    @Test
    void uploadImagePersistsTagWhenProvided() throws Exception {
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.buildPublicUrl(anyString())).thenReturn("https://oss.example.com/img.png");
        MultipartFile file = pngFile();

        service.uploadImage("action-1", "锦鲤", "白天", "配文", "摸鱼", file);

        ArgumentCaptor<ActionImageEntity> captor = ArgumentCaptor.forClass(ActionImageEntity.class);
        verify(actionImageDao).insert(captor.capture());
        assertThat(captor.getValue().getTag()).isEqualTo("摸鱼");
    }

    @Test
    void uploadImageTrimsTagAndStoresNullWhenBlank() throws Exception {
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.buildPublicUrl(anyString())).thenReturn("https://oss.example.com/img.png");

        service.uploadImage("action-1", "锦鲤", "白天", null, "  ", pngFile());

        ArgumentCaptor<ActionImageEntity> captor = ArgumentCaptor.forClass(ActionImageEntity.class);
        verify(actionImageDao).insert(captor.capture());
        assertThat(captor.getValue().getTag()).isNull();
    }

    @Test
    void uploadImageAcceptsTagAtMaxLength() throws Exception {
        when(ossService.isEnabled()).thenReturn(true);
        when(ossService.buildPublicUrl(anyString())).thenReturn("https://oss.example.com/img.png");
        String tag = "标".repeat(64);

        service.uploadImage("action-1", "锦鲤", "白天", null, tag, pngFile());

        ArgumentCaptor<ActionImageEntity> captor = ArgumentCaptor.forClass(ActionImageEntity.class);
        verify(actionImageDao).insert(captor.capture());
        assertThat(captor.getValue().getTag()).hasSize(64);
    }

    @Test
    void uploadImageRejectsTagOverMaxLength() throws Exception {
        when(ossService.isEnabled()).thenReturn(true);
        String tag = "标".repeat(65);

        assertThatThrownBy(() -> service.uploadImage("action-1", "锦鲤", "白天", null, tag, pngFile()))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("64");
        verify(actionImageDao, never()).insert(any(ActionImageEntity.class));
    }

    @Test
    void updateInfoUpdatesCaptionsAndTagTogether() {
        ActionImageEntity existing = new ActionImageEntity();
        existing.setId("img-1");
        when(actionImageDao.selectById("img-1")).thenReturn(existing);

        service.updateInfo("img-1", "新配文", "新标签");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActionImageEntity>> captor = ArgumentCaptor
                .forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(actionImageDao).update(org.mockito.Mockito.isNull(), captor.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActionImageEntity> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSet()).contains("captions=").contains("tag=");
        assertThat(wrapper.getParamNameValuePairs()).containsValue("新配文").containsValue("新标签");
    }

    @Test
    void updateInfoStoresNullWhenTagBlank() {
        ActionImageEntity existing = new ActionImageEntity();
        existing.setId("img-1");
        when(actionImageDao.selectById("img-1")).thenReturn(existing);

        service.updateInfo("img-1", null, " ");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActionImageEntity>> captor = ArgumentCaptor
                .forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(actionImageDao).update(org.mockito.Mockito.isNull(), captor.capture());
        // trim 后为空则置 NULL，靠 UpdateWrapper 显式 set null 清空旧值
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue(null);
    }

    @Test
    void updateInfoRejectsTagOverMaxLength() {
        ActionImageEntity existing = new ActionImageEntity();
        existing.setId("img-1");
        when(actionImageDao.selectById("img-1")).thenReturn(existing);

        assertThatThrownBy(() -> service.updateInfo("img-1", null, "标".repeat(65)))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("64");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    void updateInfoRejectsBlankImageId(String id) {
        assertThatThrownBy(() -> service.updateInfo(id, null, null))
                .isInstanceOf(RenException.class);
    }

    @Test
    void updateInfoRejectsMissingImage() {
        when(actionImageDao.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.updateInfo("missing", null, null))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("不存在");
    }

    private static MultipartFile pngFile() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getBytes()).thenReturn(new byte[] { 1, 2, 3 });
        return file;
    }
}
