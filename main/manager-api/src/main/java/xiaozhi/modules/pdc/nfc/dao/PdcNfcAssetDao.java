package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PdcNfcAssetDao extends BaseMapper<PdcNfcAssetEntity> {

    @Insert({"<script>",
            "INSERT INTO pdc_nfc_asset (id, asset_no, batch_id, item_no, sku_code, prototype, wechat_sn, ",
            "claim_ref_hash, claim_ref_hash_version, claim_ref_key_version, claim_ref_nonce, claim_ref_ciphertext, ",
            "status, version, creator, create_date) VALUES ",
            "<foreach collection='assets' item='a' separator=','>",
            "(#{a.id}, #{a.assetNo}, #{a.batchId}, #{a.itemNo}, #{a.skuCode}, #{a.prototype}, #{a.wechatSn}, ",
            "#{a.claimRefHash}, #{a.claimRefHashVersion}, #{a.claimRefKeyVersion}, #{a.claimRefNonce}, #{a.claimRefCiphertext}, ",
            "#{a.status}, #{a.version}, #{a.creator}, #{a.createDate})",
            "</foreach>",
            "</script>"})
    void insertBatch(@Param("assets") Collection<PdcNfcAssetEntity> assets);

    List<PdcNfcAssetEntity> selectByClaimHashesForUpdate(
            @Param("hashes") Collection<String> hashes);

    List<PdcNfcAssetEntity> selectByIdsForUpdate(
            @Param("ids") List<Long> sortedIds);
}
