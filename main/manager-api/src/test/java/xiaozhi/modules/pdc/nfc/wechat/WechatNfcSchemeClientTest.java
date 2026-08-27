package xiaozhi.modules.pdc.nfc.wechat;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.pdc.nfc.config.PdcNfcProperties;
import xiaozhi.modules.wechat.service.WechatAccessTokenProvider;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WechatNfcSchemeClient 测试")
class WechatNfcSchemeClientTest {

    @Mock
    private WechatAccessTokenProvider accessTokens;

    private PdcNfcProperties properties;
    private RecordingTransport transport;
    private WechatNfcSchemeClient client;

    @BeforeEach
    void setUp() {
        properties = new PdcNfcProperties();
        properties.setModelId("model-actual");
        transport = new RecordingTransport();
        // 微信 generatenfcscheme 成功响应的链接字段名是 openlink（非 scheme）
        transport.responseBody = "{\"errcode\":0,\"openlink\":\"weixin://nfc/xxx\"}";
        lenient().when(accessTokens.getAccessToken()).thenReturn("access-token");

        client = new WechatNfcSchemeClient(accessTokens, transport, properties);
    }

    @BeforeAll
    static void initMessageSource() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean("messageSource")).thenReturn(messageSource);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        xiaozhi.common.utils.SpringContextUtils.applicationContext = applicationContext;
    }

    @Test
    @DisplayName("发送 release 路径、query、modelId 和 sn")
    void sendsReleasePathQueryModelAndUniqueSnSeparately() {
        WechatNfcSchemeResult result = client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        assertThat(result.success()).isTrue();
        assertThat(result.scheme()).isEqualTo("weixin://nfc/xxx");
        assertThat(transport.lastUrl).endsWith("/wxa/generatenfcscheme?access_token=access-token");

        JSONObject body = JSONUtil.parseObj(transport.lastBody);
        assertThat(body.getJSONObject("jump_wxa").getStr("path"))
                .isEqualTo("/pages/nfc-claim/nfc-claim");
        assertThat(body.getJSONObject("jump_wxa").getStr("query"))
                .isEqualTo("v=1&ref=AbCdEfGhIjKlMnOpQrStUv");
        assertThat(body.getJSONObject("jump_wxa").getStr("env_version")).isEqualTo("release");
        assertThat(body.getStr("model_id")).isEqualTo("model-actual");
        assertThat(body.getStr("sn")).isEqualTo("EBSN001");
    }

    @Test
    @DisplayName("modelId 为空 - 拒绝（不获取 token）")
    void blankModelIdRejectedBeforeTokenFetch() {
        properties.setModelId("");

        assertThatThrownBy(() -> client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv"))
                .isInstanceOf(RenException.class);
        verifyNoInteractions(accessTokens);
    }

    @Test
    @DisplayName("claimRef 不是 22 字符 - 拒绝")
    void invalidClaimRefRejected() {
        assertThatThrownBy(() -> client.generate("EBSN001", "short"))
                .isInstanceOf(RenException.class);
        verifyNoInteractions(accessTokens);
    }

    @Test
    @DisplayName("wechatSn 为空 - 拒绝")
    void blankWechatSnRejected() {
        assertThatThrownBy(() -> client.generate("", "AbCdEfGhIjKlMnOpQrStUv"))
                .isInstanceOf(RenException.class);
        verifyNoInteractions(accessTokens);
    }

    @Test
    @DisplayName("schemeEnvVersion=trial 时 env_version 走体验版")
    void respectsTrialEnvVersion() {
        properties.setSchemeEnvVersion("trial");

        client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        JSONObject body = JSONUtil.parseObj(transport.lastBody);
        assertThat(body.getJSONObject("jump_wxa").getStr("env_version")).isEqualTo("trial");
    }

    @Test
    @DisplayName("微信返回 errcode!=0 - 返回 fail result")
    void wechatErrorReturnsFailResult() {
        transport.responseBody = "{\"errcode\":44990,\"errmsg\":\"api frequency control\"}";

        WechatNfcSchemeResult result = client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        assertThat(result.success()).isFalse();
        assertThat(result.errcode()).isEqualTo(44990);
        assertThat(result.action()).isEqualTo(WechatNfcErrorAction.RETRYABLE);
    }

    @Test
    @DisplayName("网络异常 - 返回 RETRYABLE networkError")
    void networkExceptionReturnsRetryable() {
        transport.shouldThrow = true;

        WechatNfcSchemeResult result = client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        assertThat(result.success()).isFalse();
        assertThat(result.action()).isEqualTo(WechatNfcErrorAction.RETRYABLE);
    }

    @Test
    @DisplayName("仅读 openlink 字段 - 旧的 scheme 字段不再被识别")
    void onlyOpenlinkFieldIsRead() {
        // 历史 bug：代码读 \"scheme\" 导致拿到 null，加密时 NPE，微信侧 sn 已被消耗
        transport.responseBody = "{\"errcode\":0,\"scheme\":\"weixin://nfc/legacy\"}";

        WechatNfcSchemeResult result = client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        assertThat(result.success()).isFalse();
        assertThat(result.errcode())
                .isEqualTo(WechatNfcSchemeResult.MISSING_OPENLINK_ERRCODE);
        assertThat(result.action()).isEqualTo(WechatNfcErrorAction.TASK_FATAL);
    }

    @Test
    @DisplayName("errcode=0 但 openlink 缺失 - 不抛 NPE，归为 TASK_FATAL")
    void missingOpenlinkFailsClosedInsteadOfNpe() {
        transport.responseBody = "{\"errcode\":0}";

        WechatNfcSchemeResult result = client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        assertThat(result.success()).isFalse();
        assertThat(result.scheme()).isNull();
        assertThat(result.action()).isEqualTo(WechatNfcErrorAction.TASK_FATAL);
    }

    @Test
    @DisplayName("openlink 为空白字符串 - 同样归为 TASK_FATAL")
    void blankOpenlinkFailsClosed() {
        transport.responseBody = "{\"errcode\":0,\"openlink\":\"   \"}";

        WechatNfcSchemeResult result = client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        assertThat(result.success()).isFalse();
        assertThat(result.action()).isEqualTo(WechatNfcErrorAction.TASK_FATAL);
    }

    @Test
    @DisplayName("9800010 schema 已存在 - TASK_FATAL 且识别为 sn 已占用")
    void snOccupiedReturnsFatalAndIsRecognized() {
        transport.responseBody =
                "{\"errcode\":9800010,\"errmsg\":\"schema已存在\"}";

        WechatNfcSchemeResult result = client.generate("EBSN001", "AbCdEfGhIjKlMnOpQrStUv");

        assertThat(result.success()).isFalse();
        assertThat(result.action()).isEqualTo(WechatNfcErrorAction.TASK_FATAL);
        assertThat(WechatNfcErrorPolicy.isSnOccupied(result.errcode())).isTrue();
    }

    /**
     * 录制传输层：捕获请求 URL 和 body，返回预设响应。
     */
    static class RecordingTransport extends WechatNfcHttpTransport {
        String lastUrl;
        String lastBody;
        String responseBody = "{}";
        boolean shouldThrow;

        @Override
        String httpPost(String url, String jsonBody) {
            lastUrl = url;
            lastBody = jsonBody;
            if (shouldThrow) {
                throw new RuntimeException("network timeout");
            }
            return responseBody;
        }
    }
}
