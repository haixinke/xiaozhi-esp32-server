package xiaozhi.modules.storyengine.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xiaozhi.modules.storyengine.constant.StoryPetPrototype;
import xiaozhi.modules.storyengine.model.StoryEvaluationResult;
import xiaozhi.modules.storyengine.service.PrototypeStoryStateService;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

/**
 * 原型共享故事状态整点调度任务。每小时整点（Asia/Shanghai）遍历固定支持的原型列表，
 * 各原型独立事务、互不阻塞；单个原型失败不影响其他原型。日志只记录原型、时槽与聚合计数，
 * 不包含用户/宠物实例/设备等敏感标识。
 */
@Slf4j
@Component
public class PrototypeStoryStateRefreshTask {
    private final PrototypeStoryStateService service;
    private final Clock clock;

    public PrototypeStoryStateRefreshTask(PrototypeStoryStateService service,
                                          @Qualifier("storyRuntimeClock") Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    // 每小时整点刷新一次原型故事状态，与图片/权重时段的整点边界对齐
    @Scheduled(cron = "0 0 * * * ?", zone = "Asia/Shanghai")
    public void refreshStates() {
        ZonedDateTime evaluatedAt = ZonedDateTime.ofInstant(clock.instant(), clock.getZone());
        log.info("宠物原型故事状态刷新开始 hour={}", evaluatedAt.truncatedTo(ChronoUnit.HOURS));
        Map<StoryEvaluationResult, Integer> counts = new EnumMap<>(StoryEvaluationResult.class);
        int failures = 0;
        for (StoryPetPrototype prototype : StoryPetPrototype.values()) {
            try {
                StoryEvaluationResult result = service.evaluate(prototype.value(), evaluatedAt);
                counts.merge(result, 1, Integer::sum);
                if (result == StoryEvaluationResult.KEPT_INVALID_CONFIGURATION) {
                    log.warn("宠物原型故事配置不完整 prototype={}, hour={}",
                            prototype.value(), evaluatedAt.truncatedTo(ChronoUnit.HOURS));
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("宠物原型故事状态刷新失败 prototype={}, hour={}",
                        prototype.value(), evaluatedAt.truncatedTo(ChronoUnit.HOURS));
            }
        }
        log.info("宠物原型故事状态刷新完成 hour={}, results={}, failures={}",
                evaluatedAt.truncatedTo(ChronoUnit.HOURS), counts, failures);
    }
}
