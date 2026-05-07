package xiaozhi.modules.pet.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.pet.entity.PetEntity;

@Mapper
public interface PetDao extends BaseMapper<PetEntity> {
}
