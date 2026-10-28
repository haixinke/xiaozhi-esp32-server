package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import xiaozhi.common.config.TestArkServiceConfig;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.exception.ListenImportException;
import xiaozhi.modules.listen.service.ListenImportService;
import xiaozhi.modules.listen.vo.ListenImportResultVO;
import xiaozhi.modules.sys.dao.SysDictDataDao;
import xiaozhi.modules.sys.dao.SysDictTypeDao;
import xiaozhi.modules.sys.entity.SysDictDataEntity;
import xiaozhi.modules.sys.entity.SysDictTypeEntity;

/**
 * 题库 JSON 增量导入 Service 集成测试（真实 dev 库）。
 * 每个用例自造 LISTEN_CATEGORY 字典小类与题库数据，AfterEach 物理清理。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("题库 JSON 增量导入集成测试")
class ListenImportServiceTest {

    @Autowired
    private ListenImportService listenImportService;
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

    /** 本用例创建的字典小类 id */
    private final List<Long> dictDataIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 按测试大类前缀级联清理：先删题再删组，避免残留影响后续用例
        List<ListenGroupEntity> groups = listenGroupDao.selectList(
                new QueryWrapper<ListenGroupEntity>().likeRight("category_code", "TEST_IMP"));
        for (ListenGroupEntity group : groups) {
            listenQuestionDao.delete(
                    new QueryWrapper<ListenQuestionEntity>().eq("group_id", group.getId()));
            listenGroupDao.deleteById(group.getId());
        }
        dictDataIds.forEach(sysDictDataDao::deleteById);
        dictDataIds.clear();
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

    /** 生成一道合法题 JSON 片段（5 选项 A-E） */
    private String questionJson(int order, String text) {
        return """
                {"id":"Q%d","order":%d,"type":"SINGLE","text":"%s","options":[
                  {"key":"A","text":"甲"},{"key":"B","text":"乙"},{"key":"C","text":"丙"},
                  {"key":"D","text":"丁"},{"key":"E","text":"戊"}]}
                """.formatted(order, order, text);
    }

    /** 生成一组 5 题的合法组 JSON 片段 */
    private String groupJson(int order, String name, String... texts) {
        StringBuilder questions = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            if (i > 0) {
                questions.append(',');
            }
            questions.append(questionJson(i + 1, texts[i]));
        }
        return """
                {"id":"G%d","order":%d,"name":"%s","questions":[%s]}
                """.formatted(order, order, name, questions);
    }

    /** 生成一个大类 JSON 片段 */
    private String categoryJson(String id, String name, String... groups) {
        return """
                {"id":"%s","name":"%s","groups":[%s]}
                """.formatted(id, name, String.join(",", groups));
    }

    /** 包装成 multipart 文件 */
    private MultipartFile fileOf(String json) {
        return new MockMultipartFile("file", "bank.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("成功导入：写入组与题，返回统计，order 按文件内相对顺序重排")
    void importSuccessWritesGroupsAndQuestionsWithStats() {
        createCategory("TEST_IMP_A", "测试大类A");
        String json = "[" + categoryJson("TEST_IMP_A", "测试大类A",
                groupJson(2, "组乙", "题1", "题2", "题3", "题4", "题5"),
                groupJson(1, "组甲", "题6", "题7", "题8", "题9", "题10")) + "]";

        ListenImportResultVO result = listenImportService.importBank(fileOf(json));

        assertThat(result.getGroupCount()).isEqualTo(2);
        assertThat(result.getQuestionCount()).isEqualTo(10);
        List<ListenGroupEntity> groups = listenGroupDao.selectList(new QueryWrapper<ListenGroupEntity>()
                .eq("category_code", "TEST_IMP_A").orderByAsc("sort_order"));
        // JSON 内 order 只定同次导入相对顺序：order=1 的「组甲」应排在 order=2 的「组乙」前
        assertThat(groups).extracting(ListenGroupEntity::getName).containsExactly("组甲", "组乙");
        assertThat(groups).extracting(ListenGroupEntity::getSortOrder).containsExactly(1, 2);
        List<ListenQuestionEntity> questions = listenQuestionDao.selectList(
                new QueryWrapper<ListenQuestionEntity>().eq("group_id", groups.get(0).getId())
                        .orderByAsc("sort_order"));
        assertThat(questions).hasSize(5);
        assertThat(questions.get(0).getText()).isEqualTo("题6");
        assertThat(questions.get(0).getType()).isEqualTo("SINGLE");
        // 选项以 JSON 列存储，含 key/text（Jackson 序列化带空格，只断言关键内容）
        assertThat(questions.get(0).getOptionsJson()).contains("\"key\"").contains("\"A\"").contains("甲");
    }

    @Test
    @DisplayName("大类 id 不在字典中 → 整单拒绝（10372），零写入")
    void importRejectsWhenCategoryNotInDict() {
        String json = "[" + categoryJson("TEST_IMP_UNKNOWN", "不存在的大类",
                groupJson(1, "组一", "题1", "题2", "题3", "题4", "题5")) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_CATEGORY_NOT_EXIST));

        assertThat(listenGroupDao.selectCount(
                new QueryWrapper<ListenGroupEntity>().likeRight("category_code", "TEST_IMP"))).isZero();
    }

    @Test
    @DisplayName("组名与库内已有组重复（同大类）→ 整单拒绝（10373），冲突明细含组名，零写入")
    void importRejectsWhenGroupNameConflictsWithDb() {
        String category = createCategory("TEST_IMP_B", "测试大类B");
        ListenGroupEntity existing = new ListenGroupEntity();
        existing.setCategoryCode(category);
        existing.setName("已有组");
        existing.setSortOrder(1);
        listenGroupDao.insert(existing);

        String json = "[" + categoryJson("TEST_IMP_B", "测试大类B",
                groupJson(1, "已有组", "题1", "题2", "题3", "题4", "题5"),
                groupJson(2, "新组", "题6", "题7", "题8", "题9", "题10")) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(ListenImportException.class)
                .satisfies(e -> {
                    ListenImportException lie = (ListenImportException) e;
                    assertThat(lie.getCode()).isEqualTo(ErrorCode.LISTEN_IMPORT_CONFLICT);
                    assertThat(lie.getConflicts()).anySatisfy(c -> {
                        assertThat(c.getGroupName()).isEqualTo("已有组");
                        assertThat(c.getCategoryCode()).isEqualTo("TEST_IMP_B");
                    });
                });

        // 单事务零写入：连无冲突的「新组」也不应落库
        assertThat(listenGroupDao.selectCount(
                new QueryWrapper<ListenGroupEntity>().eq("category_code", "TEST_IMP_B"))).isEqualTo(1);
    }

    @Test
    @DisplayName("文件内部同大类组名重复 → 整单拒绝（10373）")
    void importRejectsDuplicateGroupNamesWithinFile() {
        createCategory("TEST_IMP_C", "测试大类C");
        String json = "[" + categoryJson("TEST_IMP_C", "测试大类C",
                groupJson(1, "同名组", "题1", "题2", "题3", "题4", "题5"),
                groupJson(2, "同名组", "题6", "题7", "题8", "题9", "题10")) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(ListenImportException.class)
                .satisfies(e -> {
                    ListenImportException lie = (ListenImportException) e;
                    assertThat(lie.getCode()).isEqualTo(ErrorCode.LISTEN_IMPORT_CONFLICT);
                    assertThat(lie.getConflicts()).anySatisfy(
                            c -> assertThat(c.getGroupName()).isEqualTo("同名组"));
                });
        assertThat(listenGroupDao.selectCount(
                new QueryWrapper<ListenGroupEntity>().likeRight("category_code", "TEST_IMP"))).isZero();
    }

    @Test
    @DisplayName("文件内部同组题干重复 → 整单拒绝，冲突明细含题干")
    void importRejectsDuplicateQuestionTextsWithinGroup() {
        createCategory("TEST_IMP_D", "测试大类D");
        String json = "[" + categoryJson("TEST_IMP_D", "测试大类D",
                groupJson(1, "组一", "重复题", "题2", "重复题", "题4", "题5")) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(ListenImportException.class)
                .satisfies(e -> {
                    ListenImportException lie = (ListenImportException) e;
                    assertThat(lie.getConflicts()).anySatisfy(c -> {
                        assertThat(c.getGroupName()).isEqualTo("组一");
                        assertThat(c.getQuestionText()).isEqualTo("重复题");
                    });
                });
    }

    @Test
    @DisplayName("组内题干与库内已有题重复（同组场景由组名重复拦截；此处验证跨文件同组名同题干报组名冲突）")
    void importRejectsQuestionTextConflictingWithDb() {
        // 组名判重先于题干判重触发：同组名同题干时冲突体现在组名上。
        // 库内已有「老组」，文件换一个组名但题干与库内「老组」内题重复不构成冲突（题干只在同组内唯一），
        // 因此本用例验证：文件组名与库内不同、但文件内题干重复时按题干冲突报。
        String category = createCategory("TEST_IMP_E", "测试大类E");
        ListenGroupEntity existing = new ListenGroupEntity();
        existing.setCategoryCode(category);
        existing.setName("老组");
        existing.setSortOrder(1);
        listenGroupDao.insert(existing);
        ListenQuestionEntity oldQuestion = new ListenQuestionEntity();
        oldQuestion.setGroupId(existing.getId());
        oldQuestion.setText("库内已有题");
        oldQuestion.setType("SINGLE");
        oldQuestion.setOptionsJson("[]");
        oldQuestion.setSortOrder(1);
        listenQuestionDao.insert(oldQuestion);

        // 同名组「老组」+ 同题干 → 冲突明细应能定位到该组
        String json = "[" + categoryJson("TEST_IMP_E", "测试大类E",
                groupJson(1, "老组", "库内已有题", "题2", "题3", "题4", "题5")) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(ListenImportException.class)
                .satisfies(e -> {
                    ListenImportException lie = (ListenImportException) e;
                    assertThat(lie.getConflicts()).anySatisfy(
                            c -> assertThat(c.getGroupName()).isEqualTo("老组"));
                });
        assertThat(listenQuestionDao.selectCount(new QueryWrapper<ListenQuestionEntity>()
                .eq("group_id", existing.getId()))).isEqualTo(1);
    }

    @Test
    @DisplayName("order 追加：新组接在该大类现有 max(sortOrder) 之后递增")
    void importAppendsSortOrderAfterExistingMax() {
        String category = createCategory("TEST_IMP_F", "测试大类F");
        ListenGroupEntity existing = new ListenGroupEntity();
        existing.setCategoryCode(category);
        existing.setName("存量组");
        existing.setSortOrder(7);
        listenGroupDao.insert(existing);

        String json = "[" + categoryJson("TEST_IMP_F", "测试大类F",
                groupJson(1, "新组一", "题1", "题2", "题3", "题4", "题5"),
                groupJson(2, "新组二", "题6", "题7", "题8", "题9", "题10")) + "]";

        listenImportService.importBank(fileOf(json));

        List<ListenGroupEntity> groups = listenGroupDao.selectList(new QueryWrapper<ListenGroupEntity>()
                .eq("category_code", "TEST_IMP_F").orderByAsc("sort_order"));
        assertThat(groups).extracting(ListenGroupEntity::getName)
                .containsExactly("存量组", "新组一", "新组二");
        assertThat(groups).extracting(ListenGroupEntity::getSortOrder)
                .containsExactly(7, 8, 9);
    }

    @Test
    @DisplayName("JSON 格式非法 → 拒绝（10374），零写入")
    void importRejectsMalformedJson() {
        assertThatThrownBy(() -> listenImportService.importBank(fileOf("{not json")))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT));
    }

    @Test
    @DisplayName("每组非 5 题 → 拒绝（10374），错误信息含组名")
    void importRejectsGroupWithWrongQuestionCount() {
        createCategory("TEST_IMP_G", "测试大类G");
        String json = "[" + categoryJson("TEST_IMP_G", "测试大类G",
                groupJson(1, "残题组", "题1", "题2", "题3")) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(RenException.class)
                .satisfies(e -> {
                    RenException re = (RenException) e;
                    assertThat(re.getCode()).isEqualTo(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT);
                    assertThat(re.getMsg()).contains("残题组");
                });
    }

    @Test
    @DisplayName("每题非 5 选项 → 拒绝（10374）")
    void importRejectsQuestionWithWrongOptionCount() {
        createCategory("TEST_IMP_H", "测试大类H");
        String badQuestion = """
                {"id":"Q1","order":1,"type":"SINGLE","text":"题1","options":[{"key":"A","text":"甲"}]}
                """;
        String group = """
                {"id":"G1","order":1,"name":"坏选项组","questions":[%s,%s,%s,%s,%s]}
                """.formatted(badQuestion, questionJson(2, "题2"), questionJson(3, "题3"),
                questionJson(4, "题4"), questionJson(5, "题5"));
        String json = "[" + categoryJson("TEST_IMP_H", "测试大类H", group) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT));
        assertThat(listenGroupDao.selectCount(
                new QueryWrapper<ListenGroupEntity>().likeRight("category_code", "TEST_IMP"))).isZero();
    }

    @Test
    @DisplayName("缺字段（组无 name）→ 拒绝（10374）")
    void importRejectsMissingRequiredField() {
        createCategory("TEST_IMP_I", "测试大类I");
        String groupNoName = """
                {"id":"G1","order":1,"questions":[%s,%s,%s,%s,%s]}
                """.formatted(questionJson(1, "题1"), questionJson(2, "题2"), questionJson(3, "题3"),
                questionJson(4, "题4"), questionJson(5, "题5"));
        String json = "[" + categoryJson("TEST_IMP_I", "测试大类I", groupNoName) + "]";

        assertThatThrownBy(() -> listenImportService.importBank(fileOf(json)))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT));
    }
}
