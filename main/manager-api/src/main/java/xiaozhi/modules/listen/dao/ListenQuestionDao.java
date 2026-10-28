package xiaozhi.modules.listen.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.listen.entity.ListenQuestionEntity;

/**
 * 题目 DAO。
 * 基础 CRUD 由 MyBatis-Plus 提供；题库为物理删除，无需 deleted 过滤。
 */
@Mapper
public interface ListenQuestionDao extends BaseDao<ListenQuestionEntity> {
}