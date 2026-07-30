package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcWriteJobEntity;

@Mapper
public interface PdcNfcWriteJobDao extends BaseMapper<PdcNfcWriteJobEntity> {

    @Select("SELECT * FROM pdc_nfc_write_job WHERE id = #{jobId} FOR UPDATE")
    PdcNfcWriteJobEntity selectByIdForUpdate(@Param("jobId") Long jobId);
}
