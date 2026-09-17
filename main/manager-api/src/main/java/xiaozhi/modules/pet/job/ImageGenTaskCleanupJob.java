package xiaozhi.modules.pet.job;

import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.pet.service.ImageGenTaskService;

/**
 * AI生图超时任务兜底清理。
 *
 * <p>小程序切后台/杀进程会中断轮询，微信推送也可能丢失；超过 24h 未到终态的任务
 * 统一置 FAILED，避免僵尸任务堆积。数据库层单条批量更新，不分页加载。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenTaskCleanupJob {

    /** 任务最大存活时长 */
    private static final Duration MAX_TASK_AGE = Duration.ofHours(24);

    private final ImageGenTaskService imageGenTaskService;

    /** 每小时执行一次，首次延迟 10 分钟（避开启动期） */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 600_000L)
    public void cleanup() {
        try {
            imageGenTaskService.cleanupStaleTasks(MAX_TASK_AGE);
        } catch (Exception e) {
            log.error("AI生图超时任务清理失败", e);
        }
    }
}
