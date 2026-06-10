package xiaozhi.modules.item.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import xiaozhi.modules.item.entity.UserItemEntity;

@Mapper
public interface UserItemDao extends BaseMapper<UserItemEntity> {

    /**
     * 原子性地消耗道具：仅当 remain_count >= count 时才扣减
     * @return 影响行数（0 表示库存不足或不存在）
     */
    @Update("UPDATE ai_user_item SET used_count = used_count + #{count}, " +
            "remain_count = remain_count - #{count}, updated_at = NOW() " +
            "WHERE user_id = #{userId} AND sku_code = #{skuCode} AND remain_count >= #{count}")
    int deductRemain(@Param("userId") Long userId,
                     @Param("skuCode") String skuCode,
                     @Param("count") int count);
}
