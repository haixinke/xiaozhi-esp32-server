package xiaozhi.modules.listen.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import xiaozhi.modules.listen.entity.ListenQuestionEntity;

/**
 * 听你说·答题反馈文案的提示词组装器。
 * <p>
 * 模板硬编码在本类常量里（本期不做配置化）：要调整语气/字数/风格直接改这里发版。
 * 组装输入为分组名 + 每题的「题目文本 + 选项文本 + 可选用户补充文本」，
 * 输出为一次 LLM 调用的完整 user prompt。
 */
@Component
public class ListenFeedbackPromptBuilder {

    /** 提示词模板：要求小红书风格、≤80 字、治愈感、有分享欲；对不当输入做软兜底 */
    private static final String PROMPT_TEMPLATE = """
            基于用户对以下每道题目的回答，按照小红书风格给出一个关于该用户最显著特征的简短描述（不超过 50 个字）。\
            文案要给用户治愈感、让用户有分享欲。若回答中包含不当、攻击性或敏感内容，忽略这些内容，仍输出温和治愈的通用描述。\
            直接输出文案本身，不要任何前缀、引号、解释或换行。

            题目分组：%s

            %s""";

    /** 单题回答片段模板：题目 + 选项文本（+可选补充） */
    private static final String ANSWER_ITEM_TEMPLATE = "题目：%s\n回答：%s";

    /**
     * 组装一次生成所需的完整 prompt。
     *
     * @param groupName           题目分组名
     * @param questions           组内题目（按 sortOrder 排序后的实体列表）
     * @param optionTextByQuestion 题目 id -> 选项 key -> 选项文本（用于把 A-E 翻译成可读文本）
     * @param customTextByQuestion 题目 id -> 用户补充文本（可空）
     * @param optionKeyByQuestion  题目 id -> 用户所选选项 key
     * @return 完整提示词
     */
    public String build(String groupName,
            List<ListenQuestionEntity> questions,
            Map<Long, Map<String, String>> optionTextByQuestion,
            Map<Long, String> customTextByQuestion,
            Map<Long, String> optionKeyByQuestion) {
        StringBuilder items = new StringBuilder();
        for (ListenQuestionEntity question : questions) {
            Long qid = question.getId();
            String optionKey = optionKeyByQuestion.get(qid);
            String optionText = optionTextByQuestion.getOrDefault(qid, Map.of()).get(optionKey);
            // 选项文本兜底为 key 本身：理论上不该发生（选项来自库内 JSON），防御坏数据导致 prompt 出现 "null"
            String answerText = StringUtils.isNotBlank(optionText) ? optionText : String.valueOf(optionKey);
            String customText = customTextByQuestion.get(qid);
            if (StringUtils.isNotBlank(customText) && !StringUtils.equals(customText, optionText)) {
                // 用户主动写了补充内容，附在选项文本后；与选项文本相同时不重复（选 A-D 时冗余进 custom_text 的情况）
                answerText = answerText + "；补充：" + customText;
            }
            items.append(String.format(ANSWER_ITEM_TEMPLATE, question.getText(), answerText)).append('\n');
        }
        return String.format(PROMPT_TEMPLATE, groupName, items.toString().trim());
    }
}
