package xiaozhi.modules.wechat.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhi.common.exception.RenExceptionHandler;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.Result;
import xiaozhi.common.utils.SpringContextUtils;
import xiaozhi.modules.security.user.SecurityUser;
import xiaozhi.modules.wechat.interceptor.WechatPhoneInterceptor;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 通过 HTTP 边界验证手机号门槛，必要恢复接口和后续功能使用同一账号事实。 */
class WechatPhoneInterceptorTest {
    private WechatPhoneGate gate;
    private MockMvc mvc;
    private MockedStatic<SecurityUser> security;
    private UserDetail user;

    @BeforeAll
    static void messages() {
        ApplicationContext context = mock(ApplicationContext.class);
        MessageSource messages = mock(MessageSource.class);
        when(context.getBean("messageSource")).thenReturn(messages);
        when(messages.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(call -> call.getArgument(2));
        SpringContextUtils.applicationContext = context;
    }

    @BeforeEach
    void setup() {
        gate = mock(WechatPhoneGate.class);
        user = new UserDetail();
        user.setId(7L);
        security = mockStatic(SecurityUser.class);
        security.when(SecurityUser::getUser).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new BusinessController())
                .setControllerAdvice(new RenExceptionHandler())
                .addInterceptors(new WechatPhoneInterceptor(gate)).build();
    }

    @AfterEach
    void cleanup() {
        security.close();
    }

    @ParameterizedTest
    @CsvSource({"GET,/agent/chat-history/list", "POST,/drawing/session", "GET,/star/balance",
            "PUT,/wechat/profile", "DELETE,/pet/pet-1", "PUT,/pet/pet-1/scene"})
    void unboundHatchedAccountCannotCallLaterBusiness(String method, String path) throws Exception {
        when(gate.requiresPhoneBinding(7L)).thenReturn(true);

        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(jsonPath("$.code").value(10228));
    }

    @ParameterizedTest
    @CsvSource({"POST,/wechat/login", "POST,/wechat/bindPhone", "GET,/wechat/profile",
            "GET,/pet/list", "GET,/pet/pet-1", "GET,/pet/pet-1/hatch-actions",
            "POST,/pet/adopt", "POST,/pet/pet-1/hatch", "POST,/pet/pet-1/hatch-action",
            "GET,/pdc/nfc/claim/preview", "POST,/pdc/nfc/claim/confirm"})
    void recoveryBindingAndIncubationRemainAvailable(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(jsonPath("$.code").value(0));
        verifyNoInteractions(gate);
    }

    @Test
    void boundOrNonWechatAccountKeepsExistingBusinessAccess() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/star/balance")).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void administratorIsNotBlockedByWechatProductGate() throws Exception {
        user.setSuperAdmin(1);
        mvc.perform(request(HttpMethod.GET, "/star/balance")).andExpect(jsonPath("$.code").value(0));
        verifyNoInteractions(gate);
    }

    @Test
    void unauthenticatedOrServerRequestsKeepTheirExistingAuthentication() throws Exception {
        user.setId(null);
        mvc.perform(request(HttpMethod.GET, "/config/agent-models"))
                .andExpect(jsonPath("$.code").value(0));
        verifyNoInteractions(gate);
    }

    @Test
    void profileWriteDoesNotInheritProfileReadExemption() throws Exception {
        when(gate.requiresPhoneBinding(7L)).thenReturn(true);
        mvc.perform(request(HttpMethod.PUT, "/wechat/profile"))
                .andExpect(jsonPath("$.code").value(10228));
    }

    /** 测试业务端点仅表示是否真正到达业务层，认证仍由现有 Shiro 负责。 */
    @RestController
    static class BusinessController {
        @RequestMapping("/**")
        Result<Void> invoke() {
            return new Result<>();
        }
    }
}
