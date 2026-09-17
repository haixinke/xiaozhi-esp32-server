package xiaozhi.modules.wechat.service.impl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.wechat.service.WechatAccessTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;

@ExtendWith(MockitoExtension.class)
@DisplayName("WechatMediaCheckService 内容审核提交测试")
class WechatMediaCheckServiceImplTest {

    @Mock
    private WechatAccessTokenProvider accessTokenProvider;

    private StubMediaCheckService service;

    /** 桩化 httpPost 的实现，注入响应体 */
    private static class StubMediaCheckService extends WechatMediaCheckServiceImpl {
        private String stubbedResponse;

        StubMediaCheckService(WechatAccessTokenProvider provider) {
            super(provider);
        }

        void stubResponse(String response) {
            this.stubbedResponse = response;
        }

        @Override
        String httpPost(String url, String jsonBody) {
            return stubbedResponse;
        }
    }

    @BeforeEach
    void setUp() {
        service = new StubMediaCheckService(accessTokenProvider);
        // lenient：openid 为空等用例在取 token 前即拒绝
        lenient().when(accessTokenProvider.getAccessToken()).thenReturn("token-1");
    }

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

    @Test
    @DisplayName("mediaCheckAsync - errcode=0 时返回 trace_id")
    void mediaCheckAsync_success_returnsTraceId() {
        service.stubResponse("{\"errcode\":0,\"errmsg\":\"ok\",\"trace_id\":\"trace-abc\"}");

        String traceId = service.mediaCheckAsync("https://oss.eggbabe.com/ai-gen/1/a.jpg", "openid-1");

        assertThat(traceId).isEqualTo("trace-abc");
    }

    @Test
    @DisplayName("mediaCheckAsync - errcode非0时抛出提交失败错误码")
    void mediaCheckAsync_errcodeNonZero_throws() {
        service.stubResponse("{\"errcode\":87014,\"errmsg\":\"risky content\"}");

        assertThatThrownBy(() -> service.mediaCheckAsync("https://oss.eggbabe.com/ai-gen/1/a.jpg", "openid-1"))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
    }

    @Test
    @DisplayName("mediaCheckAsync - openid为空时直接拒绝")
    void mediaCheckAsync_blankOpenid_throws() {
        assertThatThrownBy(() -> service.mediaCheckAsync("https://oss.eggbabe.com/ai-gen/1/a.jpg", ""))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.IMAGE_GEN_CHECK_SUBMIT_FAILED);
    }
}
