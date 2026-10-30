package xiaozhi.modules.star.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xiaozhi.modules.star.entity.StarAccountEntity;

@Mapper
public interface StarAccountDao extends BaseMapper<StarAccountEntity> {

    /**
     * 原子性加星：balance 直接累加
     * @return 影响行数（0 表示账户不存在）
     */
    @Update("UPDATE ai_star_account SET balance = balance + #{amount}, update_date = NOW() " +
            "WHERE user_id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 原子性扣星：仅当 balance >= amount 时才扣减，杜绝负余额
     * @return 影响行数（0 表示余额不足或账户不存在）
     */
    @Update("UPDATE ai_star_account SET balance = balance - #{amount}, update_date = NOW() " +
            "WHERE user_id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 按用户锁定账户行（SELECT ... FOR UPDATE），串行化同一用户的奖励结算。
     * 须在事务内调用；锁内后续读取为当前读，不受可重复读旧快照影响。
     */
    @Select("SELECT id, user_id, balance FROM ai_star_account WHERE user_id = #{userId} FOR UPDATE")
    StarAccountEntity selectByUserIdForUpdate(@Param("userId") Long userId);
}
