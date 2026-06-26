package xiaozhi.modules.companion.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xiaozhi.modules.companion.service.CompanionService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanionMoodRefreshTask 每日心情刷新任务")
class CompanionMoodRefreshTaskTest {

    @Mock
    private CompanionService companionService;

    @Test
    @DisplayName("定时任务触发时委托给 CompanionService.refreshAllMoods()")
    void scheduledTask_delegatesToRefreshAllMoods() {
        CompanionMoodRefreshTask task = new CompanionMoodRefreshTask(companionService);

        task.refreshMoods();

        verify(companionService).refreshAllMoods();
    }
}
