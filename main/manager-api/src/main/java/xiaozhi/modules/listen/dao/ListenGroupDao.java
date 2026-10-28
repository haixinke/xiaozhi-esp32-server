package xiaozhi.modules.listen.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.listen.entity.ListenGroupEntity;

/**
 * 题目分组 DAO。
 * 基础 CRUD 由 MyBatis-Plus 提供；题库为物理删除，无需 deleted 过滤。
 */
@Mapper
public interface ListenGroupDao extends BaseDao<ListenGroupEntity> {
}