package xiaozhi.modules.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI生图任务视图对象
 */
@Data
@Schema(description = "AI生图任务")
public class ImageGenTaskVO {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "状态: PENDING照片审核中/RUNNING生成中/REVIEWING结果审核中/SUCCEEDED成功/FAILED失败")
    private String status;

    @Schema(description = "结果图URL（SUCCEEDED 时有值）")
    private String resultUrl;

    @Schema(description = "失败原因（FAILED 时有值）")
    private String failReason;

    @Schema(description = "抽中的预置文案")
    private String caption;
}
