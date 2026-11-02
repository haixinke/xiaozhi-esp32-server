package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenFeedbackPromptBuilder;
import xiaozhi.modules.listen.service.ListenFeedbackService;
import xiaozhi.modules.llm.service.LLMService;

/**
 * 听你说·反馈文案生成服务单测（Mockito，不依赖 DB/Redis/Spring 上下文）。
 * 覆盖：正常生成写 Redis、LLM 返回 null 不写、超长截断、异常静默、分组/题目缺失跳过。
 */
@ExtendWith(MockitoExtension.class)
class ListenFeedbackServiceTest {

    @Mock
    private LLMService llmService;
    @Mock
    private ListenGroupDao listenGroupDao;
    @Mock
    private ListenQuestionDao listenQuestionDao;
    @Mock
    private RedisUtils redisUtils;

    private ListenFeedbackService service;

    private static final Long USER_ID = 42L;
    private static final Long GROUP_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new ListenFeedbackService(llmService, listenGroupDao, listenQuestionDao,
                new ListenFeedbackPromptBuilder(), redisUtils);
    }

    private ListenGroupEntity group() {
        ListenGroupEntity g = new ListenGroupEntity();
        g.setId(GROUP_ID);
        g.setName("睡前最后那一会儿");
        return g;
    }

    private ListenQuestionEntity question(long id, String text) {
        ListenQuestionEntity q = new ListenQuestionEntity();
        q.setId(id);
        q.setGroupId(GROUP_ID);
        q.setText(text);
        q.setOptionsJson("[{\"key\":\"A\",\"text\":\"刷手机\"},{\"key\":\"E\",\"text\":\"都不是\"}]");
        return q;
    }

    private ListenAnswerSubmitDTO.AnswerItem answer(long qid, String key, String custom) {
        ListenAnswerSubmitDTO.AnswerItem item = new ListenAnswerSubmitDTO.AnswerItem();
        item.setQuestionId(qid);
        item.setOptionKey(key);
        item.setCustomText(custom);
        return item;
    }

    @Test
    @DisplayName("正常生成：LLM 返回文案则写入 Redis（5 分钟 TTL）")
    void onEvent_success_writesToRedis() {
        when(listenGroupDao.selectById(GROUP_ID)).thenReturn(group());
        when(listenQuestionDao.selectList(org.mockito.ArgumentMatchers.<QueryWrapper<ListenQuestionEntity>>any()))
                .thenReturn(List.of(question(1L, "Q1")));
        when(llmService.generateText(anyString())).thenReturn("你呀，是那种深夜也想被温柔接住的人。");

        service.onFeedbackRequested(new ListenFeedbackService.FeedbackRequestedEvent(
                USER_ID, GROUP_ID, List.of(answer(1L, "A", null))));

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCap = ArgumentCaptor.forClass(Object.class);
        verify(redisUtils).set(keyCap.capture(), valueCap.capture(), eq(300L));
        assertThat(keyCap.getValue()).isEqualTo(RedisKeys.getListenFeedbackKey(USER_ID, GROUP_ID));
        assertThat(valueCap.getValue()).isEqualTo("你呀，是那种深夜也想被温柔接住的人。");
    }

    @Test
    @DisplayName("LLM 返回 null：不写 Redis、不抛异常")
    void onEvent_llmReturnsNull_silentlySkips() {
        when(listenGroupDao.selectById(GROUP_ID)).thenReturn(group());
        when(listenQuestionDao.selectList(org.mockito.ArgumentMatchers.<QueryWrapper<ListenQuestionEntity>>any()))
                .thenReturn(List.of(question(1L, "Q1")));
        when(llmService.generateText(anyString())).thenReturn(null);

        service.onFeedbackRequested(new ListenFeedbackService.FeedbackRequestedEvent(
                USER_ID, GROUP_ID, List.of(answer(1L, "A", null))));

        verify(redisUtils, never()).set(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("LLM 输出超 80 字：截断到 80 再写 Redis")
    void onEvent_tooLongOutput_truncatedTo80() {
        when(listenGroupDao.selectById(GROUP_ID)).thenReturn(group());
        when(listenQuestionDao.selectList(org.mockito.ArgumentMatchers.<QueryWrapper<ListenQuestionEntity>>any()))
                .thenReturn(List.of(question(1L, "Q1")));
        String longOutput = "你".repeat(120);
        when(llmService.generateText(anyString())).thenReturn(longOutput);

        service.onFeedbackRequested(new ListenFeedbackService.FeedbackRequestedEvent(
                USER_ID, GROUP_ID, List.of(answer(1L, "A", null))));

        ArgumentCaptor<Object> valueCap = ArgumentCaptor.forClass(Object.class);
        verify(redisUtils).set(anyString(), valueCap.capture(), eq(300L));
        assertThat((String) valueCap.getValue()).hasSize(80);
    }

    @Test
    @DisplayName("分组不存在：跳过且不调 LLM")
    void onEvent_groupMissing_skipsWithoutCallingLLM() {
        when(listenGroupDao.selectById(GROUP_ID)).thenReturn(null);

        service.onFeedbackRequested(new ListenFeedbackService.FeedbackRequestedEvent(
                USER_ID, GROUP_ID, List.of(answer(1L, "A", null))));

        verify(llmService, never()).generateText(anyString());
        verify(redisUtils, never()).set(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("DAO 抛异常：静默吞掉仅记日志（不向上传播影响调用方）")
    void onEvent_daoThrows_silentlySwallowed() {
        when(listenGroupDao.selectById(GROUP_ID)).thenThrow(new RuntimeException("DB down"));

        // 不抛异常即通过
        service.onFeedbackRequested(new ListenFeedbackService.FeedbackRequestedEvent(
                USER_ID, GROUP_ID, List.of(answer(1L, "A", null))));

        verify(redisUtils, never()).set(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("getFeedback：Redis 里是 String 则返回，否则返回 null")
    void getFeedback_readsFromRedis() {
        when(redisUtils.get(RedisKeys.getListenFeedbackKey(USER_ID, GROUP_ID)))
                .thenReturn("你是温柔的人");
        assertThat(service.getFeedback(USER_ID, GROUP_ID)).isEqualTo("你是温柔的人");

        when(redisUtils.get(RedisKeys.getListenFeedbackKey(USER_ID, GROUP_ID)))
                .thenReturn(null);
        assertThat(service.getFeedback(USER_ID, GROUP_ID)).isNull();
    }
}
