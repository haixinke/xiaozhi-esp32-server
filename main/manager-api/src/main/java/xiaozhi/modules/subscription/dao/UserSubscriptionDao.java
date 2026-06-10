package xiaozhi.modules.subscription.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.subscription.entity.UserSubscriptionEntity;

@Mapper
public interface UserSubscriptionDao extends BaseMapper<UserSubscriptionEntity> {
}
