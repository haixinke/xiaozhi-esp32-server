package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_scheme_attempt")
public class PdcNfcSchemeAttemptEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long jobId;

    private Long assetId;

    private Integer attemptNo;

    private String requestFingerprint;

    private String action;

    private Integer wechatErrorCode;

    private String errorMessage;

    private Date startedAt;

    private Date finishedAt;
}
