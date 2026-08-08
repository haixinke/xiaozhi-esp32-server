package xiaozhi.modules.storyengine.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xiaozhi.modules.storyengine.entity.PetStoryStateEntity;

@Mapper
public interface PetStoryStateDao extends BaseMapper<PetStoryStateEntity> {

    /**
     * 按原型锁定并读取当前状态行（SELECT ... FOR UPDATE）。
     * 与 last_evaluated_hour 时槽标记共同保证多实例下每原型每小时最多计算一次。
     */
    @Select("SELECT * FROM ai_pet_story_state WHERE pet_prototype = #{prototype} FOR UPDATE")
    PetStoryStateEntity selectByPrototypeForUpdate(@Param("prototype") String prototype);
}
