package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_product_type")
public class PdcNfcProductTypeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String typeCode;

    private String typeName;

    private String claimPagePath;

    private String capabilityMode;

    private String status;

    private Long creator;

    private Date createDate;

    private Long updater;

    private Date updateDate;
}
