package xiaozhi.modules.dailynote.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.dailynote.entity.DailyNoteOpenedEntity;

/**
 * 用户已打开记录 DAO。
 * 基础 CRUD 由 MyBatis-Plus 提供；已打开记录永久保留，本期不提供删除能力。
 */
@Mapper
public interface DailyNoteOpenedDao extends BaseDao<DailyNoteOpenedEntity> {
}
