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
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenGroupSaveDTO;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenGroupService;
import xiaozhi.modules.sys.dao.SysDictDataDao;
import xiaozhi.modules.sys.dao.SysDictTypeDao;
import xiaozhi.modules.sys.entity.SysDictDataEntity;
import xiaozhi.modules.sys.entity.SysDictTypeEntity;

/**
 * 题组编辑与删除 Service 集成测试（真实 dev 库）。
 * 覆盖：整组保存 diff（更新保 id / 无 id 新增 / 缺失物理删除）、
 * 保存判重（组名大类内唯一排除自身、题干组内唯一排除自身）、
 * 整组物理删除级联删题、单题物理删除。
 * 每个用例自造 LISTEN_CATEGORY 字典小类与题库数据，AfterEach 物理清理，避免污染 dev 库与后续用例。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("题库题组编辑与删除集成测试")
class ListenGroupEditDeleteServiceTest {

    @Autowired
    private ListenGroupService listenGroupService;
    @Autowired
    private ListenGroupDao listenGroupDao;
    @Autowired
    private ListenQuestionDao listenQuestionDao;
    @Autowired
    private SysDictDataDao sysDictDataDao;
    @Autowired
    private SysDictTypeDao sysDictTypeDao;
    @Autowired
    private RedisUtils redisUtils;

    /** 本用例创建的分组 id */
    private final List<Long> groupIds = new ArrayList<>();
    /** 本用例创建的字典小类 id */
    private final List<Long> dictDataIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 组内题目随组一起清理（删除用例可能已级联删题，按 groupId 清兜底）
        groupIds.forEach(groupId -> {
            listenQuestionDao.delete(new QueryWrapper<ListenQuestionEntity>().eq("group_id", groupId));
            listenGroupDao.deleteById(groupId);
        });
        groupIds.clear();
        dictDataIds.forEach(sysDictDataDao::deleteById);
        dictDataIds.clear();
        // 字典列表有 Redis 缓存，用例间必须清掉，否则后续用例读到旧的小类快照
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
        entity.setOptionsJson("[{\"key\":\"A\",\"text\":\"甲\"}]");
        entity.setSortOrder(sortOrder);
        listenQuestionDao.insert(entity);
        return entity.getId();
    }

    /** 组内现存题目（按 sort_order 升序） */
    private List<ListenQuestionEntity> questionsOf(Long groupId) {
        return listenQuestionDao.selectList(
                new QueryWrapper<ListenQuestionEntity>().eq("group_id", groupId).orderByAsc("sort_order"));
    }

    /** 构造一道保存用题目（id 为 null 表示新增） */
    private ListenGroupSaveDTO.Question questionDto(Long id, String text, int sortOrder) {
        ListenGroupSaveDTO.Question question = new ListenGroupSaveDTO.Question();
        question.setId(id);
        question.setText(text);
        question.setType("SINGLE");
        question.setSortOrder(sortOrder);
        List<ListenGroupSaveDTO.Option> options = new ArrayList<>();
        String[] keys = { "A", "B", "C", "D", "E" };
        for (String key : keys) {
            ListenGroupSaveDTO.Option option = new ListenGroupSaveDTO.Option();
            option.setKey(key);
            option.setText(text + "-" + key);
            options.add(option);
        }
        question.setOptions(options);
        return question;
    }

    /** 构造整组保存 DTO */
    private ListenGroupSaveDTO saveDto(String name, ListenGroupSaveDTO.Question... questions) {
        ListenGroupSaveDTO dto = new ListenGroupSaveDTO();
        dto.setName(name);
        dto.setQuestions(List.of(questions));
        return dto;
    }

    @Test
    @DisplayName("整组保存 diff：带 id 更新且 id 稳定、无 id 新增、库内缺失的物理删除")
    void updateGroupDiffsUpdateInsertAndDelete() {
        String category = createCategory("TEST_EDIT_DIFF", "编辑diff大类");
        Long groupId = createGroup(category, "旧组名", 1);
        Long keepId = createQuestion(groupId, "保留题-旧题干", 1);
        Long removeId = createQuestion(groupId, "待删题", 2);

        ListenGroupSaveDTO dto = saveDto("新组名",
                questionDto(keepId, "保留题-新题干", 1),
                questionDto(null, "新增题", 2));
        listenGroupService.updateGroup(groupId, dto);

        ListenGroupEntity group = listenGroupDao.selectById(groupId);
        assertThat(group.getName()).isEqualTo("新组名");

        List<ListenQuestionEntity> questions = questionsOf(groupId);
        assertThat(questions).hasSize(2);
        // 更新路径：id 保持稳定，题干已更新，options_json 已重写为 5 个选项的裸数组（与导入路径格式一致）
        assertThat(questions.get(0).getId()).isEqualTo(keepId);
        assertThat(questions.get(0).getText()).isEqualTo("保留题-新题干");
        assertThat(questions.get(0).getOptionsJson()).startsWith("[").contains("\"E\"").doesNotContain("\"options\"");
        // 新增路径：生成了新 id
        assertThat(questions.get(1).getId()).isNotNull().isNotEqualTo(keepId);
        assertThat(questions.get(1).getText()).isEqualTo("新增题");
        // 删除路径：库内有但 body 缺失的题被物理删除
        assertThat(listenQuestionDao.selectById(removeId)).isNull();
    }

    @Test
    @DisplayName("保存时组名与同大类其他组重复则抛 10380，自身原名不视为重复")
    void updateGroupRejectsDuplicateGroupNameInCategory() {
        String category = createCategory("TEST_EDIT_DUPNAME", "组名判重大类");
        Long groupId = createGroup(category, "甲组", 1);
        createGroup(category, "乙组", 2);

        // 与其他组重名 → 拒绝
        assertThatThrownBy(() -> listenGroupService.updateGroup(groupId,
                saveDto("乙组", questionDto(null, "题", 1))))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_GROUP_NAME_DUPLICATE));

        // 保留自身原名 → 允许（排除自身）
        listenGroupService.updateGroup(groupId, saveDto("甲组", questionDto(null, "题", 1)));
        assertThat(listenGroupDao.selectById(groupId).getName()).isEqualTo("甲组");
    }

    @Test
    @DisplayName("保存时题干与同组其他题重复则抛 10381，题自身题干不视为重复")
    void updateGroupRejectsDuplicateQuestionTextInGroup() {
        String category = createCategory("TEST_EDIT_DUPTEXT", "题干判重大类");
        Long groupId = createGroup(category, "判重组", 1);
        Long questionId = createQuestion(groupId, "已有题干", 1);

        // body 内两道题干重复 → 拒绝
        assertThatThrownBy(() -> listenGroupService.updateGroup(groupId,
                saveDto("判重组", questionDto(null, "重复题干", 1), questionDto(null, "重复题干", 2))))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_QUESTION_TEXT_DUPLICATE));

        // 带 id 的题沿用自身题干 → 允许（排除自身）
        listenGroupService.updateGroup(groupId,
                saveDto("判重组", questionDto(questionId, "已有题干", 1), questionDto(null, "另一题干", 2)));
        assertThat(questionsOf(groupId)).hasSize(2);
    }

    @Test
    @DisplayName("保存不存在的题组抛 10370 组不存在")
    void updateGroupThrowsWhenGroupNotFound() {
        assertThatThrownBy(() -> listenGroupService.updateGroup(-999L,
                saveDto("幽灵组", questionDto(null, "题", 1))))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_GROUP_NOT_FOUND));
    }

    @Test
    @DisplayName("整组物理删除：组内题目级联删除")
    void deleteGroupCascadesQuestions() {
        String category = createCategory("TEST_EDIT_DELGROUP", "删组大类");
        Long groupId = createGroup(category, "待删组", 1);
        createQuestion(groupId, "题一", 1);
        createQuestion(groupId, "题二", 2);

        listenGroupService.deleteGroup(groupId);

        assertThat(listenGroupDao.selectById(groupId)).isNull();
        assertThat(questionsOf(groupId)).isEmpty();
    }

    @Test
    @DisplayName("删除不存在的题组抛 10370 组不存在")
    void deleteGroupThrowsWhenGroupNotFound() {
        assertThatThrownBy(() -> listenGroupService.deleteGroup(-999L))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_GROUP_NOT_FOUND));
    }

    @Test
    @DisplayName("单题物理删除：组与其余题目不受影响")
    void deleteQuestionRemovesOnlyThatQuestion() {
        String category = createCategory("TEST_EDIT_DELQ", "删题大类");
        Long groupId = createGroup(category, "删题组", 1);
        Long keepId = createQuestion(groupId, "保留题", 1);
        Long removeId = createQuestion(groupId, "删除题", 2);

        listenGroupService.deleteQuestion(removeId);

        assertThat(listenQuestionDao.selectById(removeId)).isNull();
        assertThat(listenQuestionDao.selectById(keepId)).isNotNull();
        assertThat(listenGroupDao.selectById(groupId)).isNotNull();
    }

    @Test
    @DisplayName("删除不存在的题目抛 10371 题不存在")
    void deleteQuestionThrowsWhenQuestionNotFound() {
        assertThatThrownBy(() -> listenGroupService.deleteQuestion(-999L))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_QUESTION_NOT_FOUND));
    }
}
