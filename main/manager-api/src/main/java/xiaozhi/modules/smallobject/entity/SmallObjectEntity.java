package xiaozhi.modules.smallobject.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 小物件实体，对应 ai_small_object 表。
 * 小物件是纯内容记录（一张图 + 一段故事），不保存名称：名称由字典 SMALL_OBJECT_TYPE
 * 的 dict_label 运行时翻译，改字典即刻生效，两处不会出现名字不一致。
 * 与「道具」(item 模块的库存/消耗语义) 无关，勿混用。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_small_object")
public class SmallObjectEntity extends BaseEntity {

    /**
     * 物件类型，存字典 SMALL_OBJECT_TYPE 的 dict_value
     */
    private String objectType;

    /**
     * 物件图片完整 OSS URL
     */
    private String imageUrl;

    /**
     * 物件背后的故事，纯文本，最长 2000 字
     */
    private String story;

    /**
     * 状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 删除标记：0 未删除，1 已删除。
     * 不使用 MyBatis-Plus @TableLogic（全仓库无该用法，ADR 0006 已否决），
     * 由 Service 显式按 deleted = 0 过滤。
     */
    private Integer deleted;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateDate;
}