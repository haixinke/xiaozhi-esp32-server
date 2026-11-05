package xiaozhi.modules.drawing.dao;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import xiaozhi.modules.drawing.entity.DrawingSessionEntity;

/** 局次查询；所有写操作先锁用户，避免同用户并发开局绕过次数与幂等限制。 */
@Mapper
public interface DrawingSessionDao extends BaseMapper<DrawingSessionEntity> {
    @Select("SELECT id FROM sys_user WHERE id = #{userId} FOR UPDATE")
    Long lockUser(@Param("userId") Long userId);

    @Select("SELECT * FROM ai_drawing_session WHERE user_id = #{userId} AND request_id = #{requestId} FOR UPDATE")
    DrawingSessionEntity findRequest(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("SELECT * FROM ai_drawing_session WHERE user_id = #{userId} AND id = #{id} FOR UPDATE")
    DrawingSessionEntity findOwned(@Param("userId") Long userId, @Param("id") String id);

    @Select("SELECT COUNT(*) FROM ai_drawing_session WHERE user_id = #{userId} AND started_day = #{day}")
    long countDay(@Param("userId") Long userId, @Param("day") LocalDate day);
}
