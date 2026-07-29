package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_batch")
public class PdcNfcBatchEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String batchNo;

    private Long productTypeId;

    private String skuCode;

    private String prototype;

    private Integer plannedQuantity;

    private String status;

    private String remark;

    private Long creator;

    private Date createDate;

    private Long updater;

    private Date updateDate;
}
