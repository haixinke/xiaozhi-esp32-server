package xiaozhi.modules.invite.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.invite.entity.InviteCodeEntity;

@Mapper
public interface InviteCodeDao extends BaseMapper<InviteCodeEntity> {

    @Select("SELECT * FROM ai_invite_code WHERE code = #{code} FOR UPDATE")
    InviteCodeEntity selectByCodeForUpdate(@Param("code") String code);

    @Select("SELECT * FROM ai_invite_code WHERE id = #{id} FOR UPDATE")
    InviteCodeEntity selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE ai_invite_code SET used_count = used_count + 1, remaining = remaining - 1, "
            + "update_date = NOW() WHERE id = #{id} AND remaining > 0")
    int decrementRemaining(@Param("id") Long id);
}
