package xiaozhi.modules.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户反馈管理端视图：列表与详情共用
 */
@Data
@Schema(description = "用户反馈管理端视图")
public class FeedbackAdminVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "受理编号，格式 FB<yyyyMMdd>-<6位序列>")
    private String receiptNumber;

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

    @Schema(description = "提交时间")
    private Date createDate;

    @Schema(description = "更新时间")
    private Date updateDate;
}
