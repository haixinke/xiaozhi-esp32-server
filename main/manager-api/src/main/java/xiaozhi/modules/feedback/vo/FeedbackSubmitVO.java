package xiaozhi.modules.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户反馈提交结果：返回受理编号供用户追溯
 */
@Data
@Schema(description = "用户反馈提交结果")
public class FeedbackSubmitVO {

    @Schema(description = "受理编号，格式 FB<yyyyMMdd>-<6位序列>")
    private String receiptNumber;

    @Schema(description = "提交时间")
    private Date createDate;
}
