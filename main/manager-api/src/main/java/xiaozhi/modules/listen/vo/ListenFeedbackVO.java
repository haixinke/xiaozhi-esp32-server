package xiaozhi.modules.listen.vo;

import lombok.Data;

/**
 * 听你说·答题反馈文案查询响应。
 * 文案由 LLM 异步生成后写入 Redis（5 分钟 TTL），前端轮询本接口拉取；
 * 未生成完成、生成失败或已过期时 feedback 为 null，前端回落到固定文案。
 */
@Data
public class ListenFeedbackVO {

    /** 个性化反馈文案；未就绪时为 null */
    private String feedback;
}
