package xiaozhi.modules.sys.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import xiaozhi.modules.sys.dao.SysOperationLogDao;
import xiaozhi.modules.sys.entity.SysOperationLogEntity;
import xiaozhi.modules.sys.enums.OperationType;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OperationLogServiceImpl 测试")
class OperationLogServiceImplTest {

    @Mock
    private SysOperationLogDao operationLogDao;

    @InjectMocks
    private OperationLogServiceImpl service;

    @Test
    @DisplayName("成功操作：状态为1，错误信息为空，类型与描述正确")
    void record_success_mapsFields() {
        service.record(OperationType.CHAT_HISTORY_EXPORT, true, "{\"email\":\"a***@b.com\"}", null);

        ArgumentCaptor<SysOperationLogEntity> captor = ArgumentCaptor.forClass(SysOperationLogEntity.class);
        verify(operationLogDao).insert(captor.capture());
        SysOperationLogEntity entity = captor.getValue();
        assertThat(entity.getOperationType()).isEqualTo("CHAT_HISTORY_EXPORT");
        assertThat(entity.getOperationDesc()).isEqualTo("导出聊天记录");
        assertThat(entity.getStatus()).isEqualTo(1);
        assertThat(entity.getErrorMsg()).isNull();
        assertThat(entity.getDetail()).isEqualTo("{\"email\":\"a***@b.com\"}");
    }

    @Test
    @DisplayName("失败操作：状态为0，错误信息被记录并截断到500字符")
    void record_failure_recordsError() {
        String longError = "x".repeat(600);
        service.record(OperationType.CHAT_HISTORY_DELETE, false, null, longError);

        ArgumentCaptor<SysOperationLogEntity> captor = ArgumentCaptor.forClass(SysOperationLogEntity.class);
        verify(operationLogDao).insert(captor.capture());
        SysOperationLogEntity entity = captor.getValue();
        assertThat(entity.getStatus()).isEqualTo(0);
        assertThat(entity.getErrorMsg()).hasSize(500);
    }

    @Test
    @DisplayName("落库失败不抛出异常（不影响主流程）")
    void save_dbFailure_doesNotThrow() {
        doThrow(new RuntimeException("db down")).when(operationLogDao).insert(any(SysOperationLogEntity.class));

        SysOperationLogEntity entity = new SysOperationLogEntity();
        entity.setOperationType("CHAT_HISTORY_EXPORT");
        // 不应抛出异常
        service.save(entity);
    }
}
