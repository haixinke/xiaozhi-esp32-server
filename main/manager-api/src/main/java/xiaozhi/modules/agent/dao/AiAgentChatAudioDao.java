package xiaozhi.modules.agent.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.agent.entity.AgentChatAudioEntity;

/**
 * {@link AgentChatAudioEntity} 智能体聊天音频数据Dao对象
 *
 * @author Goody
 * @version 1.0, 2025/5/8
 * @since 1.0.0
 */
@Mapper
public interface AiAgentChatAudioDao extends BaseMapper<AgentChatAudioEntity> {

    /**
     * 根据音频ID列表查询OSS对象键列表
     *
     * @param audioIds 音频ID列表
     * @return OSS对象键列表（已过滤NULL）
     */
    List<String> getOssKeysByAudioIds(@Param("audioIds") List<String> audioIds);
}