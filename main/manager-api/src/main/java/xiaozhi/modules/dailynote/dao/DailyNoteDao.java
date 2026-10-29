package xiaozhi.modules.dailynote.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.dailynote.entity.DailyNoteEntity;

/**
 * 小纸条 DAO。
 * 基础 CRUD 由 MyBatis-Plus 提供；小纸条为物理删除，无需 deleted 过滤。
 */
@Mapper
public interface DailyNoteDao extends BaseDao<DailyNoteEntity> {
}
