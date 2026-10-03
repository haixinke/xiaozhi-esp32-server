package xiaozhi.modules.pet.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.pet.service.PhotoCaptionService;

/**
 * 写真文案生成实现。
 *
 * <p>优先调默认 LLM 动态生成（原型 + 当日日期作上下文），输出经清洗与长度校验；
 * 不合格重试一次，仍失败回退预置文案池随机抽一条，保证 LLM 不可用时体验无损。
 * 文案渲染进图片内，长度失控会破坏图内排版，故严格限制 15 字以内。
 */
@Slf4j
@Service
public class PhotoCaptionServiceImpl implements PhotoCaptionService {

    private static final String PROTOTYPE_KOI = "锦鲤";
    private static final String PROTOTYPE_RABBIT = "玉兔";

    /** 预置兜底文案池：LLM 不可用/输出不合规时保证体验无损（00后气质：活泼、俏皮、佛系、幽默） */
    private static final Map<String, List<String>> CAPTION_POOL = Map.of(
            PROTOTYPE_KOI, List.of(
                    "转发这条锦鲤，好运直接拉满",
                    "摸鱼摸到大奖，锦鲤本鲤",
                    "今天也要做一条好运爆棚的鱼",
                    "水逆退散，锦鲤罩我",
                    "躺平的鱼运气都不会太差",
                    "锦鲤附身，主打一个心想事成",
                    "不慌不忙，好运正在路上",
                    "摸鱼一时爽，好运经常来",
                    "本鲤出马，烦恼全挂",
                    "佛系养鱼，好运自来"),
            PROTOTYPE_RABBIT, List.of(
                    "月宫在逃小可爱，已上线",
                    "兔兔我啊，今天也在认真可爱",
                    "蹦跶两下，烦恼清零",
                    "吃可爱长大的，不服来rua",
                    "玉兔营业中，快乐不打烊",
                    "随缘可爱，佛系卖萌",
                    "耳朵一竖，好事将至",
                    "今天份的快乐已充好电",
                    "慢生活万岁，兔兔不着急",
                    "温柔有光，自带治愈buff"));

    /** 未知原型兜底用玉兔（与 IP 参考图未知原型策略一致） */
    private static final String PROTOTYPE_DEFAULT = PROTOTYPE_RABBIT;

    /** 图内排版可容纳的最大文案长度（按 Unicode 码点计，一个汉字/emoji 均为 1） */
    private static final int MAX_CAPTION_CODEPOINTS = 15;

    /** LLM 不合规输出最多重试 1 次（共 2 次尝试），再失败回退预置池 */
    private static final int MAX_ATTEMPTS = 2;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final String PROMPT_TEMPLATE = """
            你是蛋宝宝AI宠物的文案写手。请为「%s」写真写一句渲染在图片内的中文文案。\
            要求：佛系、幽默、阳光，符合现代中国年轻人的调性；不超过15个汉字；\
            只输出文案本身，不要引号、不要解释、不要堆砌标点。今天是%s。""";

    private final LLMService llmService;

    public PhotoCaptionServiceImpl(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String drawCaption(String prototype) {
        // 原型为空时按默认原型进 prompt，避免 String.format 渲染出字面「null」
        String safePrototype = prototype != null ? prototype : PROTOTYPE_DEFAULT;
        String prompt = String.format(PROMPT_TEMPLATE, safePrototype, today());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String raw = null;
            try {
                raw = llmService.generateText(prompt);
            } catch (Exception e) {
                log.warn("LLM写真文案生成异常 attempt={}", attempt, e);
            }
            String caption = sanitize(raw);
            if (isValid(caption)) {
                return caption;
            }
            log.warn("LLM写真文案不合规 attempt={}, raw={}", attempt, raw);
        }
        return drawFromPool(prototype);
    }

    /** 清洗 LLM 输出：去首尾空白、剥离成对引号、去掉换行与末尾标点（文案渲染进图片，不允许换行） */
    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String caption = raw.trim().replace("\n", "").replace("\r", "");
        // 剥离成对包裹引号（中文/英文），只剥一层
        if (caption.length() >= 2 && isQuotePair(caption.charAt(0), caption.charAt(caption.length() - 1))) {
            caption = caption.substring(1, caption.length() - 1).trim();
        }
        // 剥离末尾标点，与预置池风格一致
        return caption.replaceAll("[。！？!?.，,]+$", "");
    }

    private static boolean isQuotePair(char first, char last) {
        return (first == '「' && last == '」') || (first == '『' && last == '』')
                || (first == '"' && last == '"') || (first == '\'' && last == '\'')
                || (first == '“' && last == '”') || (first == '‘' && last == '’')
                || (first == '《' && last == '》');
    }

    private static boolean isValid(String caption) {
        return StringUtils.isNotBlank(caption)
                && caption.codePointCount(0, caption.length()) <= MAX_CAPTION_CODEPOINTS;
    }

    private static String drawFromPool(String prototype) {
        List<String> pool = CAPTION_POOL.getOrDefault(prototype, CAPTION_POOL.get(PROTOTYPE_DEFAULT));
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** 当日日期（如 10月3日），给 LLM 应景上下文 */
    private static String today() {
        LocalDate now = LocalDate.now(ZONE);
        return now.getMonthValue() + "月" + now.getDayOfMonth() + "日";
    }
}
