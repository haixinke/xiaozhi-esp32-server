package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PdcNfcAssetDao extends BaseMapper<PdcNfcAssetEntity> {

    List<PdcNfcAssetEntity> selectByClaimHashesForUpdate(
            @Param("hashes") Collection<String> hashes);

    List<PdcNfcAssetEntity> selectByIdsForUpdate(
            @Param("ids") List<Long> sortedIds);
}
