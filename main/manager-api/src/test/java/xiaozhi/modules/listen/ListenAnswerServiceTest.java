package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.listen.dao.ListenAnswerDao;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.entity.ListenAnswerEntity;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenAnswerService;
import xiaozhi.modules.listen.vo.ListenDrawVO;
import xiaozhi.modules.sys.dao.SysDictDataDao;
import xiaozhi.modules.sys.dao.SysDictTypeDao;
import xiaozhi.modules.sys.entity.SysDictDataEntity;
import xiaozhi.modules.sys.entity.SysDictTypeEntity;

/**
 * 听你说·小程序答题 Service 集成测试（真实 dev 库）。
 * 每个用例自造 LISTEN_CATEGORY 字典小类与题库/答题数据，AfterEach 物理清理，避免污染 dev 库与后续用例。
 * 测试用户 id 取负数大数，避免与真实用户撞车。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("听你说小程序答题集成测试")
class ListenAnswerServiceTest {

    @Autowired
    private ListenAnswerService listenAnswerService;
    @Autowired
    private ListenGroupDao listenGroupDao;
    @Autowired
    private ListenQuestionDao listenQuestionDao;
    @Autowired
    private ListenAnswerDao listenAnswerDao;
    @Autowired
    private SysDictDataDao sysDictDataDao;
    @Autowired
    private SysDictTypeDao sysDictTypeDao;
    @Autowired
    private RedisUtils redisUtils;

    /** 测试用户 id（负值，避开真实用户） */
    private static final long TEST_USER_ID = -9000000001L;

    /** 本用例创建的分组 id */
    private final List<Long> groupIds = new ArrayList<>();
    /** 本用例创建的题目 id */
    private final List<Long> questionIds = new ArrayList<>();
    /** 本用例创建的字典小类 id */
    private final List<Long> dictDataIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 答题记录按 user_id+question_id 清理（questionIds 覆盖本用例全部题目）
        if (!questionIds.isEmpty()) {
            listenAnswerDao.delete(new QueryWrapper<ListenAnswerEntity>()
                    .eq("user_id", TEST_USER_ID)
                    .in("question_id", questionIds));
        }
        questionIds.forEach(listenQuestionDao::deleteById);
        questionIds.clear();
        groupIds.forEach(listenGroupDao::deleteById);
        groupIds.clear();
        dictDataIds.forEach(sysDictDataDao::deleteById);
        dictDataIds.clear();
        // 字典列表有 Redis 缓存，用例间必须清掉
        redisUtils.delete(RedisKeys.getDictDataByTypeKey(Constant.DictType.LISTEN_CATEGORY.getValue()));
    }

    /** LISTEN_CATEGORY 字典大类由 Liquibase 迁移创建，用例直接在其下挂小类 */
    private Long dictTypeId() {
        SysDictTypeEntity type = sysDictTypeDao.selectOne(
                new QueryWrapper<SysDictTypeEntity>().eq("dict_type", Constant.DictType.LISTEN_CATEGORY.getValue()));
        assertThat(type).as("LISTEN_CATEGORY 字典大类应随迁移自动创建").isNotNull();
        return type.getId();
    }

    /** 新建一个题目大类小类，返回其 dict_value */
    private String createCategory(String dictValue, String dictLabel) {
        SysDictDataEntity entity = new SysDictDataEntity();
        entity.setDictTypeId(dictTypeId());
        entity.setDictValue(dictValue);
        entity.setDictLabel(dictLabel);
        entity.setSort(0);
        sysDictDataDao.insert(entity);
        dictDataIds.add(entity.getId());
        redisUtils.delete(RedisKeys.getDictDataByTypeKey(Constant.DictType.LISTEN_CATEGORY.getValue()));
        return dictValue;
    }

    /** 直接落库一个题组，返回 id */
    private Long createGroup(String categoryCode, String name, int sortOrder) {
        ListenGroupEntity entity = new ListenGroupEntity();
        entity.setCategoryCode(categoryCode);
        entity.setName(name);
        entity.setSortOrder(sortOrder);
        listenGroupDao.insert(entity);
        groupIds.add(entity.getId());
        return entity.getId();
    }

    /** 直接落库一道题，返回 id */
    private Long createQuestion(Long groupId, String text, int sortOrder) {
        ListenQuestionEntity entity = new ListenQuestionEntity();
        entity.setGroupId(groupId);
        entity.setText(text);
        entity.setType("SINGLE");
        entity.setOptionsJson(
                "[{\"key\":\"A\",\"text\":\"甲\"},{\"key\":\"B\",\"text\":\"乙\"},"
                        + "{\"key\":\"C\",\"text\":\"丙\"},{\"key\":\"D\",\"text\":\"丁\"},{\"key\":\"E\",\"text\":\"都不是\"}]");
        entity.setSortOrder(sortOrder);
        listenQuestionDao.insert(entity);
        questionIds.add(entity.getId());
        return entity.getId();
    }

    /** 构造单题答案项 */
    private ListenAnswerSubmitDTO.AnswerItem answerItem(Long questionId, String optionKey, String customText) {
        ListenAnswerSubmitDTO.AnswerItem item = new ListenAnswerSubmitDTO.AnswerItem();
        item.setQuestionId(questionId);
        item.setOptionKey(optionKey);
        item.setCustomText(customText);
        return item;
    }

    /** 构造整组提交 DTO */
    private ListenAnswerSubmitDTO submitDTO(Long groupId, List<ListenAnswerSubmitDTO.AnswerItem> answers) {
        ListenAnswerSubmitDTO dto = new ListenAnswerSubmitDTO();
        dto.setGroupId(groupId);
        dto.setAnswers(answers);
        return dto;
    }

    @Test
    @DisplayName("抽题：返回某组的题目与解析后的 A-E 选项（dev 库可能已有真实题库，不断言抽中本用例组）")
    void drawReturnsGroupWithParsedOptions() {
        String category = createCategory("TEST_ANS_A", "答题测试大类A");
        Long group = createGroup(category, "答题组一", 1);
        createQuestion(group, "第二题", 2);
        createQuestion(group, "第一题", 1);

        ListenDrawVO vo = listenAnswerService.draw(TEST_USER_ID);

        // 全新用户面临全库组（含真实数据），随机结果不可预设；只验证返回结构完整
        assertThat(vo).isNotNull();
        assertThat(vo.getGroupId()).isNotNull();
        assertThat(vo.getGroupName()).isNotBlank();
        assertThat(vo.getQuestions()).isNotEmpty();
        ListenDrawVO.Question firstQuestion = vo.getQuestions().get(0);
        assertThat(firstQuestion.getText()).isNotBlank();
        assertThat(firstQuestion.getOptions()).hasSize(5);
        assertThat(firstQuestion.getOptions()).extracting(ListenDrawVO.Option::getKey)
                .containsExactly("A", "B", "C", "D", "E");
    }

    @Test
    @DisplayName("抽题：组内只答过一题，整组即被排除（多次抽取均不返回已答组）")
    void drawExcludesGroupAnsweredPartially() {
        String category = createCategory("TEST_ANS_B", "答题测试大类B");
        Long answeredGroup = createGroup(category, "已答组", 1);
        Long freshGroup = createGroup(category, "未答组", 2);
        Long answeredQuestion = createQuestion(answeredGroup, "已答组的题", 1);
        createQuestion(freshGroup, "未答组的题", 1);

        // 只答已答组的一题
        listenAnswerService.submitAnswers(TEST_USER_ID,
                submitDTO(answeredGroup, List.of(answerItem(answeredQuestion, "A", null))));

        // 随机不保证抽中本用例组，但已答组永远不该出现；抽 20 次验证排除语义稳定
        for (int i = 0; i < 20; i++) {
            ListenDrawVO vo = listenAnswerService.draw(TEST_USER_ID);
            if (vo != null) {
                assertThat(vo.getGroupId()).isNotEqualTo(answeredGroup);
            }
        }
    }

    @Test
    @DisplayName("抽题：答过的组不会再出现（多次抽取验证排除语义）")
    void drawNeverReturnsAnsweredGroup() {
        String category = createCategory("TEST_ANS_C", "答题测试大类C");
        Long group = createGroup(category, "唯一组", 1);
        Long question = createQuestion(group, "唯一题", 1);

        listenAnswerService.submitAnswers(TEST_USER_ID,
                submitDTO(group, List.of(answerItem(question, "B", null))));

        // dev 库可能还有其他组，无法断言必为 null；只能断言已答组不再出现
        for (int i = 0; i < 20; i++) {
            ListenDrawVO vo = listenAnswerService.draw(TEST_USER_ID);
            if (vo != null) {
                assertThat(vo.getGroupId()).isNotEqualTo(group);
            }
        }
    }

    @Test
    @DisplayName("提交：整组答案正确落库，选A-D冗余存选项文本，选E存用户输入")
    void submitPersistsAnswers() {
        String category = createCategory("TEST_ANS_D", "答题测试大类D");
        Long group = createGroup(category, "提交组", 1);
        Long q1 = createQuestion(group, "题一", 1);
        Long q2 = createQuestion(group, "题二", 2);

        listenAnswerService.submitAnswers(TEST_USER_ID,
                submitDTO(group, List.of(
                        answerItem(q1, "C", null),
                        answerItem(q2, "E", "我想自己说点什么"))));

        List<ListenAnswerEntity> saved = listenAnswerDao.selectList(new QueryWrapper<ListenAnswerEntity>()
                .eq("user_id", TEST_USER_ID)
                .in("question_id", q1, q2)
                .orderByAsc("question_id"));
        assertThat(saved).hasSize(2);
        ListenAnswerEntity first = saved.get(0).getQuestionId().equals(q1) ? saved.get(0) : saved.get(1);
        ListenAnswerEntity second = saved.get(0).getQuestionId().equals(q2) ? saved.get(0) : saved.get(1);
        // 选 C：option_key=C，custom_text 冗余选项文本「丙」
        assertThat(first.getOptionKey()).isEqualTo("C");
        assertThat(first.getCustomText()).isEqualTo("丙");
        assertThat(first.getGroupId()).isEqualTo(group);
        // 选 E + 用户输入：option_key=E，custom_text=用户文本
        assertThat(second.getOptionKey()).isEqualTo("E");
        assertThat(second.getCustomText()).isEqualTo("我想自己说点什么");
    }

    @Test
    @DisplayName("提交：重复提交同一题幂等——不报错、不产生第二行")
    void submitIsIdempotent() {
        String category = createCategory("TEST_ANS_E", "答题测试大类E");
        Long group = createGroup(category, "幂等组", 1);
        Long question = createQuestion(group, "幂等题", 1);

        ListenAnswerSubmitDTO dto = submitDTO(group, List.of(answerItem(question, "A", null)));
        listenAnswerService.submitAnswers(TEST_USER_ID, dto);
        // 第二次提交同题不同选项：应忽略，不产生新行
        listenAnswerService.submitAnswers(TEST_USER_ID,
                submitDTO(group, List.of(answerItem(question, "D", "改写"))));

        List<ListenAnswerEntity> saved = listenAnswerDao.selectList(new QueryWrapper<ListenAnswerEntity>()
                .eq("user_id", TEST_USER_ID)
                .eq("question_id", question));
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getOptionKey()).isEqualTo("A");
        // 选 A：custom_text 冗余选项文本「甲」，第二次提交被幂等忽略
        assertThat(saved.get(0).getCustomText()).isEqualTo("甲");
    }

    @Test
    @DisplayName("提交：customText 超过 200 字抛 LISTEN_CUSTOM_TEXT_TOO_LONG")
    void submitRejectsTooLongCustomText() {
        String category = createCategory("TEST_ANS_F", "答题测试大类F");
        Long group = createGroup(category, "超长组", 1);
        Long question = createQuestion(group, "超长题", 1);

        String tooLong = "x".repeat(201);
        assertThatThrownBy(() -> listenAnswerService.submitAnswers(TEST_USER_ID,
                submitDTO(group, List.of(answerItem(question, "E", tooLong)))))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.LISTEN_CUSTOM_TEXT_TOO_LONG);
    }

    @Test
    @DisplayName("提交：questionId 不属于 groupId 抛 LISTEN_QUESTION_NOT_FOUND（防止污染按组排除索引）")
    void submitRejectsMismatchedGroupAndQuestion() {
        String category = createCategory("TEST_ANS_G", "答题测试大类G");
        Long groupA = createGroup(category, "组A", 1);
        Long groupB = createGroup(category, "组B", 2);
        Long questionInB = createQuestion(groupB, "组B的题", 1);

        // 组 A 的提交里夹带组 B 的题：错配，拒绝
        assertThatThrownBy(() -> listenAnswerService.submitAnswers(TEST_USER_ID,
                submitDTO(groupA, List.of(answerItem(questionInB, "A", null)))))
                .isInstanceOf(RenException.class)
                .extracting(e -> ((RenException) e).getCode())
                .isEqualTo(ErrorCode.LISTEN_QUESTION_NOT_FOUND);
    }
}
