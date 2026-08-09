package xiaozhi.modules.storyengine.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhi.modules.storyengine.entity.PetStoryHistoryEntity;

/**
 * 故事历史快照 DAO。历史只追加不修改，按原型 + started_at 倒序分页。
 */
@Mapper
public interface PetStoryHistoryDao extends BaseMapper<PetStoryHistoryEntity> {
}
