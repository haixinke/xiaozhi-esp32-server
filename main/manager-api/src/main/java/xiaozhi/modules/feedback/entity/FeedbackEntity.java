package xiaozhi.modules.feedback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户反馈实体：记录蛋宝宝小程序用户提交的产品反馈
 */
@Data
@TableName("ai_feedback")
@Schema(description = "用户反馈")
public class FeedbackEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "提交用户ID")
    private Long userId;

    @Schema(description = "诉求类型（字典 EGG_FEEDBACK_TYPE 的 dict_value）")
    private String type;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "处理状态：0-未处理 1-已处理")
    private Integer status;

    @Schema(description = "运营处理备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建者")
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createDate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新者")
    private Long updater;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private Date updateDate;
}
