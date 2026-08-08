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

    @Scheduled(cron = "0 0 * * * ?", zone = "Asia/Shanghai")
    public void refreshStates() {
        ZonedDateTime evaluatedAt = ZonedDateTime.ofInstant(clock.instant(), clock.getZone());
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
