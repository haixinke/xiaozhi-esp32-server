package xiaozhi.modules.agent.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;

/**
 * {@link AgentChatHistoryEntity} 智能体聊天历史记录Dao对象
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
@Mapper
public interface AiAgentChatHistoryDao extends BaseMapper<AgentChatHistoryEntity> {

    /**
     * 根据智能体ID删除聊天历史记录
     *
     * @param agentId 智能体ID
     */
    void deleteHistoryByAgentId(String agentId);

    /**
     * 根据智能体ID删除音频ID
     *
     * @param agentId 智能体ID
     */
    void deleteAudioIdByAgentId(String agentId);

    /**
     * 根据智能体ID获取所有音频ID列表
     *
     * @param agentId 智能体ID
     * @return 音频ID列表
     */
    List<String> getAudioIdsByAgentId(String agentId);

    /**
     * 批量删除音频
     *
     * @param audioIds 音频ID列表
     */
    void deleteAudioByIds(@Param("audioIds") List<String> audioIds);

    /**
     * 统计某用户当日发送的用户消息数（chat_type=1）。
     * <p>
     * 通过 ai_device.mac_address 关联用户：同一用户的多台设备消息合并计数，
     * 对应"用户当天"语义。日界由调用方按 Asia/Shanghai 计算后传入 startAt，
     * 不依赖数据库服务器时区，保证前后端"今天"一致。
     *
     * @param userId  当前登录用户 ID（sys_user.id = ai_device.user_id）
     * @param startAt 今日 00:00（Asia/Shanghai）
     * @return 当日用户消息条数
     */
    long countTodayUserMessages(@Param("userId") Long userId, @Param("startAt") LocalDateTime startAt);
}
