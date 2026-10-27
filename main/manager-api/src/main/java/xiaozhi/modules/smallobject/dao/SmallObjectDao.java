package xiaozhi.modules.smallobject.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.metadata.IPage;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.smallobject.entity.SmallObjectEntity;

/**
 * 小物件 DAO
 */
@Mapper
public interface SmallObjectDao extends BaseDao<SmallObjectEntity> {

    /**
     * 分页查询未删除的小物件，按物件类型的字典 sort 升序排列。
     * 排序在库侧完成（LEFT JOIN sys_dict_data），使列表顺序与字典管理页的小类顺序永远一致，
     * 同时保证分页真正在库侧生效，不把全表加载到内存。
     *
     * @param page     分页参数，由 MyBatis-Plus 分页插件填充
     * @param dictType 物件类型所属的字典大类编码（SMALL_OBJECT_TYPE）
     * @return 分页结果
     */
    IPage<SmallObjectEntity> selectPageOrderByDictSort(IPage<SmallObjectEntity> page,
            @Param("dictType") String dictType);
}