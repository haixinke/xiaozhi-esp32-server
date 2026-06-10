package xiaozhi.modules.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.payment.entity.PaymentCallbackLogEntity;

@Mapper
public interface PaymentCallbackLogDao extends BaseMapper<PaymentCallbackLogEntity> {
}
