package xiaozhi.modules.pet.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xiaozhi.modules.pet.entity.PetEntity;

@Mapper
public interface PetDao extends BaseMapper<PetEntity> {

    @Select("SELECT * FROM ai_pet WHERE id = #{id} FOR UPDATE")
    PetEntity selectByIdForUpdate(@Param("id") String id);
}
