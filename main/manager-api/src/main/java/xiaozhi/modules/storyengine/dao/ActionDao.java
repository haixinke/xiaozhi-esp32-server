package xiaozhi.modules.storyengine.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.storyengine.entity.ActionEntity;

@Mapper
public interface ActionDao extends BaseMapper<ActionEntity> {
}
