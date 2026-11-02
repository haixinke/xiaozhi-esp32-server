package xiaozhi.modules.listen.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.util.ListenOptionsParser;
import xiaozhi.modules.llm.service.LLMService;

/**
 * 听你说·答题反馈文案生成服务。
 * <p>
 * 链路：答题落库事务提交 → 事务事件 → 本服务异步调 LLM 生成 → 写 Redis（5 分钟 TTL）
 * → 小程序轮询 {@code GET /listen/feedback?groupId=} 取走。
 * 事务外异步执行：LLM 慢/挂不影响答题落库与星星结算；失败静默记日志，前端兜底固定文案。
 */
@Service
@Slf4j
public class ListenFeedbackService {

    private final LLMService llmService;
    private final ListenGroupDao listenGroupDao;
    private final ListenQuestionDao listenQuestionDao;
    private final RedisUtils redisUtils;

    /** 生成文案的最大保留字符数：超出截断（LLM 不保证严格遵守 ≤50 字 prompt 约束） */
    private static final int FEEDBACK_MAX_LENGTH = 80;

    /** Redis 反馈文案 TTL：秒。覆盖用户从提交到退出反馈页的典型时长即可，不持久化（本期产品决定） */
    private static final long FEEDBACK_TTL_SECONDS = 300L;

    /** 提示词模板：要求小红书风格、≤50 字、治愈感、有分享欲；对不当输入做软兜底 */
    private static final String PROMPT_TEMPLATE = """
            基于用户对以下每道题目的回答，按照小红书风格给出一个关于该用户最显著特征的简短描述（不超过 50 个字）。\
            文案要给用户治愈感、让用户有分享欲。若回答中包含不当、攻击性或敏感内容，忽略这些内容，仍输出温和治愈的通用描述。\
            直接输出文案本身，不要任何前缀、引号、解释或换行。

            题目分组：%s

            %s""";

    /** 单题回答片段模板：题目 + 选项文本（+可选补充） */
    private static final String ANSWER_ITEM_TEMPLATE = "题目：%s\n回答：%s";

    public ListenFeedbackService(LLMService llmService,
            ListenGroupDao listenGroupDao,
            ListenQuestionDao listenQuestionDao,
            RedisUtils redisUtils) {
        this.llmService = llmService;
        this.listenGroupDao = listenGroupDao;
        this.listenQuestionDao = listenQuestionDao;
        this.redisUtils = redisUtils;
    }

    /** 答题落库事务提交后触发的事件载体 */
    public record FeedbackRequestedEvent(Long userId, Long groupId, List<ListenAnswerSubmitDTO.AnswerItem> answers) {
    }

    /**
     * 事务提交后异步生成反馈文案并写 Redis。
     * <p>
     * 任何一步失败都静默吞掉、仅记日志：前端在拿不到文案时回落到固定文案，
     * 反馈属于「锦上添花」，不应影响答题主链路。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackRequested(FeedbackRequestedEvent event) {
        try {
            String feedback = doGenerate(event.groupId(), event.answers());
            if (StringUtils.isBlank(feedback)) {
                return;
            }
            String trimmed = feedback.trim();
            if (trimmed.length() > FEEDBACK_MAX_LENGTH) {
                trimmed = trimmed.substring(0, FEEDBACK_MAX_LENGTH);
            }
            redisUtils.set(RedisKeys.getListenFeedbackKey(event.userId(), event.groupId()), trimmed, FEEDBACK_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("生成听你说反馈文案失败，userId={}, groupId={}", event.userId(), event.groupId(), e);
        }
    }

    /**
     * 读取已生成的反馈文案（不消费：允许前端重试轮询，TTL 内自然过期）。
     *
     * @return 文案内容；未生成完成/已过期/生成失败时返回 null
     */
    public String getFeedback(Long userId, Long groupId) {
        Object value = redisUtils.get(RedisKeys.getListenFeedbackKey(userId, groupId));
        return value instanceof String s ? s : null;
    }

    /** 实际组装 prompt 并调 LLM；返回原始输出（未截断），失败返回 null */
    private String doGenerate(Long groupId, List<ListenAnswerSubmitDTO.AnswerItem> answers) {
        ListenGroupEntity group = listenGroupDao.selectById(groupId);
        if (group == null) {
            log.warn("反馈生成跳过：题目分组不存在，groupId={}", groupId);
            return null;
        }
        List<ListenQuestionEntity> questions = listenQuestionDao.selectList(
                new QueryWrapper<ListenQuestionEntity>().eq("group_id", groupId).orderByAsc("sort_order"));
        if (questions.isEmpty()) {
            log.warn("反馈生成跳过：组内无题目，groupId={}", groupId);
            return null;
        }

        String prompt = buildPrompt(group.getName(), questions, answers);
        return llmService.generateText(prompt);
    }

    /**
     * 组装一次生成所需的完整 prompt。
     * <p>
     * 模板硬编码在常量里（本期不做配置化）：要调整语气/字数/风格直接改这里发版。
     * 答案语义（选项 key → 展示文本、补充文本去重）在此一处完成，调用方无需再维护中间映射。
     */
    private String buildPrompt(String groupName,
            List<ListenQuestionEntity> questions,
            List<ListenAnswerSubmitDTO.AnswerItem> answers) {
        Map<Long, ListenAnswerSubmitDTO.AnswerItem> answerByQuestion = answers.stream()
                .collect(Collectors.toMap(ListenAnswerSubmitDTO.AnswerItem::getQuestionId, Function.identity(),
                        (first, ignored) -> first));
        StringBuilder items = new StringBuilder();
        for (ListenQuestionEntity question : questions) {
            Map<String, String> textByKey = ListenOptionsParser.parse(question.getOptionsJson()).stream()
                    .collect(Collectors.toMap(ListenOptionsParser.Option::key, ListenOptionsParser.Option::text,
                            (first, ignored) -> first));
            ListenAnswerSubmitDTO.AnswerItem answer = answerByQuestion.get(question.getId());
            String optionKey = answer == null ? null : answer.getOptionKey();
            String optionText = textByKey.get(optionKey);
            // 选项文本兜底为 key 本身：理论上不该发生（选项来自库内 JSON），防御坏数据导致 prompt 出现 "null"
            String answerText = StringUtils.isNotBlank(optionText) ? optionText : String.valueOf(optionKey);
            String customText = answer == null ? null : answer.getCustomText();
            if (StringUtils.isNotBlank(customText) && !StringUtils.equals(customText, optionText)) {
                // 用户主动写了补充内容，附在选项文本后；与选项文本相同时不重复（选 A-D 时冗余进 custom_text 的情况）
                answerText = answerText + "；补充：" + customText;
            }
            items.append(String.format(ANSWER_ITEM_TEMPLATE, question.getText(), answerText)).append('\n');
        }
        return String.format(PROMPT_TEMPLATE, groupName, items.toString().trim());
    }
}
