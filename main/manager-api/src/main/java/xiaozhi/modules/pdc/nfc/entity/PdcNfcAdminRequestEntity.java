package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_admin_request")
public class PdcNfcAdminRequestEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String operationType;

    private String requestId;

    private String requestFingerprint;

    private String responseJson;

    private String status;

    private Long operatorUserId;

    private Date createDate;

    private Date updateDate;
}
