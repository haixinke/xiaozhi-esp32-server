package xiaozhi.modules.star.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.star.entity.StarTransactionEntity;

/**
 * 星星流水 DAO。履约状态/产物不落在流水表（ADR 0009），无需履约相关 SQL。
 */
@Mapper
public interface StarTransactionDao extends BaseMapper<StarTransactionEntity> {
}
