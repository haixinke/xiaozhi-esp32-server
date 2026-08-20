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
import xiaozhi.modules.storyengine.dao.ActionDao;
import xiaozhi.modules.storyengine.dao.BigSceneDao;
import xiaozhi.modules.storyengine.dao.SmallSceneDao;
import xiaozhi.modules.storyengine.entity.ActionEntity;
import xiaozhi.modules.storyengine.entity.ActionImageEntity;
import xiaozhi.modules.storyengine.entity.BigSceneEntity;
import xiaozhi.modules.storyengine.entity.SmallSceneEntity;
import xiaozhi.modules.storyengine.service.SpecialSceneTagRegistry;
import xiaozhi.modules.storyengine.vo.CaptionsImportVO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
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
    private BigSceneDao bigSceneDao;
    private SmallSceneDao smallSceneDao;
    private ActionDao actionDao;
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
        bigSceneDao = mock(BigSceneDao.class);
        smallSceneDao = mock(SmallSceneDao.class);
        actionDao = mock(ActionDao.class);
        service = new StoryActionImageServiceImpl(actionImageDao, ossService, bigSceneDao, smallSceneDao, actionDao,
                new SpecialSceneTagRegistry());
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

    // ==================== importCaptions ====================

    @Test
    void importCaptionsRejectsMismatchedHeader() throws Exception {
        MultipartFile file = excelFile(new String[][] {
                { "大场景", "小场景", "动作", "时段", "宠物类型", "配文" },
                { "在家", "卧室", "小憩", "白天", "锦鲤", "躺平中" }
        });

        assertThatThrownBy(() -> service.importCaptions(file))
                .isInstanceOf(RenException.class)
                .hasMessageContaining("图片文案");
    }

    @Test
    void importCaptionsUpdatesAllImagesInMatchedGroup() throws Exception {
        stubSceneChain();
        List<ActionImageEntity> images = List.of(image("img-1", null), image("img-2", "摸鱼"));
        when(actionImageDao.selectList(any())).thenReturn(images);
        MultipartFile file = excelFile(new String[][] {
                { "大场景", "小场景", "动作", "时段", "宠物类型", "图片文案" },
                { "在家", "卧室", "小憩", "白天", "锦鲤", "躺平中|不想起床" }
        });

        CaptionsImportVO result = service.importCaptions(file);

        assertThat(result.getUpdatedImages()).isEqualTo(2);
        assertThat(result.getSkippedRows()).isZero();
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActionImageEntity>> captor = ArgumentCaptor
                .forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(actionImageDao, org.mockito.Mockito.times(2)).update(org.mockito.Mockito.isNull(), captor.capture());
        for (com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActionImageEntity> wrapper : captor
                .getAllValues()) {
            assertThat(wrapper.getParamNameValuePairs()).containsValue("躺平中|不想起床");
        }
    }

    @Test
    void importCaptionsSkipsRowWhenNameNotMatched() throws Exception {
        // 大场景表中不存在"旅行"，按名称匹配失败
        when(bigSceneDao.selectOne(any())).thenReturn(null);
        MultipartFile file = excelFile(new String[][] {
                { "大场景", "小场景", "动作", "时段", "宠物类型", "图片文案" },
                { "旅行", "卧室", "小憩", "白天", "锦鲤", "躺平中" }
        });

        CaptionsImportVO result = service.importCaptions(file);

        assertThat(result.getUpdatedImages()).isZero();
        assertThat(result.getSkippedRows()).isEqualTo(1);
        assertThat(result.getSkippedDetails()).allSatisfy(detail -> assertThat(detail).contains("大场景名称不匹配"));
        verify(actionImageDao, never()).update(any(), any());
    }

    @Test
    void importCaptionsSkipsWindowTaggedImagesInHomeBedroom() throws Exception {
        stubSceneChain();
        List<ActionImageEntity> images = List.of(image("img-normal", null), image("img-window", "窗户"));
        when(actionImageDao.selectList(any())).thenReturn(images);
        MultipartFile file = excelFile(new String[][] {
                { "大场景", "小场景", "动作", "时段", "宠物类型", "图片文案" },
                { "在家", "卧室", "小憩", "白天", "锦鲤", "躺平中" }
        });

        CaptionsImportVO result = service.importCaptions(file);

        // 在家+卧室的窗户图为窗景特殊图，不更新文案，只更新普通图
        assertThat(result.getUpdatedImages()).isEqualTo(1);
        // 只更新了1张普通图，窗户图未参与更新
        verify(actionImageDao).update(org.mockito.Mockito.isNull(),
                any(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class));
    }

    @Test
    void importCaptionsUpdatesWindowTaggedImagesInOtherScenes() throws Exception {
        stubSceneChain("旅行", "北京-故宫", "红墙前散步");
        List<ActionImageEntity> images = List.of(image("img-1", "窗户"));
        when(actionImageDao.selectList(any())).thenReturn(images);
        MultipartFile file = excelFile(new String[][] {
                { "大场景", "小场景", "动作", "时段", "宠物类型", "图片文案" },
                { "旅行", "北京-故宫", "红墙前散步", "白天", "锦鲤", "风和日丽" }
        });

        CaptionsImportVO result = service.importCaptions(file);

        // 非特殊场景组合中 tag 仅为运营备注，所有图片正常更新
        assertThat(result.getUpdatedImages()).isEqualTo(1);
    }

    @Test
    void importCaptionsSkipsRowWhenCaptionsBlank() throws Exception {
        stubSceneChain();
        MultipartFile file = excelFile(new String[][] {
                { "大场景", "小场景", "动作", "时段", "宠物类型", "图片文案" },
                { "在家", "卧室", "小憩", "白天", "锦鲤", " " }
        });

        CaptionsImportVO result = service.importCaptions(file);

        assertThat(result.getUpdatedImages()).isZero();
        assertThat(result.getSkippedRows()).isEqualTo(1);
        assertThat(result.getSkippedDetails()).allSatisfy(detail -> assertThat(detail).contains("图片文案为空"));
        verify(actionImageDao, never()).update(any(), any());
    }

    private void stubSceneChain() {
        stubSceneChain("在家", "卧室", "小憩");
    }

    private void stubSceneChain(String bigSceneName, String smallSceneName, String actionName) {
        BigSceneEntity bigScene = new BigSceneEntity();
        bigScene.setId("big-1");
        bigScene.setName(bigSceneName);
        when(bigSceneDao.selectOne(any())).thenReturn(bigScene);
        SmallSceneEntity smallScene = new SmallSceneEntity();
        smallScene.setId("small-1");
        smallScene.setName(smallSceneName);
        when(smallSceneDao.selectOne(any())).thenReturn(smallScene);
        ActionEntity action = new ActionEntity();
        action.setId("action-1");
        action.setName(actionName);
        when(actionDao.selectOne(any())).thenReturn(action);
    }

    private static ActionImageEntity image(String id, String tag) {
        ActionImageEntity entity = new ActionImageEntity();
        entity.setId(id);
        entity.setTag(tag);
        return entity;
    }

    private static MultipartFile excelFile(String[][] rows) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");
            for (String[] rowData : rows) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
                for (int i = 0; i < rowData.length; i++) {
                    row.createCell(i).setCellValue(rowData[i]);
                }
            }
            workbook.write(out);
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            byte[] bytes = out.toByteArray();
            when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
            return file;
        }
    }
}
