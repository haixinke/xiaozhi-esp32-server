package xiaozhi.modules.pdc.nfc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 免检放行请求（ADR 0005）：代工厂未回传结果 CSV 时，
 * 操作员抽检 + 锁卡人工声明后批量信任推进写卡任务内资产。
 */
@Data
@Schema(description = "免检放行请求")
public class PdcNfcTrustReleaseDTO {

    /**
     * 锁卡人工声明：操作员确认代工厂已完成锁卡。
     * 免检通道不提供锁卡文件证据，该声明是唯一的锁卡保障，必须显式为 true。
     */
    @NotNull
    @Schema(description = "确认代工厂已完成锁卡（必须为 true）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean lockConfirmed;

    @NotNull
    @Schema(description = "幂等请求 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID requestId;
}
