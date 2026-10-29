package xiaozhi.modules.dailynote.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.common.entity.BaseEntity;

/**
 * 小纸条实体，对应 ai_daily_note 表。
 * 小纸条是纯运营的每日内容：一个完整日期只有一份（note_date 全局唯一，不按年循环）。
 * <p>
 * 无 deleted 字段：小纸条不做软删，删除即物理删除（ADR 0006 软删惯例的例外，
 * 沿用 ADR 0010 对纯运营内容的决策方式）——纯内容记录没有用户侧历史关联需要软删保护。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_daily_note")
public class DailyNoteEntity extends BaseEntity {

    /**
     * 完整日期（数据库 DATE 语义），全局唯一
     */
    private Date noteDate;

    /**
     * 标题，去首尾空白后 1-50 个 Unicode 字符
     */
    private String title;

    /**
     * 正文，去首尾空白后 1-200 个 Unicode 字符；内部换行保留，按纯文本处理
     */
    private String content;

    /**
     * 节气（可选，任意文案，最多 50 个 Unicode 字符；不限制为标准二十四节气）
     */
    private String solarTerm;

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
