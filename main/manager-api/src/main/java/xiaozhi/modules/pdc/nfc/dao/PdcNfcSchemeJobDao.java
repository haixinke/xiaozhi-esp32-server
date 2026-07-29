package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcSchemeJobEntity;

import java.util.Date;
import java.util.List;

@Mapper
public interface PdcNfcSchemeJobDao extends BaseMapper<PdcNfcSchemeJobEntity> {

    /**
     * 条件租约获取：PENDING 直接转 RUNNING 并获取租约；
     * RUNNING 且租约过期则续租。保证多实例只有一个成功。
     */
    @Update("UPDATE pdc_nfc_scheme_job " +
            "SET lease_owner = #{instanceId}, lease_until = #{leaseUntil}, " +
            "heartbeat_at = #{now}, status = 'RUNNING', update_date = #{now} " +
            "WHERE id = #{jobId} " +
            "AND (status = 'PENDING' " +
            "     OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= #{now})))")
    int claimLease(@Param("jobId") Long jobId,
                   @Param("instanceId") String instanceId,
                   @Param("now") Date now,
                   @Param("leaseUntil") Date leaseUntil);

    /**
     * 查询可恢复任务：PENDING 或 RUNNING 且租约已过期。
     */
    @Select("SELECT * FROM pdc_nfc_scheme_job " +
            "WHERE status = 'PENDING' " +
            "   OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= NOW()) " +
            "       AND (next_retry_at IS NULL OR next_retry_at <= NOW())) " +
            "ORDER BY create_date ASC " +
            "LIMIT 50")
    List<PdcNfcSchemeJobEntity> selectRecoverableJobs();

    /**
     * 心跳续租：仅当租约持有者匹配且状态仍为 RUNNING 时成功。
     */
    @Update("UPDATE pdc_nfc_scheme_job " +
            "SET lease_until = #{leaseUntil}, heartbeat_at = #{now}, update_date = #{now} " +
            "WHERE id = #{jobId} AND lease_owner = #{instanceId} AND status = 'RUNNING'")
    int heartbeat(@Param("jobId") Long jobId,
                  @Param("instanceId") String instanceId,
                  @Param("now") Date now,
                  @Param("leaseUntil") Date leaseUntil);

    /**
     * 释放租约：清除 lease_owner 和 lease_until。
     */
    @Update("UPDATE pdc_nfc_scheme_job " +
            "SET lease_owner = NULL, lease_until = NULL, update_date = #{now} " +
            "WHERE id = #{jobId} AND lease_owner = #{instanceId}")
    int releaseLease(@Param("jobId") Long jobId,
                     @Param("instanceId") String instanceId,
                     @Param("now") Date now);

    /**
     * 更新游标和计数。
     */
    @Update("UPDATE pdc_nfc_scheme_job " +
            "SET cursor_asset_id = #{cursor}, success_count = #{success}, " +
            "failure_count = #{failure}, update_date = #{now} " +
            "WHERE id = #{jobId}")
    int updateProgress(@Param("jobId") Long jobId,
                       @Param("cursor") Long cursor,
                       @Param("success") int success,
                       @Param("failure") int failure,
                       @Param("now") Date now);

    /**
     * 更新任务状态（含可选 nextRetryAt / cancelledAt）。
     */
    @Update("UPDATE pdc_nfc_scheme_job " +
            "SET status = #{status}, next_retry_at = #{nextRetryAt}, cancelled_at = #{cancelledAt}, " +
            "lease_owner = NULL, lease_until = NULL, update_date = #{now} " +
            "WHERE id = #{jobId} AND status = 'RUNNING'")
    int completeJob(@Param("jobId") Long jobId,
                    @Param("status") String status,
                    @Param("nextRetryAt") Date nextRetryAt,
                    @Param("cancelledAt") Date cancelledAt,
                    @Param("now") Date now);

    /**
     * 取消任务：仅 PENDING / RUNNING 可取消。
     */
    @Update("UPDATE pdc_nfc_scheme_job " +
            "SET status = 'CANCELLED', cancelled_at = #{now}, " +
            "lease_owner = NULL, lease_until = NULL, update_date = #{now} " +
            "WHERE id = #{jobId} AND status IN ('PENDING', 'RUNNING')")
    int cancelJob(@Param("jobId") Long jobId, @Param("now") Date now);

    /**
     * 查询批次最新 Scheme 任务。
     */
    @Select("SELECT * FROM pdc_nfc_scheme_job WHERE batch_id = #{batchId} " +
            "ORDER BY create_date DESC LIMIT 1")
    PdcNfcSchemeJobEntity selectLatestByBatchId(@Param("batchId") Long batchId);
}
