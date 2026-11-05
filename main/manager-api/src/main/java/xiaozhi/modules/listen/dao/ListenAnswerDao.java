package xiaozhi.modules.listen.dao;

import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.listen.entity.ListenAnswerEntity;

/**
 * 答题记录 DAO。
 * 基础 CRUD 由 MyBatis-Plus 提供；答题记录物理删除，无需 deleted 过滤。
 */
@Mapper
public interface ListenAnswerDao extends BaseDao<ListenAnswerEntity> {
    /** 提交事务先锁用户，避免不同题组并发绕过每日限额。 */
    @Select("SELECT id FROM sys_user WHERE id = #{userId} FOR UPDATE")
    Long lockUser(@Param("userId") Long userId);

    /** 一组提交产生多行答案；当前题组不可重玩，因此按组去重就是局次数。 */
    @Select("SELECT COUNT(DISTINCT group_id) FROM ai_listen_answer WHERE user_id = #{userId} "
            + "AND create_date >= #{start} AND create_date < #{end}")
    long countDay(@Param("userId") Long userId, @Param("start") Date start, @Param("end") Date end);
}
