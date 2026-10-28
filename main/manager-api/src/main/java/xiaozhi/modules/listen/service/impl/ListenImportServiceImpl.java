package xiaozhi.modules.listen.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.exception.ListenImportException;
import xiaozhi.modules.listen.service.ListenImportService;
import xiaozhi.modules.listen.vo.ListenImportConflictVO;
import xiaozhi.modules.listen.vo.ListenImportResultVO;
import xiaozhi.modules.sys.service.SysDictDataService;
import xiaozhi.modules.sys.vo.SysDictDataItem;

/**
 * 题库 JSON 增量导入实现。
 * 解析顺序：JSON → 结构校验 → 字典校验 → 判重（文件内自查 + 库内比对）→ 单事务写入。
 * 任何一步失败都整单拒绝：格式/字典问题抛 RenException，判重冲突抛带明细的 ListenImportException。
 */
@Service
@AllArgsConstructor
public class ListenImportServiceImpl implements ListenImportService {

    /** 每组固定题数（spec：一组固定 5 道单选题） */
    private static final int QUESTIONS_PER_GROUP = 5;
    /** 每题固定选项数（A-E） */
    private static final int OPTIONS_PER_QUESTION = 5;
    /** 冲突类型：组名在大类内重复 */
    private static final String CONFLICT_GROUP_NAME = "GROUP_NAME";
    /** 冲突类型：题干在组内重复 */
    private static final String CONFLICT_QUESTION_TEXT = "QUESTION_TEXT";

