package xiaozhi.modules.listen.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 答题记录实体，对应 ai_listen_answer 表。
 * 用户对某道题的一次作答：所选选项（A-E）+ 可选自定义文本（选 E「都不是」时可填）。
 * 幂等由数据库兜底：UNIQUE KEY (user_id, question_id)，重复提交同一题不产生第二行。
 * <p>
 * 无 deleted 字段：答题记录与题库一致物理删除（ADR 0010 听你说惯例）。
 * 不建外键：题库题目/分组物理删除后答案仍保留（用户历史数据），查询只 join 存活题目。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_listen_answer")
public class ListenAnswerEntity extends BaseEntity {

    /**
     * 答题用户 ID，对应 sys_user.id
     */
    private Long userId;

    /**
     * 所属题目分组 ID（冗余列）：可由 question_id join 推出，
     * 但抽题按组排除是热路径，冗余避免 join
     */
    private Long groupId;

    /**
     * 题目 ID，对应 ai_listen_question.id
     */
    private Long questionId;

    /**
     * 所选选项，A-E
     */
    private String optionKey;

    /**
     * 自定义文本答案：选 E「都不是」时可填，非强制，最长 200 字
     */
    private String customText;

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
