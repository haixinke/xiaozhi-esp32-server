package xiaozhi.modules.agent.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.dao.AgentChatTitleDao;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentChatTitleEntity;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.ChatHistoryExportService;
import xiaozhi.modules.email.dto.EmailSendDTO;
import xiaozhi.modules.email.service.EmailService;

/**
 * 聊天记录导出服务实现
 * <p>
 * 按用户导出全部智能体的聊天记录，生成 TXT 作为邮件附件异步发送。
 * 数据量可能较大，按会话分批查询并流式拼接，单页不超过 500 条。
 */
@Slf4j
@Service
@AllArgsConstructor
public class ChatHistoryExportServiceImpl implements ChatHistoryExportService {

    private static final int SESSION_PAGE_SIZE = 100;
    private static final int MESSAGE_PAGE_SIZE = 500;

    private final AgentService agentService;
    private final AiAgentChatHistoryDao chatHistoryDao;
    private final AgentChatTitleDao agentChatTitleDao;
    private final EmailService emailService;

    @Override
    @Async("taskExecutor")
    public void exportAndEmailAsync(Long userId, String toAddress) {
        try {
            byte[] content = buildExportContent(userId);
            if (content == null) {
                log.info("用户无聊天记录可导出，跳过邮件发送，userId={}", userId);
                return;
            }
            EmailSendDTO dto = new EmailSendDTO();
            dto.setToAddress(toAddress);
            dto.setSubject("蛋宝宝聊天记录导出");
            dto.setHtmlBody(buildHtmlBody());
            dto.setAttachmentName("chat-history.txt");
            InputStream attachment = new ByteArrayInputStream(content);
            dto.setAttachmentStream(attachment);
            try {
                emailService.sendEmail(dto);
            } finally {
                try {
                    attachment.close();
                } catch (IOException closeEx) {
                    log.warn("关闭导出附件流失败", closeEx);
                }
            }
            log.info("聊天记录导出邮件发送成功，userId={}, to={}", userId, toAddress);
        } catch (Exception e) {
            log.error("聊天记录导出或邮件发送失败，userId={}, to={}", userId, toAddress, e);
        }
    }

