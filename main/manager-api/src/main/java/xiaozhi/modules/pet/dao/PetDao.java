package xiaozhi.modules.pet.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xiaozhi.modules.pet.entity.PetEntity;

@Mapper
public interface PetDao extends BaseMapper<PetEntity> {

    /** 同一用户的各领养渠道共享行锁，避免跨原型渠道互斥的并发空窗。 */
    @Select("SELECT id FROM sys_user WHERE id = #{userId} FOR UPDATE")
    Long lockAdoptionUser(@Param("userId") Long userId);

    @Select("SELECT * FROM ai_pet WHERE id = #{id} FOR UPDATE")
    PetEntity selectByIdForUpdate(@Param("id") String id);
}
