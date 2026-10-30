package xiaozhi.modules.star.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.star.entity.StarDailyRewardEntity;

/**
 * 每日星星奖励记录 DAO。
 * 基础 CRUD 由 MyBatis-Plus 提供；并发下重复插入由联合唯一约束
 * (user_id, source_type, source_key, reward_date) 兜底。
 */
@Mapper
public interface StarDailyRewardDao extends BaseDao<StarDailyRewardEntity> {
}
