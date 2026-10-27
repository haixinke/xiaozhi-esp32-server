package xiaozhi.modules.smallobject.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 小物件状态切换 DTO，仅用于「启用/停用」。
 * 刻意独立于 SmallObjectDTO：列表内切换状态只提交 id 与目标状态，不回传图片与故事，
 * 避免把整条记录（含最长 2000 字的故事）回传并在并发下用陈旧副本覆盖他人刚改过的内容。
 */
@Data
@Schema(description = "小物件状态切换")
public class SmallObjectStatusDTO implements Serializable {

    @Schema(description = "id")
    @NotNull(message = "{id.require}")
    private Long id;

    @Schema(description = "目标状态：1 启用，0 停用")
    @NotNull(message = "{smallObject.status.require}")
    private Integer status;
}
