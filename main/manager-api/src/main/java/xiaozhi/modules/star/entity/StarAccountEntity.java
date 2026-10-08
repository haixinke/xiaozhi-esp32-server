package xiaozhi.modules.star.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 星星罐账户：用户级虚拟货币余额，一人一行。
 * 整数星星、永不过期、禁止负余额；扣减走 StarAccountDao 原子条件 UPDATE。
 */
@Data
@TableName("ai_star_account")
@Schema(description = "星星罐账户")
public class StarAccountEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID(sys_user.id)")
    private Long userId;

    @Schema(description = "星星余额")
    private Long balance;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建者")
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createDate;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新者")
    private Long updater;

    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新时间")
    private Date updateDate;
}
