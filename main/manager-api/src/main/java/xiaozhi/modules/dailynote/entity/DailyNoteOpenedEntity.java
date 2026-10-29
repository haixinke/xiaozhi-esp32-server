package xiaozhi.modules.dailynote.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户已打开记录实体，对应 ai_daily_note_opened 表。
 * 已打开状态按用户+完整日期隔离（user_id+note_date 联合唯一）：换设备生效、次日不继承。
 * <p>
 * 不保存 note_id、不建外键：小纸条内容覆盖、物理删除或同日重建均不影响该记录，
 * 已打开记录永久保留（本期不提供后台查看、撤销、重置或清理）。
 * 不继承 BaseEntity：BaseEntity 的 creator 会按当前登录用户自动填充，
 * 而已打开记录的归属语义就是 user_id，再存一份 creator 只会造成歧义。
 */
@Data
@TableName("ai_daily_note_opened")
public class DailyNoteOpenedEntity {

    /**
     * 主键（雪花 ID，非自增）
     */
    @TableId
    private Long id;

    /**
     * 用户 ID（后台用户与微信用户主键均为 BIGINT）
     */
    private Long userId;

    /**
     * 小纸条完整日期（数据库 DATE 语义，不跨年复用）
     */
    private Date noteDate;

    /**
     * 首次打开时间；重复上报成功但不更新该值
     */
    private Date openedAt;

    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
