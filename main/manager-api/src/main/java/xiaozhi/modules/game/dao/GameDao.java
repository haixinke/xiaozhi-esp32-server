package xiaozhi.modules.game.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.game.entity.GameEntity;

/**
 * 游戏配置 DAO
 */
@Mapper
public interface GameDao extends BaseDao<GameEntity> {

    /**
     * 按游戏类型查询配置；game_type 有唯一索引，至多一行。
     * 结算链路（奖/扣）按类型定位配置，不走主键。
     */
    @Select("SELECT * FROM ai_game WHERE game_type = #{gameType}")
    GameEntity selectByGameType(@Param("gameType") String gameType);
}
