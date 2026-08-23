package xiaozhi.modules.feedback.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.feedback.entity.FeedbackEntity;

/**
 * 用户反馈 DAO
 */
@Mapper
public interface FeedbackDao extends BaseMapper<FeedbackEntity> {
}
