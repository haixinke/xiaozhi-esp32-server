package xiaozhi.modules.listen.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 题目实体，对应 ai_listen_question 表。
 * 一道单选题 = 题干 + 5 个选项；选项无独立生命周期（不单独建表），
 * 以 JSON 列 optionsJson 随题目存储，格式 {@code [{"key":"A","text":"..."}]}。
 * 题干在同一题目分组内唯一。
 * <p>
 * 无 deleted 字段：题库物理删除（ADR 0006 软删惯例的例外，见 ADR 0010）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_listen_question")
public class ListenQuestionEntity extends BaseEntity {

    /**
     * 所属题目分组 ID，对应 ai_listen_group.id
     */
    private Long groupId;

    /**
     * 题干，同一分组内唯一
     */
    private String text;

    /**
     * 题目类型，当前仅 SINGLE（单选）；扩展类型待游戏立项后评估
     */
    private String type;

    /**
     * 选项列表 JSON，格式 {@code [{"key":"A","text":"..."}]}，固定 5 项 A-E
     */
    private String optionsJson;

    /**
     * 组内排序序号，越小越靠前；新题接在组内末尾追加（列名 sort_order，避免 MySQL 保留字 order）
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