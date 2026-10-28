package xiaozhi.modules.listen.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.listen.entity.ListenAnswerEntity;

/**
 * 答题记录 DAO。
 * 基础 CRUD 由 MyBatis-Plus 提供；答题记录物理删除，无需 deleted 过滤。
 */
@Mapper
public interface ListenAnswerDao extends BaseDao<ListenAnswerEntity> {
}
