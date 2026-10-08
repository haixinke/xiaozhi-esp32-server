package xiaozhi.modules.star.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import xiaozhi.modules.star.entity.StarTransactionEntity;

@Mapper
public interface StarTransactionDao extends BaseMapper<StarTransactionEntity> {

    /**
     * 原子性履约：仅当流水仍处于 pending 时才置 fulfilled 并回填履约产物ID。
     * 并发/重复调用安全——影响行数 0 表示已被履约或不存在。
     *
     * @return 影响行数（1=本次履约成功，0=已被履约或流水不存在）
     */
    @Update("UPDATE ai_star_transaction SET fulfill_status = 'fulfilled', fulfill_ref_id = #{fulfillRefId} " +
            "WHERE id = #{id} AND fulfill_status = 'pending'")
    int markFulfilled(@Param("id") Long id, @Param("fulfillRefId") String fulfillRefId);
}
