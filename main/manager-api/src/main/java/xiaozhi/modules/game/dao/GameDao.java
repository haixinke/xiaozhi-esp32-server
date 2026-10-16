package xiaozhi.modules.game.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.game.entity.GameEntity;

/**
 * 游戏配置 DAO
 */
@Mapper
public interface GameDao extends BaseDao<GameEntity> {

}
