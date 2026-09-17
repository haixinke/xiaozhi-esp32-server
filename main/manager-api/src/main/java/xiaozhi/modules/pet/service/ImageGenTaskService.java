package xiaozhi.modules.pet.service;

import java.time.Duration;

import xiaozhi.modules.pet.vo.ImageGenTaskVO;

/**
 * AI生图任务服务。
 *
 * <p>编排"用户照片 + IP参考图 + 预置文案 → Seedream 合成图"的异步任务：
 * 创建（含照片审核提交）、查询、微信内容审核回调推进、超时清理。
 * 状态机见 {@link xiaozhi.modules.pet.constant.ImageGenTaskStatus}。
 */
public interface ImageGenTaskService {

    /**
     * 创建生图任务：校验登录/宠物/配额/照片URL，提交照片内容审核后落库（PENDING）。
     *
     * @param userId   当前用户ID
     * @param photoUrl 用户照片 OSS URL（必须是本 bucket ai-gen 前缀）
     * @return 任务视图（taskId + PENDING）
     */
    ImageGenTaskVO createTask(Long userId, String photoUrl);

    /**
     * 查询任务（仅限本人任务）。
     */
    ImageGenTaskVO getTask(Long userId, Long taskId);

    /**
     * 处理微信 mediaCheckAsync 审核结果推送。
     *
     * <p>按 traceId 路由：命中 photo_trace_id 走照片审核结论（通过→RUNNING 并触发生成），
     * 命中 result_trace_id 走结果图审核结论（通过→SUCCEEDED 且计入配额）。
     * 幂等：重复推送或状态已迁移时跳过。
     *
     * @param traceId 微信审核 trace_id
     * @param pass    审核是否通过（suggest=pass）
     */
    void handleMediaCheckResult(String traceId, boolean pass);

    /**
     * 清理超时未到终态的任务（PENDING/RUNNING/REVIEWING 且创建时间早于 cutoff），置 FAILED。
     *
     * @param maxAge 任务最大存活时长（如 24h）
     * @return 被清理的任务数
     */
    int cleanupStaleTasks(Duration maxAge);
}
