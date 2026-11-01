package xiaozhi.modules.pdc.nfc.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.support.MessageSourceMockSupport;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static xiaozhi.modules.pdc.nfc.constant.PdcNfcAssetStatus.*;

class PdcNfcAssetStateMachineTest {

    private PdcNfcAssetStateMachine stateMachine;

    // MessageUtils 现从 SpringContextUtils 现取 messageSource，改为挂 mock ApplicationContext
    @BeforeAll
    static void initMessageSource() {
        MessageSourceMockSupport.install();
    }

    @BeforeEach
    void setUp() {
        stateMachine = new PdcNfcAssetStateMachine();
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED,SCHEME_GENERATED", "SCHEME_GENERATED,WRITTEN",
        "WRITTEN,VERIFIED", "VERIFIED,IN_STOCK",
        "IN_STOCK,ACTIVE", "ACTIVE,CLAIMED",
        "CREATED,SCRAPPED", "SCHEME_GENERATED,SCRAPPED",
        "WRITTEN,SCRAPPED", "VERIFIED,SCRAPPED",
        "IN_STOCK,DISABLED", "ACTIVE,DISABLED", "CLAIMED,DISABLED"
    })
    void permitsOnlyDeclaredTransitions(PdcNfcAssetStatus from, PdcNfcAssetStatus to) {
        assertThatCode(() -> stateMachine.requireTransition(from, to)).doesNotThrowAnyException();
    }

    @Test
    void scrappedAndDisabledCannotRecover() {
        for (PdcNfcAssetStatus target : PdcNfcAssetStatus.values()) {
            assertThatThrownBy(() -> stateMachine.requireTransition(SCRAPPED, target))
                    .isInstanceOf(RenException.class);
            assertThatThrownBy(() -> stateMachine.requireTransition(DISABLED, target))
                    .isInstanceOf(RenException.class);
        }
    }

    @Test
    void rejectsInvalidForwardTransitions() {
        assertThatThrownBy(() -> stateMachine.requireTransition(CREATED, WRITTEN))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition(CREATED, ACTIVE))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition(IN_STOCK, CLAIMED))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition(CLAIMED, ACTIVE))
                .isInstanceOf(RenException.class);
    }

    @Test
    void rejectsSelfTransitions() {
        for (PdcNfcAssetStatus status : PdcNfcAssetStatus.values()) {
            assertThatThrownBy(() -> stateMachine.requireTransition(status, status))
                    .isInstanceOf(RenException.class);
        }
    }
}
