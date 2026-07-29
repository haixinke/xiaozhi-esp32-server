package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcClaimRecordEntity;

import java.util.Optional;

@Mapper
public interface PdcNfcClaimRecordDao extends BaseMapper<PdcNfcClaimRecordEntity> {

    @Select("SELECT * FROM pdc_nfc_claim_record WHERE user_id = #{userId} AND request_id = #{requestId} LIMIT 1")
    Optional<PdcNfcClaimRecordEntity> findByUserAndRequest(
            @Param("userId") Long userId, @Param("requestId") String requestId);
}
