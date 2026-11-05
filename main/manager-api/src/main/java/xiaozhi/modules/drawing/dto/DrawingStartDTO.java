package xiaozhi.modules.drawing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 开局请求：requestId 在网络重试中保持不变，宠物只作为首次参与门槛。 */
@Data
public class DrawingStartDTO {
    @NotBlank @Size(max = 64) private String petId;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") private String requestId;
}
