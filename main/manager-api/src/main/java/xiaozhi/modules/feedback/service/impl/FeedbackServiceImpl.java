package xiaozhi.modules.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.feedback.dao.FeedbackDao;
import xiaozhi.modules.feedback.dto.FeedbackHandleDTO;
import xiaozhi.modules.feedback.dto.FeedbackSubmitDTO;
import xiaozhi.modules.feedback.entity.FeedbackEntity;
import xiaozhi.modules.feedback.service.FeedbackService;
import xiaozhi.modules.feedback.vo.FeedbackAdminVO;
import xiaozhi.modules.feedback.vo.FeedbackSubmitVO;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈服务实现
 */
@Service
@AllArgsConstructor
public class FeedbackServiceImpl extends BaseServiceImpl<FeedbackDao, FeedbackEntity> implements FeedbackService {

    /**
     * 同一用户提交限流窗口（秒）
     */
    private static final long SUBMIT_RATE_LIMIT_SECONDS = 60L;

    private final RedisUtils redisUtils;

    @Override
    public FeedbackSubmitVO submit(Long userId, FeedbackSubmitDTO dto) {
        // 限流：同用户 60 秒窗口内仅允许 1 条，防止刷屏刷库
        String rateLimitKey = RedisKeys.getFeedbackSubmitCountKey(String.valueOf(userId));
        Long submitCount = redisUtils.increment(rateLimitKey, SUBMIT_RATE_LIMIT_SECONDS);
        if (submitCount != null && submitCount > 1) {
            throw new RenException(ErrorCode.FEEDBACK_SUBMIT_TOO_FREQUENTLY);
        }

        FeedbackEntity entity = new FeedbackEntity();
        entity.setUserId(userId);
        entity.setType(dto.getType());
        entity.setContent(dto.getContent().trim());
        entity.setStatus(0);
        insert(entity);

        FeedbackSubmitVO vo = new FeedbackSubmitVO();
        vo.setReceiptNumber(buildReceiptNumber(entity.getId(), entity.getCreateDate()));
        vo.setCreateDate(entity.getCreateDate());
        return vo;
    }

    @Override
    public PageData<FeedbackAdminVO> page(Map<String, Object> params) {
        IPage<FeedbackEntity> page = baseDao.selectPage(getPage(params, "create_date", false), getWrapper(params));
        PageData<FeedbackAdminVO> pageData = getPageData(page, FeedbackAdminVO.class);
        pageData.getList().forEach(vo -> vo.setReceiptNumber(buildReceiptNumber(vo.getId(), vo.getCreateDate())));
        return pageData;
    }

    @Override
    public FeedbackAdminVO get(Long id) {
        FeedbackEntity entity = baseDao.selectById(id);
        if (entity == null) {
            throw new RenException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        FeedbackAdminVO vo = ConvertUtils.sourceToTarget(entity, FeedbackAdminVO.class);
        vo.setReceiptNumber(buildReceiptNumber(entity.getId(), entity.getCreateDate()));
        return vo;
    }

    @Override
    public void handle(FeedbackHandleDTO dto) {
        FeedbackEntity entity = baseDao.selectById(dto.getId());
        if (entity == null) {
            throw new RenException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
        updateById(entity);
    }

    private QueryWrapper<FeedbackEntity> getWrapper(Map<String, Object> params) {
        String status = (String) params.get("status");
        String type = (String) params.get("type");

        QueryWrapper<FeedbackEntity> wrapper = new QueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(status), "status", status);
        wrapper.eq(StringUtils.isNotBlank(type), "type", type);
        return wrapper;
    }

    /**
     * 受理编号：FB + 提交日期 + 自增 id 派生的 6 位序列，同一记录恒定可重现
     */
    static String buildReceiptNumber(Long id, Date createDate) {
        String datePart = new SimpleDateFormat("yyyyMMdd").format(createDate);
        return "FB" + datePart + "-" + String.format("%06d", id % 1000000);
    }
}
