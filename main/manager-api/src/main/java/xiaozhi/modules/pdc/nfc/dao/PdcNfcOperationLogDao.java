package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcOperationLogEntity;

@Mapper
public interface PdcNfcOperationLogDao extends BaseMapper<PdcNfcOperationLogEntity> {

    @Select("SELECT * FROM pdc_nfc_operation_log " +
            "WHERE object_type = 'NFC_RELEASE' " +
            "AND operation_type = 'RELEASE_EVIDENCE' " +
            "AND source = 'ADMIN_API' " +
            "AND result = 'SUCCESS' " +
            "AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.releaseVersion')) = #{releaseVersion} " +
            "ORDER BY create_date DESC LIMIT 1")
    PdcNfcOperationLogEntity selectLatestSuccessfulReleaseEvidence(
            @Param("releaseVersion") String releaseVersion);
}
