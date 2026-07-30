package xiaozhi.modules.pdc.nfc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * NFC 商品类型实体。
 * <p>
 * 定义不同 NFC 产品的类型，每种类型对应特定的微信配置和领取路径。
 * 商品类型的业务字段通过数据库初始化脚本设置，不提供 CRUD 入口。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pdc_nfc_product_type")
public class PdcNfcProductTypeEntity {

    /** 主键 ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 类型编码，全局唯一（如 EGG_STANDARD） */
    private String typeCode;

    /** 类型名称（如“蛋宝宝标准版”） */
    private String typeName;

    /** 微信小程序领取页路径（如 /pages/nfc-claim/nfc-claim） */
    private String claimPagePath;

    /** 能力模式（如 BASIC / PREMIUM） */
    private String capabilityMode;

    /** 状态（ACTIVE / INACTIVE） */
    private String status;

    /** 创建人 ID */
    private Long creator;

    /** 创建时间 */
    private Date createDate;

    /** 更新人 ID */
    private Long updater;

    /** 更新时间 */
    private Date updateDate;
}
