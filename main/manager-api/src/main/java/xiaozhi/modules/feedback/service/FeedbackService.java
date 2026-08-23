package xiaozhi.modules.feedback.service;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.feedback.dto.FeedbackHandleDTO;
import xiaozhi.modules.feedback.dto.FeedbackSubmitDTO;
import xiaozhi.modules.feedback.vo.FeedbackAdminVO;
import xiaozhi.modules.feedback.vo.FeedbackSubmitVO;

import java.util.Map;

/**
 * 用户反馈服务
 */
public interface FeedbackService {

    /**
     * 提交用户反馈，同用户 60 秒内仅允许提交 1 条
     *
     * @param userId 提交用户ID
     * @param dto    反馈内容
     * @return 受理编号
     */
    FeedbackSubmitVO submit(Long userId, FeedbackSubmitDTO dto);

    /**
     * 管理端分页查询反馈，按提交时间倒序
     */
    PageData<FeedbackAdminVO> page(Map<String, Object> params);

    /**
     * 管理端查询反馈详情
     */
    FeedbackAdminVO get(Long id);

    /**
     * 管理端处理反馈：更新状态与备注
     */
    void handle(FeedbackHandleDTO dto);
}
