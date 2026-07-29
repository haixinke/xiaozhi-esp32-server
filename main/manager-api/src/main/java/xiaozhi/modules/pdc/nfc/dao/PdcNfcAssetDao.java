package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcAssetEntity;

import java.util.Collection;
import java.util.Date;
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

    @Select("SELECT * FROM pdc_nfc_asset " +
            "WHERE batch_id = #{batchId} AND status = 'CREATED' AND id > #{cursor} " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<PdcNfcAssetEntity> selectCreatedAssetsAfterCursor(
            @Param("batchId") Long batchId,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    @Update("UPDATE pdc_nfc_asset " +
            "SET scheme_key_version = #{keyVersion}, scheme_nonce = #{nonce}, " +
            "scheme_ciphertext = #{ciphertext}, scheme_sha256 = #{sha256}, " +
            "status = 'SCHEME_GENERATED', scheme_generated_at = #{now}, " +
            "active_scheme_job_id = #{jobId}, update_date = #{now}, version = version + 1 " +
            "WHERE id = #{assetId} AND status = 'CREATED'")
    int markSchemeGenerated(@Param("assetId") Long assetId,
                           @Param("keyVersion") String keyVersion,
                           @Param("nonce") byte[] nonce,
                           @Param("ciphertext") byte[] ciphertext,
                           @Param("sha256") String sha256,
                           @Param("jobId") Long jobId,
                           @Param("now") Date now);

    @Update("UPDATE pdc_nfc_asset SET active_scheme_job_id = #{jobId} " +
            "WHERE batch_id = #{batchId} AND status = 'CREATED'")
    int assignJobToCreatedAssets(@Param("batchId") Long batchId, @Param("jobId") Long jobId);

    @Update("UPDATE pdc_nfc_asset SET active_scheme_job_id = NULL " +
            "WHERE batch_id = #{batchId} AND status = 'CREATED' AND active_scheme_job_id = #{jobId}")
    int releaseAssetsForJob(@Param("batchId") Long batchId, @Param("jobId") Long jobId);

    @Select("SELECT COUNT(*) FROM pdc_nfc_asset WHERE batch_id = #{batchId} AND status = 'CREATED'")
    int countCreatedAssets(@Param("batchId") Long batchId);

    @Update("UPDATE pdc_nfc_asset SET status = 'CLAIMED', claimed_user_id = #{userId}, " +
            "pet_id = #{petId}, claimed_at = NOW(), version = version + 1 " +
            "WHERE id = #{id} AND version = #{version} AND status = 'ACTIVE'")
    int markClaimed(@Param("id") Long id, @Param("version") Integer version,
                    @Param("userId") Long userId, @Param("petId") String petId);
}
