package xiaozhi.modules.wechat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天记录导出请求
 */
@Data
@Schema(description = "聊天记录导出请求")
public class ChatHistoryExportReqDTO {

    @Schema(description = "接收导出文件的邮箱")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
