package xiaozhi.modules.companion.task;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xiaozhi.modules.companion.service.CompanionService;

/**
 * AI 伴侣每日心情刷新定时任务。
 * 每天零点为所有伴侣生成新的今日心情，并同步到对应智能体。
 */
@Slf4j
@Component
@AllArgsConstructor
public class CompanionMoodRefreshTask {

    private final CompanionService companionService;

    // @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void refreshMoods() {
        log.info("定时任务：开始刷新 AI 伴侣今日心情");
        companionService.refreshAllMoods();
        log.info("定时任务：开始刷新 AI 伴侣亲密度");
        companionService.refreshAllIntimacy();
    }
}
