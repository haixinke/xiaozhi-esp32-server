package xiaozhi.modules.listen.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.game.service.GameStarRewardService;
import xiaozhi.modules.game.vo.GameStarRewardResult;
import xiaozhi.modules.listen.dao.ListenAnswerDao;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.entity.ListenAnswerEntity;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenAnswerService;
import xiaozhi.modules.listen.service.ListenFeedbackService;
import xiaozhi.modules.listen.util.ListenOptionsParser;
import xiaozhi.modules.listen.vo.ListenDrawVO;
import xiaozhi.modules.listen.vo.ListenSubmitResultVO;

/**
 * 听你说·小程序答题 Service 实现。
 * 抽题：SQL 随机（ORDER BY RAND()），排除用户已答过的组（组内任一题有答案即整组排除）。
 * 提交：整组一次性落库，幂等由 UNIQUE KEY (user_id, question_id) 兜底。
 */
@Service
@Slf4j
public class ListenAnswerServiceImpl implements ListenAnswerService {

    /** 听你说游戏类型 code（字典 GAME_TYPE 的 code），作为游戏每日首次完成奖励的结算标识 */
    private static final String GAME_TYPE_LISTEN_TO_YOU = "LISTEN_TO_YOU";

    private final ListenGroupDao listenGroupDao;
    private final ListenQuestionDao listenQuestionDao;
    private final ListenAnswerDao listenAnswerDao;

    /** 游戏星星结算能力：整组提交成功后按游戏管理配置的 earnStars 发当日首次完成奖励 */
    private final GameStarRewardService gameStarRewardService;

    /** 事件发布：答题落库事务提交后触发反馈文案生成（异步，不阻塞本事务） */
    private final ApplicationEventPublisher eventPublisher;

    /** 每日可答题组数上限，默认 5（0 或负数视为不限） */
    private final int dailyGroupLimit;

    /** 自定义文本最大长度（与 DDL VARCHAR(200) 一致） */
    private static final int CUSTOM_TEXT_MAX_LENGTH = 200;

    /** 日界时区：额度按 Asia/Shanghai 自然日切分（与 AI 生图配额、每日聊天提醒一致） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 构造注入（不用 @AllArgsConstructor）：dailyGroupLimit 需要 @Value 注入，
     * 交给 Lombok 的构造器会被当成按类型注入的 int 参数，启动即
     * NoSuchBeanDefinitionException: No qualifying bean of type 'int'。
     */
    public ListenAnswerServiceImpl(ListenGroupDao listenGroupDao,
            ListenQuestionDao listenQuestionDao,
            ListenAnswerDao listenAnswerDao,
            GameStarRewardService gameStarRewardService,
            ApplicationEventPublisher eventPublisher,
            @Value("${listen.daily-group-limit:5}") int dailyGroupLimit) {
        this.listenGroupDao = listenGroupDao;
        this.listenQuestionDao = listenQuestionDao;
        this.listenAnswerDao = listenAnswerDao;
        this.gameStarRewardService = gameStarRewardService;
        this.eventPublisher = eventPublisher;
        this.dailyGroupLimit = dailyGroupLimit;
    }

