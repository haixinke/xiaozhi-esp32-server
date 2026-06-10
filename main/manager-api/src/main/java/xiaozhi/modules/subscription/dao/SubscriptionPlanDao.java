package xiaozhi.modules.subscription.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.subscription.entity.SubscriptionPlanEntity;

@Mapper
public interface SubscriptionPlanDao extends BaseMapper<SubscriptionPlanEntity> {
}
