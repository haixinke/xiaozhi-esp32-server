package xiaozhi.modules.storyengine.service;

import xiaozhi.modules.storyengine.model.StoryEvaluationResult;

import java.time.ZonedDateTime;

/**
 * 原型级共享故事状态服务。负责一个宠物原型的一次整点检查与状态推进。
 */
public interface PrototypeStoryStateService {

    /**
     * 在独立事务中对指定原型执行一次整点检查：行锁 + 时槽幂等 + 到期判断 + 选择 + 归档/更新。
     *
     * @param prototype   宠物原型（锦鲤/玉兔）
     * @param evaluatedAt 评估时间（Asia/Shanghai）
     * @return 本次检查结果
     */
    StoryEvaluationResult evaluate(String prototype, ZonedDateTime evaluatedAt);
}
