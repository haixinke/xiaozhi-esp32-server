package xiaozhi.modules.pdc.nfc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import xiaozhi.modules.pdc.nfc.entity.PdcNfcBatchEntity;

import java.util.Date;

@Mapper
public interface PdcNfcBatchDao extends BaseMapper<PdcNfcBatchEntity> {

    /**
     * 原子状态翻转：仅当批次当前状态等于 from 时才更新为 to。
     * 影响行数为 0 表示状态已被并发请求改变，调用方应视为冲突。
     * 用于写卡任务创建等需要"检查状态并推进"的场景，避免 check-then-act 竞态。
     */
    @Update("UPDATE pdc_nfc_batch " +
            "SET status = #{to}, updater = #{operatorId}, update_date = #{now} " +
            "WHERE id = #{batchId} AND status = #{from}")
    int transitionStatus(@Param("batchId") Long batchId,
                         @Param("from") String from,
                         @Param("to") String to,
                         @Param("operatorId") Long operatorId,
                         @Param("now") Date now);
}
