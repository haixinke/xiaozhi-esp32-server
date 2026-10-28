package xiaozhi.modules.listen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import xiaozhi.common.page.PageData;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenGroupService;
import xiaozhi.modules.listen.vo.ListenGroupDetailVO;
import xiaozhi.modules.listen.vo.ListenGroupVO;
import xiaozhi.modules.sys.dao.SysDictDataDao;
import xiaozhi.modules.sys.dao.SysDictTypeDao;
import xiaozhi.modules.sys.entity.SysDictDataEntity;
import xiaozhi.modules.sys.entity.SysDictTypeEntity;

/**
 * 题组浏览 Service 集成测试（真实 dev 库）。
 * 每个用例自造 LISTEN_CATEGORY 字典小类与题库数据，AfterEach 物理清理，避免污染 dev 库与后续用例。
 */
@Import(TestArkServiceConfig.class)
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("题库题组浏览集成测试")
class ListenGroupServiceTest {

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
    /** 本用例创建的题目 id */
    private final List<Long> questionIds = new ArrayList<>();
    /** 本用例创建的字典小类 id */
    private final List<Long> dictDataIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        questionIds.forEach(listenQuestionDao::deleteById);
        questionIds.clear();
        groupIds.forEach(listenGroupDao::deleteById);
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
        // options_json 统一为裸数组 [{key,text}]（spec/DDL 注释格式；DB 无存量包装形态数据）
        entity.setOptionsJson(
                "[{\"key\":\"A\",\"text\":\"甲\"},{\"key\":\"B\",\"text\":\"乙\"},"
                        + "{\"key\":\"C\",\"text\":\"丙\"},{\"key\":\"D\",\"text\":\"丁\"},{\"key\":\"E\",\"text\":\"戊\"}]");
        entity.setSortOrder(sortOrder);
        listenQuestionDao.insert(entity);
        questionIds.add(entity.getId());
        return entity.getId();
    }

    private Map<String, Object> pageParams(String categoryCode) {
        Map<String, Object> params = new HashMap<>();
        params.put(Constant.PAGE, "1");
        params.put(Constant.LIMIT, "10");
        params.put("categoryCode", categoryCode);
        return params;
    }

    @Test
    @DisplayName("分页按大类过滤并返回组名、排序与题数，大类名由字典实时翻译")
    void pageFiltersByCategoryAndFillsQuestionCount() {
        String category = createCategory("TEST_CAT_A", "测试大类A");
        String other = createCategory("TEST_CAT_B", "测试大类B");
        Long group1 = createGroup(category, "组一", 1);
        createGroup(category, "组二", 2);
        createGroup(other, "别类组", 1);
        createQuestion(group1, "题目一", 1);
        createQuestion(group1, "题目二", 2);

        PageData<ListenGroupVO> page = listenGroupService.page(pageParams(category));

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getList()).extracting(ListenGroupVO::getName)
                .containsExactly("组一", "组二");
        assertThat(page.getList().get(0).getQuestionCount()).isEqualTo(2);
        assertThat(page.getList().get(0).getCategoryName()).isEqualTo("测试大类A");
        assertThat(page.getList().get(0).getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("字典查不到大类时 categoryName 回落为原始 code")
    void pageFallsBackToRawCodeWhenDictMissing() {
        Long group = createGroup("TEST_UNKNOWN_CAT", "未配大类组", 1);
        createQuestion(group, "题目", 1);

        PageData<ListenGroupVO> page = listenGroupService.page(pageParams("TEST_UNKNOWN_CAT"));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList().get(0).getCategoryName()).isEqualTo("TEST_UNKNOWN_CAT");
    }

    @Test
    @DisplayName("不传大类时返回全部题组")
    void pageWithoutCategoryReturnsAll() {
        String category = createCategory("TEST_CAT_ALL", "测试大类ALL");
        createGroup(category, "组X", 1);

        Map<String, Object> params = new HashMap<>();
        params.put(Constant.PAGE, "1");
        params.put(Constant.LIMIT, "200");
        PageData<ListenGroupVO> page = listenGroupService.page(params);

        assertThat(page.getList()).extracting(ListenGroupVO::getName).contains("组X");
    }

    @Test
    @DisplayName("组详情含组内题目，题干按排序升序，选项从 options_json 解析为 A-E 列表")
    void detailReturnsQuestionsWithParsedOptions() {
        String category = createCategory("TEST_CAT_D", "测试大类D");
        Long group = createGroup(category, "详情组", 1);
        createQuestion(group, "第二题", 2);
        createQuestion(group, "第一题", 1);

        ListenGroupDetailVO detail = listenGroupService.getDetail(group);

        assertThat(detail.getName()).isEqualTo("详情组");
        assertThat(detail.getCategoryCode()).isEqualTo(category);
        assertThat(detail.getQuestions()).hasSize(2);
        assertThat(detail.getQuestions()).extracting(ListenGroupDetailVO.Question::getText)
                .containsExactly("第一题", "第二题");
        List<ListenGroupDetailVO.Option> options = detail.getQuestions().get(0).getOptions();
        assertThat(options).hasSize(5);
        assertThat(options).extracting(ListenGroupDetailVO.Option::getKey)
                .containsExactly("A", "B", "C", "D", "E");
        assertThat(options.get(0).getText()).isEqualTo("甲");
    }

    @Test
    @DisplayName("组不存在时详情抛 10370 组不存在")
    void detailThrowsWhenGroupNotFound() {
        assertThatThrownBy(() -> listenGroupService.getDetail(-999L))
                .isInstanceOf(RenException.class)
                .satisfies(e -> assertThat(((RenException) e).getCode())
                        .isEqualTo(ErrorCode.LISTEN_GROUP_NOT_FOUND));
    }

    @Test
    @DisplayName("空组的详情返回空题目列表而不是报错")
    void detailOfEmptyGroupReturnsEmptyQuestions() {
        String category = createCategory("TEST_CAT_E", "测试大类E");
        Long group = createGroup(category, "空组", 1);

        ListenGroupDetailVO detail = listenGroupService.getDetail(group);

        assertThat(detail.getQuestions()).isEmpty();
    }
}
