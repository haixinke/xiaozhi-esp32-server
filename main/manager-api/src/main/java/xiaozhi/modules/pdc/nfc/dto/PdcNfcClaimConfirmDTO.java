package xiaozhi.modules.pdc.nfc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PdcNfcClaimConfirmDTO {

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{22}")
    private String claimRef;

    @NotBlank
    private String requestId;
}
