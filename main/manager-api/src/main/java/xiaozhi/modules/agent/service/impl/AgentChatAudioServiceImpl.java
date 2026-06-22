package xiaozhi.modules.agent.service.impl;

import org.springframework.stereotype.Service;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.oss.OssService;
import xiaozhi.common.validator.AssertUtils;
import xiaozhi.modules.agent.dao.AiAgentChatAudioDao;
import xiaozhi.modules.agent.entity.AgentChatAudioEntity;
import xiaozhi.modules.agent.service.AgentChatAudioService;

/**
 * 智能体聊天音频数据表处理service {@link AgentChatAudioService} impl
 *
 * @author Goody
 * @version 1.0, 2025/5/8
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatAudioServiceImpl extends ServiceImpl<AiAgentChatAudioDao, AgentChatAudioEntity>
        implements AgentChatAudioService {

    private final OssService ossService;

    @Override
    public String saveAudio(byte[] audioData) {
        AssertUtils.isNull(audioData, ErrorCode.VOICEPRINT_AUDIO_EMPTY, "audioData");
        if (audioData.length == 0) {
            throw new RenException(ErrorCode.VOICEPRINT_AUDIO_EMPTY, "audioData");
        }

        AgentChatAudioEntity entity = new AgentChatAudioEntity();

        if (ossService.isEnabled()) {
            // OSS模式：先save获取UUID，再上传到OSS
            entity.setAudio(null);
            save(entity);
            String ossKey = OssService.buildAudioOssKey(entity.getId());
            try {
                ossService.upload(ossKey, audioData);
                entity.setOssKey(ossKey);
                updateById(entity);
            } catch (OSSException | ClientException e) {
                log.error("OSS上传失败，回退到DB存储, audioId={}", entity.getId(), e);
                entity.setAudio(audioData);
                entity.setOssKey(null);
                updateById(entity);
            }
        } else {
            // 兼容模式：直接存入DB
            entity.setAudio(audioData);
            save(entity);
        }
        return entity.getId();
    }

    @Override
    public byte[] getAudio(String audioId) {
        AgentChatAudioEntity entity = getById(audioId);
        if (entity == null) {
            return null;
        }

        // 优先从OSS读取
        if (entity.getOssKey() != null && ossService.isEnabled()) {
            try {
                return ossService.download(entity.getOssKey());
            } catch (OSSException | ClientException e) {
                log.error("从OSS下载音频失败, audioId={}, ossKey={}, 回退到BLOB",
                        audioId, entity.getOssKey(), e);
                return entity.getAudio();
            }
        }

        // 回退：从BLOB读取（旧数据或OSS未配置）
        return entity.getAudio();
    }
}