    /**
     * 组装导出文件内容；无记录时返回 null
     * <p>
     * 先分页收集全部会话，再批量预取会话标题（避免逐会话查询的 N+1），最后逐会话追加消息。
     */
    private byte[] buildExportContent(Long userId) {
        List<AgentDTO> agents = agentService.getUserAgents(userId, null, null);
        if (agents == null || agents.isEmpty()) {
            return null;
        }

        // 第一遍：收集每个智能体的全部会话（保留智能体名与顺序）
        List<AgentSessions> agentSessionsList = new java.util.ArrayList<>();
        List<String> allSessionIds = new java.util.ArrayList<>();
        for (AgentDTO agent : agents) {
            String agentId = agent.getId();
            String agentName = StringUtils.defaultIfBlank(agent.getAgentName(), agentId);
            List<String> sessionIds = collectSessionIds(agentId);
            if (!sessionIds.isEmpty()) {
                agentSessionsList.add(new AgentSessions(agentName, agentId, sessionIds));
                allSessionIds.addAll(sessionIds);
            }
        }
        if (allSessionIds.isEmpty()) {
            return null;
        }

        // 批量预取所有会话标题（一次查询，避免 N+1）
        Map<String, String> titleMap = loadTitleMap(allSessionIds);

        // 第二遍：逐会话追加消息
        StringBuilder sb = new StringBuilder();
        for (AgentSessions as : agentSessionsList) {
            for (String sessionId : as.sessionIds) {
                appendSession(sb, as.agentName, as.agentId, sessionId, titleMap.get(sessionId));
            }
        }

        if (sb.length() == 0) {
            return null;
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 某智能体的会话集合（内部持有结构）
     */
    private static final class AgentSessions {
        private final String agentName;
        private final String agentId;
        private final List<String> sessionIds;

        private AgentSessions(String agentName, String agentId, List<String> sessionIds) {
            this.agentName = agentName;
            this.agentId = agentId;
            this.sessionIds = sessionIds;
        }
    }

    /**
     * 分页收集某智能体的全部会话ID（按最近活跃倒序）
     */
    private List<String> collectSessionIds(String agentId) {
        List<String> sessionIds = new java.util.ArrayList<>();
        long pageNo = 1;
        while (true) {
            IPage<Map<String, Object>> sessionPage = querySessionPage(agentId, pageNo);
            List<Map<String, Object>> sessions = sessionPage.getRecords();
            if (sessions == null || sessions.isEmpty()) {
                break;
            }
            for (Map<String, Object> session : sessions) {
                sessionIds.add((String) session.get("session_id"));
            }
            if (sessions.size() < SESSION_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        return sessionIds;
    }

    /**
     * 批量加载会话标题，返回 sessionId -> title 映射
     */
    private Map<String, String> loadTitleMap(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return new HashMap<>();
        }
        // selectBatchIds 按主键查询，这里主键是 id 而非 session_id，改用 session_id in 查询
        QueryWrapper<AgentChatTitleEntity> wrapper = new QueryWrapper<>();
        wrapper.in("session_id", sessionIds);
        List<AgentChatTitleEntity> titles = agentChatTitleDao.selectList(wrapper);
        return titles.stream()
                .filter(t -> t.getTitle() != null)
                .collect(Collectors.toMap(AgentChatTitleEntity::getSessionId,
                        AgentChatTitleEntity::getTitle, (a, b) -> a));
    }

    /**
     * 分页查询某智能体的会话列表（按最近活跃倒序）
     */
    private IPage<Map<String, Object>> querySessionPage(String agentId, long pageNo) {
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.select("session_id", "MAX(created_at) as latest_at")
                .eq("agent_id", agentId)
                .groupBy("session_id")
                .orderByDesc("latest_at");
        return chatHistoryDao.selectMapsPage(new Page<>(pageNo, SESSION_PAGE_SIZE), wrapper);
    }

    /**
     * 追加一个会话的完整聊天记录到导出内容
     *
     * @param title 已批量预取的会话标题（可为 null）
     */
    private void appendSession(StringBuilder sb, String agentName, String agentId, String sessionId, String title) {
        sb.append("========== 智能体：").append(agentName).append(" ==========\n");
        if (StringUtils.isNotBlank(title)) {
            sb.append("【会话】").append(title).append('\n');
        } else {
            sb.append("【会话】").append(sessionId).append('\n');
        }

        long pageNo = 1;
        while (true) {
            QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("agent_id", agentId)
                    .eq("session_id", sessionId)
                    .orderByAsc("created_at")
                    .orderByAsc("id");
            IPage<AgentChatHistoryEntity> page = chatHistoryDao.selectPage(
                    new Page<>(pageNo, MESSAGE_PAGE_SIZE), wrapper);
            List<AgentChatHistoryEntity> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            for (AgentChatHistoryEntity msg : records) {
                boolean isUserMessage = msg.getChatType() != null && msg.getChatType() == 1;
                String role = isUserMessage ? "用户" : "智能体";
                String direction = isUserMessage ? ">>" : "<<";
                String content = extractContent(msg.getContent());
                sb.append('[').append(role).append("]-[").append(msg.getCreatedAt()).append(']')
                        .append(direction).append(':').append(content).append('\n');
            }
            if (records.size() < MESSAGE_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        sb.append('\n');
    }

    /**
     * 从 content 字段提取纯文本（兼容 JSON 包装格式）
     */
    private String extractContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        try {
            Map<String, Object> jsonMap = JsonUtils.parseObject(content, Map.class);
            if (jsonMap != null && jsonMap.containsKey("content")) {
                Object contentObj = jsonMap.get("content");
                return contentObj != null ? contentObj.toString() : content;
            }
        } catch (Exception e) {
            // 非 JSON 格式，直接返回原文
        }
        return content;
    }

    private String buildHtmlBody() {
        return "<html><body style=\"font-family:Arial,sans-serif;line-height:1.6;\">"
                + "<p>你好，</p>"
                + "<p>你申请的蛋宝宝聊天记录已导出，请查收附件 <b>chat-history.txt</b>。</p>"
                + "<p style=\"color:#999;font-size:12px;\">本邮件由系统自动发送，请勿直接回复。</p>"
                + "</body></html>";
    }
}
