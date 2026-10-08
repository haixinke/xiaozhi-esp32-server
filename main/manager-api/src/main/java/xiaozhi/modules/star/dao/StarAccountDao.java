package xiaozhi.modules.star.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
}
