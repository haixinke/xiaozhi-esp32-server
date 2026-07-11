package xiaozhi.modules.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import xiaozhi.modules.wechat.service.WechatPhoneGate;

class Oauth2FilterPhoneGateTest {

    @Test
    void allowsBindPhoneBeforePhoneIsBound() {
        TestFilter filter = new TestFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/wechat/bindPhone");

        assertThat(filter.phoneAccessAllowed(request, 7L)).isTrue();
    }

    @Test
    void rejectsOtherEndpointBeforePhoneIsBound() {
        TestFilter filter = new TestFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/subscription/me");

        assertThat(filter.phoneAccessAllowed(request, 7L)).isFalse();
    }

    @Test
    void allowsOtherEndpointAfterPhoneIsBound() {
        TestFilter filter = new TestFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/subscription/me");

        assertThat(filter.phoneAccessAllowed(request, 7L)).isTrue();
    }

    private static final class TestFilter extends Oauth2Filter {

        private TestFilter(boolean canAccess) {
            super(new StubWechatPhoneGate(canAccess));
        }

        private boolean phoneAccessAllowed(MockHttpServletRequest request, Long userId) {
            return isPhoneAccessAllowed(request, userId);
        }
    }

    private static final class StubWechatPhoneGate extends WechatPhoneGate {

        private final boolean canAccess;

        private StubWechatPhoneGate(boolean canAccess) {
            super(null);
            this.canAccess = canAccess;
        }

        @Override
        public boolean canAccess(Long userId) {
            return canAccess;
        }
    }
}
