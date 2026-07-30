package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * NFC 批次实体。
 * <p>
 * 每个批次关联一个商品类型，包含计划数量的资产，
 * 生命周期：CREATED → SCHEME_GENERATING → READY_FOR_WRITE → STOCKED → COMPLETED / CANCELLED。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_batch")
public class PdcNfcBatchEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 批次编号，全局唯一 */
    private String batchNo;

    /** 关联商品类型 ID（对应 pdc_nfc_product_type.id） */
    private Long productTypeId;

    /** SKU 编码，标识产品型号 */
    private String skuCode;

    /** 原型标识，区分同一 SKU 下的不同硬件版本 */
    private String prototype;

    /** 计划生产数量 */
    private Integer plannedQuantity;

    /** 批次状态（CREATED / SCHEME_GENERATING / READY_FOR_WRITE / STOCKED / COMPLETED / CANCELLED） */
    private String status;

    /** 备注信息 */
    private String remark;

    /** 创建人 ID */
    private Long creator;

    /** 创建时间 */
    private Date createDate;

    /** 更新人 ID */
    private Long updater;

    /** 更新时间 */
    private Date updateDate;
}