    /** 解析导入文件用的共享 ObjectMapper（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ListenGroupDao listenGroupDao;
    private final ListenQuestionDao listenQuestionDao;
    private final SysDictDataService sysDictDataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ListenImportResultVO importBank(MultipartFile file) {
        JsonNode categories = parseJson(file);
        Map<String, Set<String>> dictValues = validateCategories(categories);
        List<ListenImportConflictVO> conflicts = collectConflicts(categories, dictValues.keySet());
        if (!conflicts.isEmpty()) {
            // 判重整单失败制：先汇总全部冲突再抛出，事务回滚保证零写入
            throw new ListenImportException(ErrorCode.LISTEN_IMPORT_CONFLICT, conflicts);
        }
        return writeAll(categories);
    }

    /** 读取并解析 JSON；格式非法或不是大类数组时抛 10374 */
    private JsonNode parseJson(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT, "文件为空");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(
                    new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT, "JSON 解析失败");
        }
        if (root == null || !root.isArray()) {
            throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT, "顶层必须是大类数组");
        }
        return root;
    }

    /**
     * 校验每个大类：id 必填、存在于 LISTEN_CATEGORY 字典；同时做结构校验
     * （组名/题干必填、每组 5 题、每题 5 选项）。
     *
     * @return 大类 id -> 字典小类集合（key 为大类 id，value 仅用于存在性，内容不消费）
     */
    private Map<String, Set<String>> validateCategories(JsonNode categories) {
        // 字典 LISTEN_CATEGORY 小类 dict_value 集合，用于大类 id 存在性校验
        Set<String> validCategoryCodes = new HashSet<>();
        List<SysDictDataItem> dictItems = sysDictDataService
                .getDictDataByType(Constant.DictType.LISTEN_CATEGORY.getValue());
        if (dictItems != null) {
            for (SysDictDataItem item : dictItems) {
                if (item.getKey() != null) {
                    validCategoryCodes.add(item.getKey());
                }
            }
        }

        Map<String, Set<String>> seen = new HashMap<>();
        for (JsonNode category : categories) {
            String categoryId = textOrNull(category, "id");
            if (categoryId == null) {
                throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT, "大类缺少 id 字段");
            }
            if (!validCategoryCodes.contains(categoryId)) {
                throw new RenException(ErrorCode.LISTEN_CATEGORY_NOT_EXIST, categoryId);
            }
            validateGroups(categoryId, category);
            seen.put(categoryId, new HashSet<>());
        }
        return seen;
    }

    /** 结构校验：一个大类下每组 5 题、每题 5 选项、必填字段齐全 */
    private void validateGroups(String categoryId, JsonNode category) {
        JsonNode groups = category.path("groups");
        if (!groups.isArray()) {
            throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT,
                    "大类 " + categoryId + " 缺少 groups 数组");
        }
        for (JsonNode group : groups) {
            String groupName = textOrNull(group, "name");
            if (groupName == null) {
                throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT,
                        "大类 " + categoryId + " 下存在缺 name 的组");
            }
            JsonNode questions = group.path("questions");
            if (!questions.isArray() || questions.size() != QUESTIONS_PER_GROUP) {
                throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT,
                        "组「" + groupName + "」题目数必须为 " + QUESTIONS_PER_GROUP);
            }
            for (JsonNode question : questions) {
                validateQuestion(groupName, question);
            }
        }
    }

    /** 结构校验：单题题干必填、选项 5 个且 key/text 齐全 */
    private void validateQuestion(String groupName, JsonNode question) {
        String text = textOrNull(question, "text");
        if (text == null) {
            throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT,
                    "组「" + groupName + "」下存在缺 text 的题目");
        }
        JsonNode options = question.path("options");
        if (!options.isArray() || options.size() != OPTIONS_PER_QUESTION) {
            throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT,
                    "组「" + groupName + "」题目「" + text + "」选项数必须为 " + OPTIONS_PER_QUESTION);
        }
        for (JsonNode option : options) {
            if (textOrNull(option, "key") == null || textOrNull(option, "text") == null) {
                throw new RenException(ErrorCode.LISTEN_IMPORT_INVALID_FORMAT,
                        "组「" + groupName + "」题目「" + text + "」存在缺 key/text 的选项");
            }
        }
    }

    /**
     * 判重汇总（文件内自查 + 库内比对）：
     * <ul>
     * <li>同大类下组名重复：文件内两组同名、或与库内已有组同名</li>
     * <li>同组内题干重复：文件内同组两题同题干、或与库内同组（同大类同名组）内已有题干重复</li>
     * </ul>
     * 组名重复的组不再展开题干比对（组已整体被拒，题干归属不明）。
     */
    private List<ListenImportConflictVO> collectConflicts(JsonNode categories, Set<String> categoryIds) {
        // 一次性查出涉及大类的库内已有组与题，避免逐组查库（N+1）
        Map<String, Set<String>> dbGroupNames = new HashMap<>();
        Map<String, Map<String, Set<String>>> dbQuestionTexts = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<ListenGroupEntity> dbGroups = listenGroupDao.selectList(new QueryWrapper<ListenGroupEntity>()
                    .select("id", "category_code", "name").in("category_code", categoryIds));
            List<Long> dbGroupIds = new ArrayList<>();
            Map<Long, String> dbGroupKeyById = new HashMap<>();
            for (ListenGroupEntity group : dbGroups) {
                dbGroupNames.computeIfAbsent(group.getCategoryCode(), k -> new HashSet<>()).add(group.getName());
                dbGroupIds.add(group.getId());
                dbGroupKeyById.put(group.getId(), group.getCategoryCode() + " " + group.getName());
            }
            if (!dbGroupIds.isEmpty()) {
                List<ListenQuestionEntity> dbQuestions = listenQuestionDao.selectList(
                        new QueryWrapper<ListenQuestionEntity>().select("group_id", "text")
                                .in("group_id", dbGroupIds));
                for (ListenQuestionEntity question : dbQuestions) {
                    String key = dbGroupKeyById.get(question.getGroupId());
                    if (key == null) {
                        continue;
                    }
                    int sep = key.indexOf(' ');
                    dbQuestionTexts.computeIfAbsent(key.substring(0, sep), k -> new HashMap<>())
                            .computeIfAbsent(key.substring(sep + 1), k -> new HashSet<>())
                            .add(question.getText());
                }
            }
        }

        List<ListenImportConflictVO> conflicts = new ArrayList<>();
        for (JsonNode category : categories) {
            String categoryId = category.path("id").asText();
            Set<String> fileGroupNames = new HashSet<>();
            for (JsonNode group : category.path("groups")) {
                String groupName = group.path("name").asText();
                boolean groupConflict = false;
                if (!fileGroupNames.add(groupName)) {
                    conflicts.add(conflict(CONFLICT_GROUP_NAME, categoryId, groupName, null, "文件内同大类组名重复"));
                    groupConflict = true;
                }
                if (dbGroupNames.getOrDefault(categoryId, Set.of()).contains(groupName)) {
                    conflicts.add(conflict(CONFLICT_GROUP_NAME, categoryId, groupName, null, "与库内已有组名重复"));
                    groupConflict = true;
                }
                if (groupConflict) {
                    continue;
                }
                collectQuestionConflicts(conflicts, categoryId, groupName, group.path("questions"),
                        dbQuestionTexts.getOrDefault(categoryId, Map.of()).getOrDefault(groupName, Set.of()));
            }
        }
        return conflicts;
    }

    /** 组内题干判重：文件内同组自查 + 与库内同组已有题干比对 */
    private void collectQuestionConflicts(List<ListenImportConflictVO> conflicts, String categoryId,
            String groupName, JsonNode questions, Set<String> dbTexts) {
        Set<String> fileTexts = new HashSet<>();
        for (JsonNode question : questions) {
            String text = question.path("text").asText();
            if (!fileTexts.add(text)) {
                conflicts.add(conflict(CONFLICT_QUESTION_TEXT, categoryId, groupName, text, "文件内同组题干重复"));
                continue;
            }
            if (dbTexts.contains(text)) {
                conflicts.add(conflict(CONFLICT_QUESTION_TEXT, categoryId, groupName, text, "与库内同组已有题干重复"));
            }
        }
    }

    private ListenImportConflictVO conflict(String type, String categoryId, String groupName, String questionText,
            String reason) {
        ListenImportConflictVO vo = new ListenImportConflictVO();
        vo.setType(type);
        vo.setCategoryCode(categoryId);
        vo.setGroupName(groupName);
        vo.setQuestionText(questionText);
        vo.setReason(reason);
        return vo;
    }

    /**
     * 写入：按大类聚合，组 sortOrder 接该大类现有 max+1 起递增（文件内相对顺序由 JSON order 决定）；
     * 新组的题 sortOrder 从 1 起按 JSON order 递增；题干 type 原样存，选项 JSON 原样入库。
     */
    private ListenImportResultVO writeAll(JsonNode categories) {
        int groupCount = 0;
        int questionCount = 0;
        for (JsonNode category : categories) {
            String categoryId = category.path("id").asText();
            // 追加语义：新组接在该大类现有最大 sortOrder 之后
            int nextSortOrder = maxGroupSortOrder(categoryId) + 1;
            List<JsonNode> groups = sortedByOrder(category.path("groups"));
            for (JsonNode group : groups) {
                ListenGroupEntity groupEntity = new ListenGroupEntity();
                groupEntity.setCategoryCode(categoryId);
                groupEntity.setName(group.path("name").asText());
                groupEntity.setSortOrder(nextSortOrder++);
                listenGroupDao.insert(groupEntity);
                groupCount++;

                int questionSortOrder = 1;
                for (JsonNode question : sortedByOrder(group.path("questions"))) {
                    ListenQuestionEntity questionEntity = new ListenQuestionEntity();
                    questionEntity.setGroupId(groupEntity.getId());
                    questionEntity.setText(question.path("text").asText());
                    // type 原样存（当前仅 SINGLE，扩展类型待游戏立项后评估）
                    questionEntity.setType(question.path("type").asText("SINGLE"));
                    questionEntity.setOptionsJson(question.path("options").toString());
                    questionEntity.setSortOrder(questionSortOrder++);
                    listenQuestionDao.insert(questionEntity);
                    questionCount++;
                }
            }
        }
        ListenImportResultVO result = new ListenImportResultVO();
        result.setGroupCount(groupCount);
        result.setQuestionCount(questionCount);
        return result;
    }

    /** 该大类现有组的最大 sortOrder（无组时为 0） */
    private int maxGroupSortOrder(String categoryId) {
        List<Map<String, Object>> rows = listenGroupDao.selectMaps(new QueryWrapper<ListenGroupEntity>()
                .select("COALESCE(MAX(sort_order), 0) AS maxSort")
                .eq("category_code", categoryId));
        if (rows.isEmpty()) {
            return 0;
        }
        return ((Number) rows.get(0).get("maxSort")).intValue();
    }

    /** 按 JSON 内 order 字段升序稳定排序；order 缺失时落到末尾（保持文件内出现顺序） */
    private List<JsonNode> sortedByOrder(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        array.forEach(list::add);
        list.sort((a, b) -> {
            int orderA = a.path("order").isInt() ? a.path("order").asInt() : Integer.MAX_VALUE;
            int orderB = b.path("order").isInt() ? b.path("order").asInt() : Integer.MAX_VALUE;
            return Integer.compare(orderA, orderB);
        });
        return list;
    }

    /** 取文本字段，空白按缺失处理 */
    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || !value.isTextual()) {
            return null;
        }
        return StringUtils.trimToNull(value.asText());
    }
}