    @Override
    public ListenDrawVO draw(Long userId) {
        // 额度是当天的硬门槛，优先于「所有题目都答过」提示：题库可能还有未答组，只是今天不能答了
        ensureDailyQuotaAvailable(userId);

        // 已答过的组 id：组内任一题有答案即整组排除（按组判定答过）
        List<Long> answeredGroupIds = listenAnswerDao.selectObjs(new QueryWrapper<ListenAnswerEntity>()
                .select("DISTINCT group_id")
                .eq("user_id", userId));

        QueryWrapper<ListenGroupEntity> wrapper = new QueryWrapper<>();
        // 参数化 notIn，避免 notInSql 拼字符串
        wrapper.notIn(!answeredGroupIds.isEmpty(), "id", answeredGroupIds);
        // SQL 随机（ORDER BY RAND()），Oceanbase/MySQL 兼容，开发库实测可用
        wrapper.orderByAsc("RAND()").last("LIMIT 1");
        ListenGroupEntity group = listenGroupDao.selectOne(wrapper);
        if (group == null) {
            return null;
        }

        List<ListenQuestionEntity> questions = listenQuestionDao.selectList(
                new QueryWrapper<ListenQuestionEntity>().eq("group_id", group.getId()).orderByAsc("sort_order"));

        ListenDrawVO vo = new ListenDrawVO();
        vo.setGroupId(group.getId());
        vo.setGroupName(group.getName());
        vo.setQuestions(questions.stream().map(this::toQuestionVo).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ListenSubmitResultVO submitAnswers(Long userId, ListenAnswerSubmitDTO dto) {
        // 校验 questionId 确实属于 groupId：冗余 group_id 列支撑抽题按组排除，
        // 错配会污染排除索引（UNIQUE 约束兜不住），必须拒绝
        List<ListenQuestionEntity> questionsInGroup = listenQuestionDao.selectList(
                new QueryWrapper<ListenQuestionEntity>().eq("group_id", dto.getGroupId()));
        Set<Long> questionIdsInGroup = questionsInGroup.stream()
                .map(ListenQuestionEntity::getId).collect(Collectors.toSet());

        // 额度校验放在查询组内题目之后、逐条写库之前：这样组内题目归属错配时先在下方的循环里报
        // LISTEN_QUESTION_NOT_FOUND，不会被「今日次数已用完」盖住。事务会回滚，不会留半截数据。
        // 已答过的组（无论哪天答的）重提是幂等空操作，不占新额度、不受上限拦截，
        // 否则用户网络重试会被误报「今日次数已用完」。
        boolean alreadyAnswered = hasAnsweredGroup(userId, dto.getGroupId());
        if (!alreadyAnswered) {
            ensureDailyQuotaAvailable(userId);
        }
        // 题目 id -> 选项 key -> 选项文本，用于选 A-D 时回填 custom_text（方便后续查看答案）
        java.util.Map<Long, java.util.Map<String, String>> optionTextByQuestion = new java.util.HashMap<>();
        for (ListenQuestionEntity question : questionsInGroup) {
            java.util.Map<String, String> textByKey = new java.util.HashMap<>();
            for (ListenOptionsParser.Option option : ListenOptionsParser.parse(question.getOptionsJson())) {
                textByKey.put(option.key(), option.text());
            }
            optionTextByQuestion.put(question.getId(), textByKey);
        }
        for (ListenAnswerSubmitDTO.AnswerItem item : dto.getAnswers()) {
            if (!questionIdsInGroup.contains(item.getQuestionId())) {
                throw new RenException(ErrorCode.LISTEN_QUESTION_NOT_FOUND);
            }
            // 仅校验文本长度；不校验文本必须对应 E 选项（信任前端只在选 E 时传文本）
            if (StringUtils.length(item.getCustomText()) > CUSTOM_TEXT_MAX_LENGTH) {
                throw new RenException(ErrorCode.LISTEN_CUSTOM_TEXT_TOO_LONG);
            }
            ListenAnswerEntity entity = new ListenAnswerEntity();
            entity.setUserId(userId);
            entity.setGroupId(dto.getGroupId());
            entity.setQuestionId(item.getQuestionId());
            entity.setOptionKey(item.getOptionKey());
            // 选 A-D 时把选项文本冗余进 custom_text，方便后续直接查看答案；
            // 选 E 时用用户输入文本（可空，选 E 不强制输入）
            String customText = item.getCustomText();
            if (StringUtils.isBlank(customText)) {
                customText = optionTextByQuestion.getOrDefault(item.getQuestionId(), java.util.Map.of())
                        .get(item.getOptionKey());
            }
            entity.setCustomText(customText);
            try {
                listenAnswerDao.insert(entity);
            } catch (DuplicateKeyException e) {
                // 幂等：UNIQUE (user_id, question_id) 冲突说明该题已答过，忽略本行（网络重试/双击安全）
            }
        }
        // 事务提交后异步生成个性化反馈文案（LLM 慢/挂不影响本响应；重提幂等空操作不重复生成）
        if (!alreadyAnswered) {
            eventPublisher.publishEvent(new ListenFeedbackService.FeedbackRequestedEvent(userId, dto.getGroupId(), dto.getAnswers()));
        }
        return buildSubmitResult(userId);
    }

    /**
     * 提交成功后的星星结算：按游戏管理配置的 earnStars 发当日首次完成奖励。
     * <p>
     * 结算能力本身幂等（同一 userId+游戏类型+北京自然日最多一份），重复提交/网络重试
     * 会命中「当日已领取」返回原结果而不重复入账，故无需在此先判是否新答组。
     * 奖励金额固定为 earnStars，不随连续天数递增；免费双零或消费型游戏配置下不发星。
     */
    private ListenSubmitResultVO buildSubmitResult(Long userId) {
        GameStarRewardResult reward = gameStarRewardService.claimGameReward(userId, GAME_TYPE_LISTEN_TO_YOU);
        ListenSubmitResultVO vo = new ListenSubmitResultVO();
        vo.setAwardedStars(reward.getNewRewardedStars());
        vo.setRewardStatus(reward.getStatus().name());
        return vo;
    }

    /**
     * 校验今日答题额度。额度用尽抛 LISTEN_DAILY_LIMIT_REACHED。
     * <p>
     * 计数取 DB 实时统计而非 Redis 计数器：答案行本身已在库、按组天然去重，
     * Redis 一旦丢 key 就能刷额度，且要与提交事务对齐的成本更高（与 AI 生图配额同款做法）。
     * <p>
     * 已知并发窗口：先 count 后 insert 未加行锁，同一用户并发提交两组时可能各读到 4 组而双双通过，
     * 当日落到 6 组。这是体验型限额而非资金/权益，触发需要同时抽两组再并发提交，概率可忽略；
     * 上 Redis 原子计数或行锁会引入新的一致性问题，代价大于收益。
     */
    private void ensureDailyQuotaAvailable(Long userId) {
        // 0 或负数表示不限（配置化留出调试/活动场景的口子）
        if (dailyGroupLimit <= 0) {
            return;
        }
        if (todayAnsweredGroupCount(userId) >= dailyGroupLimit) {
            throw new RenException(ErrorCode.LISTEN_DAILY_LIMIT_REACHED);
        }
    }

    /**
     * 今日已答组数（按 create_date 落今日内、DISTINCT group_id 去重）。
     * 日界按 Asia/Shanghai 自然日 00:00，与每日聊天提醒、AI 生图配额口径一致。
     */
    private long todayAnsweredGroupCount(Long userId) {
        Date dayStart = Date.from(LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant());
        List<Object> groupIds = listenAnswerDao.selectObjs(new QueryWrapper<ListenAnswerEntity>()
                .select("DISTINCT group_id")
                .eq("user_id", userId)
                .ge("create_date", dayStart));
        return groupIds.size();
    }

    /** 该组是否已有答案（任何日期）——用于判定重提是否属于幂等空操作 */
    private boolean hasAnsweredGroup(Long userId, Long groupId) {
        return listenAnswerDao.selectCount(new QueryWrapper<ListenAnswerEntity>()
                .eq("user_id", userId)
                .eq("group_id", groupId)) > 0;
    }

    /** 实体转抽题题目 VO：options_json JSON 列解析为 key/text 选项列表（统一走 ListenOptionsParser） */
    private ListenDrawVO.Question toQuestionVo(ListenQuestionEntity entity) {
        ListenDrawVO.Question question = new ListenDrawVO.Question();
        question.setId(entity.getId());
        question.setText(entity.getText());
        question.setOptions(ListenOptionsParser.parse(entity.getOptionsJson()).stream()
                .map(o -> {
                    ListenDrawVO.Option option = new ListenDrawVO.Option();
                    option.setKey(o.key());
                    option.setText(o.text());
                    return option;
                })
                .collect(Collectors.toList()));
        return question;
    }
}
