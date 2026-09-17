package xiaozhi.modules.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * AI生图任务（用户照片 + IP参考图 + 预置文案 → 合成图）
 */
@Data
@TableName("ai_image_task")
@Schema(description = "AI生图任务")
public class ImageGenTaskEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "归属用户ID")
    private Long userId;

    @Schema(description = "宠物ID")
    private String petId;

    @Schema(description = "用户照片OSS URL")
    private String photoUrl;

    @Schema(description = "IP参考图URL快照")
    private String ipImageUrl;

    @Schema(description = "抽中的预置文案快照")
    private String caption;

    @Schema(description = "状态: PENDING/RUNNING/REVIEWING/SUCCEEDED/FAILED")
    private String status;

    @Schema(description = "结果图OSS URL")
    private String resultUrl;

    @Schema(description = "用户可读失败原因")
    private String failReason;

    @Schema(description = "照片mediaCheckAsync的trace_id")
    private String photoTraceId;

    @Schema(description = "结果图mediaCheckAsync的trace_id")
    private String resultTraceId;

    @Schema(description = "是否计入每日配额: 0否 1是(成功才计次)")
    private Integer counted;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.UPDATE)
    private Long updater;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private Date updateDate;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
}
