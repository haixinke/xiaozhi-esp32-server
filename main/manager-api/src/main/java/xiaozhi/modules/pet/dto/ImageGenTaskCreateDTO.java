package xiaozhi.modules.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI生图任务创建请求
 */
@Data
@Schema(description = "AI生图任务创建请求")
public class ImageGenTaskCreateDTO {

    @NotBlank(message = "照片地址不能为空")
    @Schema(description = "用户照片 OSS URL（经 /upload/image scene=ai-gen 上传获得）")
    private String photoUrl;

    @NotBlank(message = "宠物ID不能为空")
    @Schema(description = "目标宠物ID（写真正主，必须属于当前用户且未删除）")
    private String petId;
}
