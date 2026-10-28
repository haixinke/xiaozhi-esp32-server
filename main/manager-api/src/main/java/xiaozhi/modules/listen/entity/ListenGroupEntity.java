package xiaozhi.modules.listen.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 题目分组实体，对应 ai_listen_group 表。
 * 题目分组是「听你说题库」导入与编辑的原子单位：智控台按组整体展示、整组提交保存，
 * 一组固定含 5 道单选题。组名在同一题目大类（字典 LISTEN_CATEGORY）内唯一。
 * <p>
 * 无 deleted 字段：题库不做软删，删除即物理删除（ADR 0006 软删惯例的例外，见 ADR 0010）——
 * 题目没有用户侧历史记录关联，软删要保护的对象不存在。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_listen_group")
public class ListenGroupEntity extends BaseEntity {

    /**
     * 题目大类，存字典 LISTEN_CATEGORY 的 dict_value；名称展示时由字典实时翻译
     */
    private String categoryCode;

    /**
     * 题目分组名称，同一大类内唯一
     */
    private String name;

    /**
     * 排序序号，越小越靠前；新分组接在该大类现有最大值之后追加。
     * 列名为 sort_order（order 是 MySQL 保留字，需反引号且实体需额外转义），
     * 本仓库同类排序字段统一用 sort_order（见 storyengine 相关表）。
     */
    private Integer sortOrder;

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