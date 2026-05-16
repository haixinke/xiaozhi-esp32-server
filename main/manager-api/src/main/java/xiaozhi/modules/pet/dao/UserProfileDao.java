package xiaozhi.modules.pet.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.pet.entity.UserProfileEntity;

/**
 * UserProfile Dao对象
 *
 * @author TDD
 * @version 1.0, 2025-05-16
 * @since 1.0.0
 */
@Mapper
public interface UserProfileDao extends BaseMapper<UserProfileEntity> {
}
