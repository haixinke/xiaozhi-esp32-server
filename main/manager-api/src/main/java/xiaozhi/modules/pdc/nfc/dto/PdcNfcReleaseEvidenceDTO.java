package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdcNfcReleaseEvidenceDTO {

    @NotNull(message = "商品类型ID不能为空")
    private Long productTypeId;

    @NotBlank(message = "证据类型不能为空")
    private String evidenceType;

    @NotBlank(message = "证据内容不能为空")
    private String evidenceContent;
}
