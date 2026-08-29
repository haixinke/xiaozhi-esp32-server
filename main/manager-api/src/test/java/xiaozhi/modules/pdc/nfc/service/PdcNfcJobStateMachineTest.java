package xiaozhi.modules.pdc.nfc.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.MessageUtils;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcBatchStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcSchemeJobStatus;
import xiaozhi.modules.pdc.nfc.constant.PdcNfcWriteJobStatus;

import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdcNfcJobStateMachineTest {

    private PdcNfcBatchStateMachine batchStateMachine;
    private PdcNfcSchemeJobStateMachine schemeJobStateMachine;
    private PdcNfcWriteJobStateMachine writeJobStateMachine;

    @BeforeAll
    static void initMessageSource() throws Exception {
        MessageSource mockSource = mock(MessageSource.class);
        when(mockSource.getMessage(anyString(), any(), any(), any(Locale.class)))
                .thenReturn("mock message");
        Field field = MessageUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mockSource);
    }

    @BeforeEach
    void setUp() {
        batchStateMachine = new PdcNfcBatchStateMachine();
        schemeJobStateMachine = new PdcNfcSchemeJobStateMachine();
        writeJobStateMachine = new PdcNfcWriteJobStateMachine();
    }

    // --- Batch state machine ---

    @Test
    void batchPermitsDeclaredTransitions() {
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.DRAFT, PdcNfcBatchStatus.SCHEME_GENERATING)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.SCHEME_GENERATING, PdcNfcBatchStatus.READY_FOR_WRITE)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.READY_FOR_WRITE, PdcNfcBatchStatus.WRITING)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.WRITING, PdcNfcBatchStatus.READY_FOR_STOCK)).doesNotThrowAnyException();
        // 写卡任务取消时，批次允许从 WRITING 回退 READY_FOR_WRITE 以便重建任务
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.WRITING, PdcNfcBatchStatus.READY_FOR_WRITE)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.READY_FOR_STOCK, PdcNfcBatchStatus.COMPLETED)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.COMPLETED, PdcNfcBatchStatus.CLOSED)).doesNotThrowAnyException();
    }

    @Test
    void batchPermitsCancelFromActiveStates() {
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.DRAFT, PdcNfcBatchStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.SCHEME_GENERATING, PdcNfcBatchStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.READY_FOR_WRITE, PdcNfcBatchStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.WRITING, PdcNfcBatchStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.READY_FOR_STOCK, PdcNfcBatchStatus.CANCELLED)).doesNotThrowAnyException();
    }

    @Test
    void batchRejectsClosedAndCancelledRecovery() {
        for (PdcNfcBatchStatus target : PdcNfcBatchStatus.values()) {
            assertThatThrownBy(() -> batchStateMachine.requireTransition(PdcNfcBatchStatus.CLOSED, target))
                    .isInstanceOf(RenException.class);
            assertThatThrownBy(() -> batchStateMachine.requireTransition(PdcNfcBatchStatus.CANCELLED, target))
                    .isInstanceOf(RenException.class);
        }
    }

    @Test
    void batchRejectsInvalidTransitions() {
        assertThatThrownBy(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.DRAFT, PdcNfcBatchStatus.WRITING))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.DRAFT, PdcNfcBatchStatus.COMPLETED))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> batchStateMachine.requireTransition(
                PdcNfcBatchStatus.COMPLETED, PdcNfcBatchStatus.CANCELLED))
                .isInstanceOf(RenException.class);
    }

    // --- Scheme job state machine ---

    @Test
    void schemeJobPermitsDeclaredTransitions() {
        assertThatCode(() -> schemeJobStateMachine.requireTransition(
                PdcNfcSchemeJobStatus.PENDING, PdcNfcSchemeJobStatus.RUNNING)).doesNotThrowAnyException();
        assertThatCode(() -> schemeJobStateMachine.requireTransition(
                PdcNfcSchemeJobStatus.PENDING, PdcNfcSchemeJobStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> schemeJobStateMachine.requireTransition(
                PdcNfcSchemeJobStatus.RUNNING, PdcNfcSchemeJobStatus.PARTIAL_SUCCESS)).doesNotThrowAnyException();
        assertThatCode(() -> schemeJobStateMachine.requireTransition(
                PdcNfcSchemeJobStatus.RUNNING, PdcNfcSchemeJobStatus.SUCCEEDED)).doesNotThrowAnyException();
        assertThatCode(() -> schemeJobStateMachine.requireTransition(
                PdcNfcSchemeJobStatus.RUNNING, PdcNfcSchemeJobStatus.FAILED)).doesNotThrowAnyException();
        assertThatCode(() -> schemeJobStateMachine.requireTransition(
                PdcNfcSchemeJobStatus.RUNNING, PdcNfcSchemeJobStatus.CANCELLED)).doesNotThrowAnyException();
    }

    @Test
    void schemeJobRejectsTerminalRecovery() {
        for (PdcNfcSchemeJobStatus target : PdcNfcSchemeJobStatus.values()) {
            assertThatThrownBy(() -> schemeJobStateMachine.requireTransition(PdcNfcSchemeJobStatus.SUCCEEDED, target))
                    .isInstanceOf(RenException.class);
            assertThatThrownBy(() -> schemeJobStateMachine.requireTransition(PdcNfcSchemeJobStatus.FAILED, target))
                    .isInstanceOf(RenException.class);
            assertThatThrownBy(() -> schemeJobStateMachine.requireTransition(PdcNfcSchemeJobStatus.CANCELLED, target))
                    .isInstanceOf(RenException.class);
            assertThatThrownBy(() -> schemeJobStateMachine.requireTransition(PdcNfcSchemeJobStatus.PARTIAL_SUCCESS, target))
                    .isInstanceOf(RenException.class);
        }
    }

    // --- Write job state machine ---

    @Test
    void writeJobPermitsDeclaredTransitions() {
        assertThatCode(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.CREATED, PdcNfcWriteJobStatus.EXPORTED)).doesNotThrowAnyException();
        assertThatCode(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.CREATED, PdcNfcWriteJobStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.EXPORTED, PdcNfcWriteJobStatus.RESULT_IMPORTED)).doesNotThrowAnyException();
        assertThatCode(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.EXPORTED, PdcNfcWriteJobStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.RESULT_IMPORTED, PdcNfcWriteJobStatus.COMPLETED)).doesNotThrowAnyException();
    }

    @Test
    void writeJobRejectsTerminalRecovery() {
        for (PdcNfcWriteJobStatus target : PdcNfcWriteJobStatus.values()) {
            assertThatThrownBy(() -> writeJobStateMachine.requireTransition(PdcNfcWriteJobStatus.COMPLETED, target))
                    .isInstanceOf(RenException.class);
            assertThatThrownBy(() -> writeJobStateMachine.requireTransition(PdcNfcWriteJobStatus.CANCELLED, target))
                    .isInstanceOf(RenException.class);
        }
    }

    @Test
    void writeJobRejectsInvalidTransitions() {
        assertThatThrownBy(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.CREATED, PdcNfcWriteJobStatus.RESULT_IMPORTED))
                .isInstanceOf(RenException.class);
        assertThatThrownBy(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.EXPORTED, PdcNfcWriteJobStatus.COMPLETED))
                .isInstanceOf(RenException.class);
    }

    @Test
    void writeJobAllowsCreatedToCompletedForManualMode() {
        // ADR 0003：手动模式不经过导出/导入，全部资产验证通过后 CREATED 直达 COMPLETED
        assertThatCode(() -> writeJobStateMachine.requireTransition(
                PdcNfcWriteJobStatus.CREATED, PdcNfcWriteJobStatus.COMPLETED)).doesNotThrowAnyException();
    }
}
