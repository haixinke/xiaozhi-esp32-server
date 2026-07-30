package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 管理操作幂等请求记录实体。
 * <p>
 * 记录每次管理操作的请求指纹和响应，用于幂等性保护。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_admin_request")
public class PdcNfcAdminRequestEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作类型（WRITE_RESULT_IMPORT / STOCK_IN / ACTIVATE / DISABLE / SCRAP） */
    private String operationType;

    /** 幂等请求 ID（UUID 字符串） */
    private String requestId;

    /** 请求指纹（规范化请求内容的哈希，用于冲突检测） */
    private String requestFingerprint;

    /** 响应 JSON（操作成功后的完整响应序列化） */
    private String responseJson;

    /** 处理状态（PENDING / SUCCESS / FAILED） */
    private String status;

    /** 操作人用户 ID */
    private Long operatorUserId;

    /** 创建时间 */
    private Date createDate;

    /** 更新时间 */
    private Date updateDate;
}
