package xiaozhi.modules.wechat.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import me.chanjar.weixin.common.util.crypto.SHA1;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import me.chanjar.weixin.mp.util.crypto.WxMpCryptUtil;
import xiaozhi.modules.pet.service.ImageGenTaskService;
import xiaozhi.modules.wechat.config.WechatMsgPushProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WechatMsgCallbackController 消息推送回调测试")
class WechatMsgCallbackControllerTest {

    private static final String APPID = "wx-test-appid";
    private static final String TOKEN = "test-token";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String TIMESTAMP = "1700000000";
    private static final String NONCE = "nonce-1";

    @Mock
    private ImageGenTaskService imageGenTaskService;

    private WechatMsgCallbackController controller;

    @BeforeEach
    void setUp() {
        WechatMsgPushProperties properties = new WechatMsgPushProperties();
        properties.setToken(TOKEN);
        properties.setAesKey(AES_KEY);
        controller = new WechatMsgCallbackController(properties, imageGenTaskService);
        ReflectionTestUtils.setField(controller, "appid", APPID);
    }

    @Test
    @DisplayName("verify - 签名正确时回显echostr")
    void verify_validSignature_echoes() {
        String signature = SHA1.gen(TOKEN, TIMESTAMP, NONCE);

        String result = controller.verify(signature, TIMESTAMP, NONCE, "echo-123");

        assertThat(result).isEqualTo("echo-123");
    }

    @Test
    @DisplayName("verify - 签名错误时返回fail")
    void verify_invalidSignature_fails() {
        String result = controller.verify("bad-signature", TIMESTAMP, NONCE, "echo-123");

        assertThat(result).isEqualTo("fail");
    }

    @Test
    @DisplayName("receive - 审核通过事件按traceId路由且suggest=pass视为通过")
    void receive_mediaCheckPass_routesByTraceId() throws Exception {
        String plainXml = """
                <xml>
                  <ToUserName><![CDATA[gh_test]]></ToUserName>
                  <FromUserName><![CDATA[oUser]]></FromUserName>
                  <CreateTime>1700000000</CreateTime>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[wxa_media_check]]></Event>
                  <appid><![CDATA[%s]]></appid>
                  <trace_id><![CDATA[trace-xyz]]></trace_id>
                  <version>2</version>
                  <result><suggest>pass</suggest><label>100</label></result>
                </xml>
                """.formatted(APPID);
        String body = encryptEnvelope(plainXml);
        String msgSignature = SHA1.gen(TOKEN, TIMESTAMP, NONCE, extractEncrypt(body));

        String resp = controller.receive(body, msgSignature, TIMESTAMP, NONCE);

        assertThat(resp).isEqualTo("success");
        verify(imageGenTaskService).handleMediaCheckResult("trace-xyz", true);
    }

    @Test
    @DisplayName("receive - 审核驳回事件suggest=risky视为不通过")
    void receive_mediaCheckRisky_notPass() throws Exception {
        String plainXml = """
                <xml>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[wxa_media_check]]></Event>
                  <appid><![CDATA[%s]]></appid>
                  <trace_id><![CDATA[trace-bad]]></trace_id>
                  <version>2</version>
                  <result><suggest>risky</suggest><label>20001</label></result>
                </xml>
                """.formatted(APPID);
        String body = encryptEnvelope(plainXml);
        String msgSignature = SHA1.gen(TOKEN, TIMESTAMP, NONCE, extractEncrypt(body));

        controller.receive(body, msgSignature, TIMESTAMP, NONCE);

        verify(imageGenTaskService).handleMediaCheckResult("trace-bad", false);
    }

    @Test
    @DisplayName("receive - msg_signature不匹配时不路由")
    void receive_badSignature_noRouting() {
        String body = "<xml><Encrypt><![CDATA[whatever]]></Encrypt></xml>";

        String resp = controller.receive(body, "bad-sig", TIMESTAMP, NONCE);

        assertThat(resp).isEqualTo("success");
        verifyNoInteractions(imageGenTaskService);
    }

    @Test
    @DisplayName("receive - 非审核事件忽略")
    void receive_otherEvent_ignored() throws Exception {
        String plainXml = """
                <xml>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[hi]]></Content>
                </xml>
                """;
        String body = encryptEnvelope(plainXml);
        String msgSignature = SHA1.gen(TOKEN, TIMESTAMP, NONCE, extractEncrypt(body));

        controller.receive(body, msgSignature, TIMESTAMP, NONCE);

        verifyNoInteractions(imageGenTaskService);
    }

    /** 用与控制器相同的密钥构造加密外层报文（模拟微信安全模式推送） */
    private static String encryptEnvelope(String plainXml) {
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId(APPID);
        config.setToken(TOKEN);
        config.setAesKey(AES_KEY);
        // 双参版本返回纯密文（单参版本返回完整 XML 信封，不能嵌进 CDATA）
        String encrypt = new WxMpCryptUtil(config).encrypt("0123456789abcdef", plainXml);
        return "<xml><Encrypt><![CDATA[" + encrypt + "]]></Encrypt></xml>";
    }

    private static String extractEncrypt(String body) {
        int start = body.indexOf("<![CDATA[") + "<![CDATA[".length();
        int end = body.indexOf("]]>");
        return body.substring(start, end);
    }
}
