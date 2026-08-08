package xiaozhi.modules.storyengine.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xiaozhi.modules.storyengine.entity.PetStoryStateEntity;

@Mapper
public interface PetStoryStateDao extends BaseMapper<PetStoryStateEntity> {

    @Select("SELECT * FROM ai_pet_story_state WHERE pet_prototype = #{prototype} FOR UPDATE")
    PetStoryStateEntity selectByPrototypeForUpdate(@Param("prototype") String prototype);
}
