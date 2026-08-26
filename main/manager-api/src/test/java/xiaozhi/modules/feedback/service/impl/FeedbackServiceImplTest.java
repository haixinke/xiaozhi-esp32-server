package xiaozhi.modules.feedback.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.feedback.dao.FeedbackDao;
import xiaozhi.modules.feedback.dto.FeedbackHandleDTO;
import xiaozhi.modules.feedback.dto.FeedbackSubmitDTO;
import xiaozhi.modules.feedback.entity.FeedbackEntity;
import xiaozhi.modules.feedback.vo.FeedbackSubmitVO;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceImplTest {

    private FeedbackDao feedbackDao;
    private RedisUtils redisUtils;
    private FeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        feedbackDao = mock(FeedbackDao.class);
        redisUtils = mock(RedisUtils.class);
        service = new FeedbackServiceImpl(redisUtils);
        ReflectionTestUtils.setField(service, "baseDao", feedbackDao);
    }

    @Test
    void submitPersistsFeedbackAndReturnsReceiptNumber() {
        when(redisUtils.increment(anyString(), anyLong())).thenReturn(1L);
        doAnswer(invocation -> {
            FeedbackEntity entity = invocation.getArgument(0);
            entity.setId(123L);
            entity.setCreateDate(new Date());
            return 1;
        }).when(feedbackDao).insert(any(FeedbackEntity.class));

        FeedbackSubmitDTO dto = new FeedbackSubmitDTO();
        dto.setType("FUNCTION_FAILURE");
        dto.setContent("  宠物对话打不开  ");
        dto.setConsent(true);

        FeedbackSubmitVO result = service.submit(7L, dto);

        String expectedDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        assertThat(result.getReceiptNumber()).isEqualTo("FB" + expectedDate + "-000123");
        verify(feedbackDao).insert(any(FeedbackEntity.class));
        // 内容应去首尾空白后入库
        org.mockito.ArgumentCaptor<FeedbackEntity> captor = org.mockito.ArgumentCaptor.forClass(FeedbackEntity.class);
        verify(feedbackDao).insert(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("宠物对话打不开");
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void submitRejectsSecondSubmissionWithinRateLimitWindow() {
        // 60 秒窗口内计数 >1 视为频繁提交
        when(redisUtils.increment(anyString(), anyLong())).thenReturn(2L);

        FeedbackSubmitDTO dto = new FeedbackSubmitDTO();
        dto.setType("OTHER");
        dto.setContent("重复提交");
        dto.setConsent(true);

        // RenException 构造需经 MessageUtils 走 Spring 上下文，单测环境 mock 掉
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            messages.when(() -> MessageUtils.getMessage(ErrorCode.FEEDBACK_SUBMIT_TOO_FREQUENTLY))
                    .thenReturn("反馈提交过于频繁，请稍后再试");

            assertThatThrownBy(() -> service.submit(7L, dto))
                    .isInstanceOf(RenException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FEEDBACK_SUBMIT_TOO_FREQUENTLY);
        }
        verify(feedbackDao, never()).insert(any(FeedbackEntity.class));
    }

    @Test
    void handleUpdatesStatusAndRemark() {
        FeedbackEntity entity = new FeedbackEntity();
        entity.setId(5L);
        entity.setStatus(0);
        when(feedbackDao.selectById(5L)).thenReturn(entity);

        FeedbackHandleDTO dto = new FeedbackHandleDTO();
        dto.setId(5L);
        dto.setStatus(1);
        dto.setRemark("已记录，下个版本修复");

        service.handle(dto);

        assertThat(entity.getStatus()).isEqualTo(1);
        assertThat(entity.getRemark()).isEqualTo("已记录，下个版本修复");
        verify(feedbackDao).updateById(entity);
    }

    @Test
    void handleThrowsWhenFeedbackNotFound() {
        when(feedbackDao.selectById(404L)).thenReturn(null);

        FeedbackHandleDTO dto = new FeedbackHandleDTO();
        dto.setId(404L);
        dto.setStatus(1);

        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            messages.when(() -> MessageUtils.getMessage(ErrorCode.FEEDBACK_NOT_FOUND))
                    .thenReturn("反馈记录不存在");

            assertThatThrownBy(() -> service.handle(dto))
                    .isInstanceOf(RenException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FEEDBACK_NOT_FOUND);
        }
    }

    @Test
    void receiptNumberFormatIsStablePerRecord() throws Exception {
        Date date = new SimpleDateFormat("yyyyMMdd").parse("20260823");
        String receipt = FeedbackServiceImpl.buildReceiptNumber(1234567L, date);
        assertThat(receipt).isEqualTo("FB20260823-234567");
        assertThat(FeedbackServiceImpl.buildReceiptNumber(1234567L, date)).isEqualTo(receipt);
    }
}
