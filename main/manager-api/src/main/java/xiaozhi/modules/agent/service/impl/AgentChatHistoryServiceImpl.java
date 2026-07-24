package xiaozhi.modules.agent.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.hutool.core.collection.ListUtil;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;

import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.ToolUtil;
import xiaozhi.modules.agent.Enums.AgentChatHistoryType;
import xiaozhi.modules.agent.dao.AiAgentChatAudioDao;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.dto.AgentChatHistoryDTO;
import xiaozhi.modules.agent.dto.AgentChatSessionDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.service.AgentChatAudioService;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.agent.service.AgentChatTitleService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.vo.AgentChatHistoryListVO;
import xiaozhi.modules.agent.vo.AgentChatHistoryUserVO;

/**
 * 智能体聊天记录表处理service {@link AgentChatHistoryService} impl
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
@Slf4j
@Service
public class AgentChatHistoryServiceImpl extends CrudRepository<AiAgentChatHistoryDao, AgentChatHistoryEntity>
        implements AgentChatHistoryService {

    private final AgentChatTitleService agentChatTitleService;
    private final OssService ossService;
    private final AiAgentChatAudioDao aiAgentChatAudioDao;
    // @Lazy 代理注入，打破与 AgentServiceImpl 的循环依赖（AgentService -> 本类 -> AgentService）
    private final AgentService agentService;
    private final AgentChatAudioService agentChatAudioService;

    public AgentChatHistoryServiceImpl(AgentChatTitleService agentChatTitleService, OssService ossService,
            AiAgentChatAudioDao aiAgentChatAudioDao, @Lazy AgentService agentService,
            AgentChatAudioService agentChatAudioService) {
        this.agentChatTitleService = agentChatTitleService;
        this.ossService = ossService;
        this.aiAgentChatAudioDao = aiAgentChatAudioDao;
        this.agentService = agentService;
        this.agentChatAudioService = agentChatAudioService;
    }

    @Override
    public PageData<AgentChatSessionDTO> getSessionListByAgentId(Map<String, Object> params) {
        String agentId = (String) params.get("agentId");
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Integer.parseInt(params.get(Constant.LIMIT).toString());

        // 构建查询条件
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.select("session_id", "MAX(created_at) as created_at", "COUNT(*) as chat_count")
                .eq("agent_id", agentId)
                .groupBy("session_id")
                .orderByDesc("created_at");

        // 执行分页查询
        Page<Map<String, Object>> pageParam = new Page<>(page, limit);
        IPage<Map<String, Object>> result = this.baseMapper.selectMapsPage(pageParam, wrapper);

        List<AgentChatSessionDTO> records = result.getRecords().stream().map(map -> {
            AgentChatSessionDTO dto = new AgentChatSessionDTO();
            dto.setSessionId((String) map.get("session_id"));
            dto.setCreatedAt((LocalDateTime) map.get("created_at"));
            dto.setChatCount(((Number) map.get("chat_count")).intValue());
            dto.setTitle(agentChatTitleService.getTitleBySessionId(dto.getSessionId()));
            return dto;
        }).collect(Collectors.toList());

        return new PageData<>(records, result.getTotal());
    }

    @Override
    public List<AgentChatHistoryDTO> getChatHistoryBySessionId(String agentId, String sessionId) {
        // 构建查询条件
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("agent_id", agentId)
                .eq("session_id", sessionId)
                .orderByAsc("created_at");

        // 查询聊天记录
        List<AgentChatHistoryEntity> historyList = list(wrapper);

        // 转换为DTO
        return ConvertUtils.sourceToTarget(historyList, AgentChatHistoryDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAgentId(String agentId, Boolean deleteAudio, Boolean deleteText) {
        if (deleteAudio) {
            // 分批删除音频,避免超时
            List<String> audioIds = baseMapper.getAudioIdsByAgentId(agentId);
            if (ToolUtil.isNotEmpty(audioIds)) {
                // 每批删除1000条
                List<List<String>> batch = ListUtil.split(audioIds, 1000);
                batch.forEach(dataList -> {
                    // 先从OSS删除对象
                    if (ossService.isEnabled()) {
                        try {
                            List<String> ossKeys = aiAgentChatAudioDao.getOssKeysByAudioIds(dataList);
                            if (ToolUtil.isNotEmpty(ossKeys)) {
                                ossService.deleteBatch(ossKeys);
                            }
                        } catch (Exception e) {
                            log.error("批量删除OSS音频对象部分失败, agentId={}, failedOssKeys将在后续清理,",
                                    agentId, e);
                        }
                    }
                    // 再删除DB记录
                    baseMapper.deleteAudioByIds(dataList);
                });
            }
        }
        if (deleteAudio && !deleteText) {
            baseMapper.deleteAudioIdByAgentId(agentId);
        }
        if (deleteText) {
            baseMapper.deleteHistoryByAgentId(agentId);
        }

    }

    @Override
    public List<AgentChatHistoryUserVO> getRecentlyFiftyByAgentId(String agentId) {
        // 构建查询条件(不添加按照创建时间排序，数据本来就是主键越大创建时间越大
        // 不添加这样可以减少排序全部数据在分页的全盘扫描消耗)
        LambdaQueryWrapper<AgentChatHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AgentChatHistoryEntity::getContent, AgentChatHistoryEntity::getAudioId)
                .eq(AgentChatHistoryEntity::getAgentId, agentId)
                .eq(AgentChatHistoryEntity::getChatType, AgentChatHistoryType.USER.getValue())
                .isNotNull(AgentChatHistoryEntity::getAudioId)
                // 添加此行，确保查询结果按照创建时间降序排列
                // 使用id的原因：数据形式，id越大的创建时间就越晚，所以使用id的结果和创建时间降序排列结果一样
                // id作为降序排列的优势，性能高，有主键索引，不用在排序的时候重新进行排除扫描比较
                .orderByDesc(AgentChatHistoryEntity::getId);

        // 构建分页查询，查询前50页数据
        Page<AgentChatHistoryEntity> pageParam = new Page<>(0, 50);
        IPage<AgentChatHistoryEntity> result = this.baseMapper.selectPage(pageParam, wrapper);
        return result.getRecords().stream().map(item -> {
            AgentChatHistoryUserVO vo = ConvertUtils.sourceToTarget(item, AgentChatHistoryUserVO.class);
            // 处理 content 字段，确保只返回聊天内容
            if (vo != null && vo.getContent() != null) {
                vo.setContent(extractContentFromString(vo.getContent()));
            }
            return vo;
        }).toList();
    }

    /**
     * 从 content 字段中提取聊天内容
     * 如果 content 是 JSON 格式（如 {"speaker": "未知说话人", "content": "现在几点了。"}），则提取 content
     * 字段
     * 如果 content 是普通字符串，则直接返回
     * 
     * @param content 原始内容
     * @return 提取的聊天内容
     */
    private String extractContentFromString(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        // 尝试解析为 JSON
        try {
            Map<String, Object> jsonMap = JsonUtils.parseObject(content, Map.class);
            if (jsonMap != null && jsonMap.containsKey("content")) {
                Object contentObj = jsonMap.get("content");
                return contentObj != null ? contentObj.toString() : content;
            }
        } catch (Exception e) {
            // 如果不是有效的 JSON，直接返回原内容
        }

        // 如果不是 JSON 格式或没有 content 字段，直接返回原内容
        return content;
    }

    @Override
    public String getContentByAudioId(String audioId) {
        AgentChatHistoryEntity agentChatHistoryEntity = baseMapper
                .selectOne(new LambdaQueryWrapper<AgentChatHistoryEntity>()
                        .select(AgentChatHistoryEntity::getContent)
                        .eq(AgentChatHistoryEntity::getAudioId, audioId));
        return agentChatHistoryEntity == null ? null : agentChatHistoryEntity.getContent();
    }

    @Override
    public String getAgentIdBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        AgentChatHistoryEntity entity = baseMapper.selectOne(
                new LambdaQueryWrapper<AgentChatHistoryEntity>()
                        .select(AgentChatHistoryEntity::getAgentId)
                        .eq(AgentChatHistoryEntity::getSessionId, sessionId)
                        .last("LIMIT 1"));
        return entity == null ? null : entity.getAgentId();
    }

    @Override
    public String getAgentIdByAudioId(String audioId) {
        if (audioId == null || audioId.isBlank()) {
            return null;
        }
        AgentChatHistoryEntity entity = baseMapper.selectOne(
                new LambdaQueryWrapper<AgentChatHistoryEntity>()
                        .select(AgentChatHistoryEntity::getAgentId)
                        .eq(AgentChatHistoryEntity::getAudioId, audioId)
                        .last("LIMIT 1"));
        return entity == null ? null : entity.getAgentId();
    }

    @Override
    public boolean isAudioOwnedByAgent(String audioId, String agentId) {
        // 查询是否有指定音频id和智能体id的数据，如果有且只有一条说明此数据属性此智能体
        Long row = baseMapper.selectCount(new LambdaQueryWrapper<AgentChatHistoryEntity>()
                .eq(AgentChatHistoryEntity::getAudioId, audioId)
                .eq(AgentChatHistoryEntity::getAgentId, agentId));
        return row == 1;
    }

    @Override
    public PageData<AgentChatHistoryListVO> getChatHistoryList(String agentId, String macAddress,
            String createdBefore, Map<String, Object> params) {
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Math.min(Integer.parseInt(params.get(Constant.LIMIT).toString()), 50);

        LambdaQueryWrapper<AgentChatHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AgentChatHistoryEntity::getId, AgentChatHistoryEntity::getChatType,
                AgentChatHistoryEntity::getContent, AgentChatHistoryEntity::getCreatedAt,
                AgentChatHistoryEntity::getAudioId)
                .eq(AgentChatHistoryEntity::getAgentId, agentId)
                .eq(AgentChatHistoryEntity::getMacAddress, macAddress)
                .in(AgentChatHistoryEntity::getChatType,
                        AgentChatHistoryType.USER.getValue(),
                        AgentChatHistoryType.AGENT.getValue());
        if (StringUtils.isNotBlank(createdBefore)) {
            wrapper.lt(AgentChatHistoryEntity::getCreatedAt, createdBefore);
        }
        wrapper.orderByDesc(AgentChatHistoryEntity::getCreatedAt);

        Page<AgentChatHistoryEntity> pageParam = new Page<>(page, limit);
        IPage<AgentChatHistoryEntity> result = this.baseMapper.selectPage(pageParam, wrapper);

        List<AgentChatHistoryListVO> records = ConvertUtils.sourceToTarget(result.getRecords(),
                AgentChatHistoryListVO.class);
        return new PageData<>(records, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recall(Long messageId, Long userId) {
        AgentChatHistoryEntity entity = this.getById(messageId);
        if (entity == null) {
            // 消息不存在视同无权限，避免暴露存在性
            throw new RenException(ErrorCode.CHAT_HISTORY_NO_PERMISSION);
        }
        // 仅允许撤回用户消息（Byte.equals 接收自动装箱的 byte 参数）
        if (entity.getChatType() == null
                || !entity.getChatType().equals(AgentChatHistoryType.USER.getValue())) {
            throw new RenException(ErrorCode.CHAT_HISTORY_NO_PERMISSION);
        }
        // 校验 agent 归属
        if (!agentService.checkAgentPermission(entity.getAgentId(), userId)) {
            throw new RenException(ErrorCode.CHAT_HISTORY_NO_PERMISSION);
        }
        // 删除关联音频（含 OSS 对象）
        if (StringUtils.isNotBlank(entity.getAudioId())) {
            agentChatAudioService.deleteAudioWithOss(entity.getAudioId());
        }
        // 删除历史行（直接走 baseMapper，避免 ServiceImpl.removeById 触发 TableInfo 逻辑删除检查）
        this.baseMapper.deleteById(messageId);
    }
}
