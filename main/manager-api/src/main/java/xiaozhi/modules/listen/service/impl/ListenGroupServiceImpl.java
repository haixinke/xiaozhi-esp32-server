package xiaozhi.modules.listen.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenGroupSaveDTO;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenGroupService;
import xiaozhi.modules.listen.vo.ListenGroupDetailVO;
import xiaozhi.modules.listen.vo.ListenGroupVO;
import xiaozhi.modules.sys.service.SysDictDataService;
import xiaozhi.modules.sys.vo.SysDictDataItem;

/**
 * 题目分组 Service 实现。
 * 题库为物理删除（无 deleted 列），读取点无需过滤删除标记；
 * 大类名由字典 LISTEN_CATEGORY 的 dict_label 实时翻译，翻译不到时回落原始 dict_value。
 */
@Service
@AllArgsConstructor
public class ListenGroupServiceImpl extends BaseServiceImpl<ListenGroupDao, ListenGroupEntity>
        implements ListenGroupService {

    private final ListenQuestionDao listenQuestionDao;
    private final SysDictDataService sysDictDataService;

    /** 解析 options_json 用的共享 ObjectMapper（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public PageData<ListenGroupVO> page(Map<String, Object> params) {
        // 列表顺序由 sort_order 决定，剔除调用方可能传入的 orderField/order，避免客户端改写排序
        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.remove(Constant.ORDER_FIELD);
        pageParams.remove(Constant.ORDER);
        QueryWrapper<ListenGroupEntity> wrapper = new QueryWrapper<>();
        String categoryCode = StringUtils.trimToNull((String) pageParams.get("categoryCode"));
        wrapper.eq(categoryCode != null, "category_code", categoryCode);
        IPage<ListenGroupEntity> page = baseDao.selectPage(getPage(pageParams, "sort_order", true), wrapper);

        List<ListenGroupVO> voList = ConvertUtils.sourceToTarget(page.getRecords(), ListenGroupVO.class);
        fillQuestionCount(voList);
        fillCategoryName(voList);
        return new PageData<>(voList, page.getTotal());
    }

    @Override
    public ListenGroupDetailVO getDetail(Long id) {
        ListenGroupEntity entity = baseDao.selectById(id);
        if (entity == null) {
            throw new RenException(ErrorCode.LISTEN_GROUP_NOT_FOUND);
        }
        ListenGroupDetailVO vo = ConvertUtils.sourceToTarget(entity, ListenGroupDetailVO.class);
        vo.setCategoryName(categoryLabels().getOrDefault(entity.getCategoryCode(), entity.getCategoryCode()));

        List<ListenQuestionEntity> questions = listenQuestionDao.selectList(
                new QueryWrapper<ListenQuestionEntity>().eq("group_id", id).orderByAsc("sort_order"));
        vo.setQuestions(questions.stream().map(this::toQuestionVo).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 整组编辑保存。单事务内 diff：带 id → 更新（保 id）；无 id → 新增；库内有但 body 缺失 → 物理删除。
     * 判重在写入前整体完成（组名大类内唯一排除自身、题干组内唯一排除自身），任一违反整单拒绝，
     * 与导入的整单失败制（Q10-B）语义一致，不产生半截数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGroup(Long id, ListenGroupSaveDTO dto) {
        ListenGroupEntity group = baseDao.selectById(id);
        if (group == null) {
            throw new RenException(ErrorCode.LISTEN_GROUP_NOT_FOUND);
        }

        // 组名判重：同一大类内排除自身
        Long nameConflicts = baseDao.selectCount(new QueryWrapper<ListenGroupEntity>()
                .eq("category_code", group.getCategoryCode())
                .eq("name", dto.getName())
                .ne("id", id));
        if (nameConflicts > 0) {
            throw new RenException(ErrorCode.LISTEN_GROUP_NAME_DUPLICATE);
        }

        List<ListenQuestionEntity> existing = listenQuestionDao.selectList(
                new QueryWrapper<ListenQuestionEntity>().eq("group_id", id));
        Map<Long, ListenQuestionEntity> existingById = existing.stream()
                .collect(Collectors.toMap(ListenQuestionEntity::getId, e -> e));

        // 题干判重：body 内先去重（排除 id 指向同一题的自身项），再与库内未被 body 覆盖的题比对
        Set<String> textsInBody = new HashSet<>();
        Set<Long> idsInBody = new HashSet<>();
        for (ListenGroupSaveDTO.Question question : dto.getQuestions()) {
            if (!textsInBody.add(question.getText())) {
                throw new RenException(ErrorCode.LISTEN_QUESTION_TEXT_DUPLICATE);
            }
            if (question.getId() != null) {
                idsInBody.add(question.getId());
            }
        }
        for (ListenQuestionEntity entity : existing) {
            if (!idsInBody.contains(entity.getId()) && textsInBody.contains(entity.getText())) {
                throw new RenException(ErrorCode.LISTEN_QUESTION_TEXT_DUPLICATE);
            }
        }

        group.setName(dto.getName());
        baseDao.updateById(group);

        // 写题目：带 id 更新（id 保持稳定），无 id 新增
        for (ListenGroupSaveDTO.Question question : dto.getQuestions()) {
            ListenQuestionEntity entity = question.getId() != null ? existingById.get(question.getId()) : null;
            if (entity == null) {
                entity = new ListenQuestionEntity();
                entity.setGroupId(id);
                fillQuestion(entity, question);
                listenQuestionDao.insert(entity);
            } else {
                fillQuestion(entity, question);
                listenQuestionDao.updateById(entity);
            }
        }

        // 库内有但 body 缺失的题物理删除（题库物理删除，无软删语义）
        existing.stream()
                .filter(entity -> !idsInBody.contains(entity.getId()))
                .forEach(entity -> listenQuestionDao.deleteById(entity.getId()));
    }

    /** 用 DTO 内容填充题目实体（新建与更新共用）；选项序列化为 {\"options\":[{key,text}]} 与存量导入文件一致 */
    private void fillQuestion(ListenQuestionEntity entity, ListenGroupSaveDTO.Question question) {
        entity.setText(question.getText());
        entity.setType(question.getType());
        entity.setSortOrder(question.getSortOrder());
        try {
            List<Map<String, String>> options = question.getOptions().stream()
                    .map(option -> Map.of("key", option.getKey(), "text", option.getText()))
                    .collect(Collectors.toList());
            entity.setOptionsJson(OBJECT_MAPPER.writeValueAsString(Map.of("options", options)));
        } catch (Exception e) {
            // DTO 校验已保证 options 非空且字段非空，序列化失败属内部错误而非业务异常
            throw new RenException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    /** 整组物理删除：组内题目级联删除，单事务（先删题再删组） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        ListenGroupEntity group = baseDao.selectById(id);
        if (group == null) {
            throw new RenException(ErrorCode.LISTEN_GROUP_NOT_FOUND);
        }
        listenQuestionDao.delete(new QueryWrapper<ListenQuestionEntity>().eq("group_id", id));
        baseDao.deleteById(id);
    }

    /** 单题物理删除 */
    @Override
    public void deleteQuestion(Long id) {
        int rows = listenQuestionDao.deleteById(id);
        if (rows == 0) {
            throw new RenException(ErrorCode.LISTEN_QUESTION_NOT_FOUND);
        }
    }

    /** 实体转题目 VO：options_json JSON 列解析为 key/text 选项列表 */
    private ListenGroupDetailVO.Question toQuestionVo(ListenQuestionEntity entity) {
        ListenGroupDetailVO.Question question = new ListenGroupDetailVO.Question();
        question.setId(entity.getId());
        question.setText(entity.getText());
        question.setType(entity.getType());
        question.setSortOrder(entity.getSortOrder());
        question.setOptions(parseOptions(entity.getOptionsJson()));
        return question;
    }

    /**
     * 解析选项 JSON。库内格式为 {"options":[{"key":"A","text":"..."}]}（与存量导入文件一致）；
     * 解析失败按空列表处理——本期只读展示，坏数据不应让整个详情页 500，由导入期的校验兜底格式。
     */
    private List<ListenGroupDetailVO.Option> parseOptions(String optionsJson) {
        if (StringUtils.isBlank(optionsJson)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(optionsJson);
            // 兼容两种形态：{"options":[...]} 包装与裸数组 [...]
            JsonNode array = root.isArray() ? root : root.path("options");
            if (!array.isArray()) {
                return List.of();
            }
            return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                    .map(node -> {
                        ListenGroupDetailVO.Option option = new ListenGroupDetailVO.Option();
                        option.setKey(node.path("key").asText(null));
                        option.setText(node.path("text").asText(null));
                        return option;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 批量填充组内题数：一次 GROUP BY 查询，避免逐组查库（N+1） */
    private void fillQuestionCount(List<ListenGroupVO> voList) {
        if (voList.isEmpty()) {
            return;
        }
        List<Long> groupIds = voList.stream().map(ListenGroupVO::getId).collect(Collectors.toList());
        List<Map<String, Object>> counts = listenQuestionDao.selectMaps(new QueryWrapper<ListenQuestionEntity>()
                .select("group_id AS groupId", "COUNT(*) AS cnt")
                .in("group_id", groupIds)
                .groupBy("group_id"));
        Map<Long, Integer> countByGroup = new HashMap<>();
        for (Map<String, Object> row : counts) {
            countByGroup.put(((Number) row.get("groupId")).longValue(), ((Number) row.get("cnt")).intValue());
        }
        voList.forEach(vo -> vo.setQuestionCount(countByGroup.getOrDefault(vo.getId(), 0)));
    }

    /** 批量填充大类显示名，与 smallobject 的回落策略一致 */
    private void fillCategoryName(List<ListenGroupVO> voList) {
        Map<String, String> labelByValue = categoryLabels();
        voList.forEach(
                vo -> vo.setCategoryName(labelByValue.getOrDefault(vo.getCategoryCode(), vo.getCategoryCode())));
    }

    /** 字典 dict_value -> dict_label 映射 */
    private Map<String, String> categoryLabels() {
        List<SysDictDataItem> dictItems = sysDictDataService
                .getDictDataByType(Constant.DictType.LISTEN_CATEGORY.getValue());
        Map<String, String> labelByValue = new HashMap<>();
        if (dictItems == null) {
            return labelByValue;
        }
        for (SysDictDataItem item : dictItems) {
            if (item.getKey() != null) {
                labelByValue.put(item.getKey(), item.getName());
            }
        }
        return labelByValue;
    }
}
