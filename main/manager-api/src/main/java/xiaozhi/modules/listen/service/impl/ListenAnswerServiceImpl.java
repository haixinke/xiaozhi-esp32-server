package xiaozhi.modules.listen.service.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.listen.dao.ListenAnswerDao;
import xiaozhi.modules.listen.dao.ListenGroupDao;
import xiaozhi.modules.listen.dao.ListenQuestionDao;
import xiaozhi.modules.listen.dto.ListenAnswerSubmitDTO;
import xiaozhi.modules.listen.entity.ListenAnswerEntity;
import xiaozhi.modules.listen.entity.ListenGroupEntity;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;
import xiaozhi.modules.listen.service.ListenAnswerService;
import xiaozhi.modules.listen.vo.ListenDrawVO;

/**
 * 听你说·小程序答题 Service 实现。
 * 抽题：SQL 随机（ORDER BY RAND()），排除用户已答过的组（组内任一题有答案即整组排除）。
 * 提交：整组一次性落库，幂等由 UNIQUE KEY (user_id, question_id) 兜底。
 */
@Service
@Slf4j
@AllArgsConstructor
public class ListenAnswerServiceImpl implements ListenAnswerService {

    private final ListenGroupDao listenGroupDao;
    private final ListenQuestionDao listenQuestionDao;
    private final ListenAnswerDao listenAnswerDao;

    /** 解析 options_json 用的共享 ObjectMapper（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 自定义文本最大长度（与 DDL VARCHAR(200) 一致） */
    private static final int CUSTOM_TEXT_MAX_LENGTH = 200;

    @Override
    public ListenDrawVO draw(Long userId) {
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
    public void submitAnswers(Long userId, ListenAnswerSubmitDTO dto) {
        // 校验 questionId 确实属于 groupId：冗余 group_id 列支撑抽题按组排除，
        // 错配会污染排除索引（UNIQUE 约束兜不住），必须拒绝
        Set<Long> questionIdsInGroup = listenQuestionDao.selectObjs(new QueryWrapper<ListenQuestionEntity>()
                .select("id")
                .eq("group_id", dto.getGroupId()))
                .stream().map(o -> ((Number) o).longValue()).collect(Collectors.toSet());
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
            entity.setCustomText(item.getCustomText());
            try {
                listenAnswerDao.insert(entity);
            } catch (DuplicateKeyException e) {
                // 幂等：UNIQUE (user_id, question_id) 冲突说明该题已答过，忽略本行（网络重试/双击安全）
            }
        }
    }

    /** 实体转抽题题目 VO：options_json JSON 列解析为 key/text 选项列表 */
    private ListenDrawVO.Question toQuestionVo(ListenQuestionEntity entity) {
        ListenDrawVO.Question question = new ListenDrawVO.Question();
        question.setId(entity.getId());
        question.setText(entity.getText());
        question.setOptions(parseOptions(entity.getOptionsJson()));
        return question;
    }

    /**
     * 解析选项 JSON。库内格式为裸数组 [{"key":"A","text":"..."}]；
     * 解析失败按空列表处理——坏数据不应让整个抽题 500，由导入期的校验兜底格式；记 warn 日志保留可观测性。
     */
    private List<ListenDrawVO.Option> parseOptions(String optionsJson) {
        if (StringUtils.isBlank(optionsJson)) {
            return List.of();
        }
        try {
            JsonNode array = OBJECT_MAPPER.readTree(optionsJson);
            if (!array.isArray()) {
                log.warn("options_json 不是数组: {}", optionsJson);
                return List.of();
            }
            return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                    .map(node -> {
                        ListenDrawVO.Option option = new ListenDrawVO.Option();
                        option.setKey(node.path("key").asText(null));
                        option.setText(node.path("text").asText(null));
                        return option;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("options_json 解析失败: {}", optionsJson, e);
            return List.of();
        }
    }
}
