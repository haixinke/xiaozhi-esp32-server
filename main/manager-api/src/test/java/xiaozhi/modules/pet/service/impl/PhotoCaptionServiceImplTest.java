package xiaozhi.modules.pet.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.service.PhotoCaptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhotoCaptionServiceImpl 写真文案生成测试")
class PhotoCaptionServiceImplTest {

    /** 与 PhotoCaptionServiceImpl.CAPTION_POOL 锦鲤池保持一致 */
    private static final String[] KOI_POOL = {
            "转发这条锦鲤，好运直接拉满", "摸鱼摸到大奖，锦鲤本鲤", "今天也要做一条好运爆棚的鱼",
            "水逆退散，锦鲤罩我", "躺平的鱼运气都不会太差", "锦鲤附身，主打一个心想事成",
            "不慌不忙，好运正在路上", "摸鱼一时爽，好运经常来", "本鲤出马，烦恼全挂", "佛系养鱼，好运自来" };

    /** 与 PhotoCaptionServiceImpl.CAPTION_POOL 玉兔池保持一致 */
    private static final String[] RABBIT_POOL = {
            "月宫在逃小可爱，已上线", "兔兔我啊，今天也在认真可爱", "蹦跶两下，烦恼清零",
            "吃可爱长大的，不服来rua", "玉兔营业中，快乐不打烊", "随缘可爱，佛系卖萌",
            "耳朵一竖，好事将至", "今天份的快乐已充好电", "慢生活万岁，兔兔不着急", "温柔有光，自带治愈buff" };

    @Mock
    private LLMService llmService;

    private PhotoCaptionService captionService;

    @BeforeEach
    void setUp() {
        captionService = new PhotoCaptionServiceImpl(llmService);
    }

    @Test
    @DisplayName("drawCaption - LLM返回合规文案时直接使用")
    void drawCaption_llmValid_usesLlmCaption() {
        when(llmService.generateText(anyString())).thenReturn("锦鲤在线，好运不掉线");

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isEqualTo("锦鲤在线，好运不掉线");
    }

    @Test
    @DisplayName("drawCaption - prompt带原型与日期上下文")
    void drawCaption_promptContainsPrototypeAndDate() {
        when(llmService.generateText(anyString())).thenReturn("好运连连");

        captionService.drawCaption("玉兔");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(llmService).generateText(captor.capture());
        assertThat(captor.getValue()).contains("玉兔").contains("月").contains("日");
    }

    @Test
    @DisplayName("drawCaption - 带引号换行的输出清洗后使用")
    void drawCaption_llmOutputDirty_sanitized() {
        when(llmService.generateText(anyString())).thenReturn("「摸鱼摸到大奖\n锦鲤本鲤」");

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isEqualTo("摸鱼摸到大奖锦鲤本鲤");
    }

    @Test
    @DisplayName("drawCaption - 末尾标点被剥离")
    void drawCaption_trailingPunctuation_stripped() {
        when(llmService.generateText(anyString())).thenReturn("好运正在路上。");

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isEqualTo("好运正在路上");
    }

    @Test
    @DisplayName("drawCaption - 首次超15字重试，第二次合规则使用")
    void drawCaption_firstTooLong_retryOnce() {
        when(llmService.generateText(anyString()))
                .thenReturn("这条文案实在是太长太长太长太长太长太长了")
                .thenReturn("好运连连");

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isEqualTo("好运连连");
        verify(llmService, times(2)).generateText(anyString());
    }

    @Test
    @DisplayName("drawCaption - 两次都超15字回退预置池")
    void drawCaption_bothTooLong_fallbackPool() {
        when(llmService.generateText(anyString()))
                .thenReturn("这条文案实在是太长太长太长太长太长太长了");

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isIn(KOI_POOL);
        verify(llmService, times(2)).generateText(anyString());
    }

    @Test
    @DisplayName("drawCaption - LLM返回null回退预置池")
    void drawCaption_llmNull_fallbackPool() {
        when(llmService.generateText(anyString())).thenReturn(null);

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isIn(KOI_POOL);
    }

    @Test
    @DisplayName("drawCaption - LLM抛异常回退预置池")
    void drawCaption_llmThrows_fallbackPool() {
        when(llmService.generateText(anyString())).thenThrow(new RuntimeException("llm down"));

        String caption = captionService.drawCaption("玉兔");

        assertThat(caption).isIn(RABBIT_POOL);
    }

    @Test
    @DisplayName("drawCaption - 恰好15码点边界合规")
    void drawCaption_exactly15CodePoints_valid() {
        when(llmService.generateText(anyString())).thenReturn("一二三四五六七八九十壹贰叁肆伍");

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isEqualTo("一二三四五六七八九十壹贰叁肆伍");
    }

    @Test
    @DisplayName("drawCaption - emoji按码点计数，14字+1emoji合规")
    void drawCaption_emojiCountedByCodePoint_valid() {
        when(llmService.generateText(anyString())).thenReturn("好运连连财源滚滚来呀来呀🍀");

        String caption = captionService.drawCaption("锦鲤");

        assertThat(caption).isEqualTo("好运连连财源滚滚来呀来呀🍀");
    }

    @Test
    @DisplayName("drawCaption - 原型为null时prompt按玉兔兜底，不出现字面null")
    void drawCaption_nullPrototype_promptUsesDefault() {
        when(llmService.generateText(anyString())).thenReturn("好运连连");

        captionService.drawCaption(null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(llmService).generateText(captor.capture());
        assertThat(captor.getValue()).contains("玉兔").doesNotContain("null");
    }

    @Test
    @DisplayName("drawCaption - 未知原型回退玉兔池")
    void drawCaption_unknownPrototype_fallbackRabbitPool() {
        when(llmService.generateText(anyString())).thenReturn(null);

        String caption = captionService.drawCaption("不存在的原型");

        assertThat(caption).isIn(RABBIT_POOL);
    }
}
