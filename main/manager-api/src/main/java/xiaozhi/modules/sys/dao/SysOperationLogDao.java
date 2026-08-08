package xiaozhi.modules.sys.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.sys.entity.SysOperationLogEntity;

/**
 * 通用操作日志
 */
@Mapper
public interface SysOperationLogDao extends BaseDao<SysOperationLogEntity> {
}
