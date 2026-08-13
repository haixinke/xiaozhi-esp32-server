package xiaozhi.modules.storyengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "故事引擎-动作图片配文与标签修改请求")
public class ActionImageUpdateDTO {

    @NotBlank(message = "图片ID不能为空")
    @Schema(description = "图片ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "图片配文，多句用|分隔；传空表示清空配文")
    private String captions;

    @Size(max = 64, message = "标签长度不能超过64字符")
    @Schema(description = "图片标签(管理端分类标注,单标签)；传空表示清空标签")
    private String tag;
}
