package xiaozhi.modules.listen.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

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
