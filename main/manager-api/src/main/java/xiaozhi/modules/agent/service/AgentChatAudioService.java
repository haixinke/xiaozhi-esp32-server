package xiaozhi.modules.agent.service;

import com.baomidou.mybatisplus.extension.repository.IRepository;

import xiaozhi.modules.agent.entity.AgentChatAudioEntity;

/**
 * 智能体聊天音频数据表处理service
 *
 * @author Goody
 * @version 1.0, 2025/5/8
 * @since 1.0.0
 */
public interface AgentChatAudioService extends IRepository<AgentChatAudioEntity> {
    /**
     * 保存音频数据
     *
     * @param audioData 音频数据
     * @param macAddress 设备MAC地址
     * @return 音频ID
     */
    String saveAudio(byte[] audioData, String macAddress);

    /**
     * 获取音频数据
     *
     * @param audioId 音频ID
     * @return 音频数据
     */
    byte[] getAudio(String audioId);

    /**
     * 删除音频数据（含 OSS 对象），用于消息撤回
     * <p>
     * 若音频存储在 OSS，先删除 OSS 对象（失败仅告警，不阻断），
     * 再删除 ai_agent_chat_audio 表行。audioId 为空或不存在时直接返回。
     *
     * @param audioId 音频ID
     */
    void deleteAudioWithOss(String audioId);
}
