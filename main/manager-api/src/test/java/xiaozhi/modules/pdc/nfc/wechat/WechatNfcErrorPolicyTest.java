package xiaozhi.modules.pdc.nfc.wechat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WechatNfcErrorPolicy 错误码映射测试")
class WechatNfcErrorPolicyTest {

    @Test
    @DisplayName("44990 → RETRYABLE")
    void code44990IsRetryable() {
        assertThat(WechatNfcErrorPolicy.classify(44990))
                .isEqualTo(WechatNfcErrorAction.RETRYABLE);
    }

    @Test
    @DisplayName("44993 → QUOTA_DEFER")
    void code44993IsQuotaDefer() {
        assertThat(WechatNfcErrorPolicy.classify(44993))
                .isEqualTo(WechatNfcErrorAction.QUOTA_DEFER);
    }

    @Test
    @DisplayName("40002 → TASK_FATAL")
    void code40002IsTaskFatal() {
        assertThat(WechatNfcErrorPolicy.classify(40002))
                .isEqualTo(WechatNfcErrorAction.TASK_FATAL);
    }

    @Test
    @DisplayName("9800003 → TASK_FATAL")
    void code9800003IsTaskFatal() {
        assertThat(WechatNfcErrorPolicy.classify(9800003))
                .isEqualTo(WechatNfcErrorAction.TASK_FATAL);
    }

    @Test
    @DisplayName("未知 errcode → TASK_FATAL")
    void unknownCodeIsTaskFatal() {
        assertThat(WechatNfcErrorPolicy.classify(99999))
                .isEqualTo(WechatNfcErrorAction.TASK_FATAL);
    }

    @Test
    @DisplayName("网络错误 → RETRYABLE")
    void networkErrorIsRetryable() {
        assertThat(WechatNfcErrorPolicy.networkError())
                .isEqualTo(WechatNfcErrorAction.RETRYABLE);
    }

    @Test
    @DisplayName("TASK_FATAL 错误码全覆盖")
    void allFatalCodesAreTaskFatal() {
        int[] fatalCodes = {40002, 40165, 40212, 85079, 9800003, 9800007, 9800008, 9800009};
        for (int code : fatalCodes) {
            assertThat(WechatNfcErrorPolicy.classify(code))
                    .as("errcode %d should be TASK_FATAL", code)
                    .isEqualTo(WechatNfcErrorAction.TASK_FATAL);
        }
    }
}
