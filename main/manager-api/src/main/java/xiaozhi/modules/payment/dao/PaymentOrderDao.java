package xiaozhi.modules.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xiaozhi.modules.payment.entity.PaymentOrderEntity;

import java.util.Date;
import java.util.List;

@Mapper
public interface PaymentOrderDao extends BaseMapper<PaymentOrderEntity> {

    /**
     * 原子地将 0(待支付) → 1(已支付)；返回受影响行数。
     */
    @Update("UPDATE ai_payment_order SET status = 1, paid_at = #{paidAt}, transaction_id = #{transactionId}, " +
            "updated_at = NOW() WHERE id = #{id} AND status = 0")
    int markPaid(@Param("id") Long id,
                 @Param("paidAt") Date paidAt,
                 @Param("transactionId") String transactionId);

    /**
     * 原子地将 1(已支付) → 2(已发货)；返回受影响行数。
     */
    @Update("UPDATE ai_payment_order SET status = 2, fulfilled_at = #{fulfilledAt}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 1")
    int markFulfilled(@Param("id") Long id, @Param("fulfilledAt") Date fulfilledAt);

    /**
     * 原子地将 0(待支付) → 3(已取消)；返回受影响行数。
     * 只能取消待支付状态的订单，避免用户取消请求与支付回调并发时覆盖 PAID 状态。
     */
    @Update("UPDATE ai_payment_order SET status = 3, updated_at = NOW() WHERE id = #{id} AND status = 0")
    int markCancelled(@Param("id") Long id);

    /**
     * 原子地将 0(待支付) → 5(已超时)；返回受影响行数。
     */
    @Update("UPDATE ai_payment_order SET status = 5, updated_at = NOW() WHERE id = #{id} AND status = 0")
    int markExpired(@Param("id") Long id);

    /**
     * 查询已支付但履约超时的订单（paid_at 早于阈值且仍为 PAID 状态）。
     */
    @Select("SELECT * FROM ai_payment_order WHERE status = 1 AND paid_at < #{threshold} LIMIT #{limit}")
    List<PaymentOrderEntity> findPaidBefore(@Param("threshold") Date threshold, @Param("limit") int limit);

    /**
     * 查询已过期的待支付订单（expire_at 早于当前时间且仍为 PENDING 状态）。
     */
    @Select("SELECT * FROM ai_payment_order WHERE status = 0 AND expire_at < #{now} LIMIT #{limit}")
    List<PaymentOrderEntity> findExpiredPending(@Param("now") Date now, @Param("limit") int limit);
}
