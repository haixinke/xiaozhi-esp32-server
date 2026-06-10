package xiaozhi.modules.subscription.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Schema(description = "用户当前权益")
public class EntitlementVO {

    @Schema(description = "是否有生效中的订阅")
    private Boolean active;

    @Schema(description = "档位编码（无订阅为null）")
    private String planCode;

    @Schema(description = "拥有的能力点")
    private List<String> features;

    @Schema(description = "到期时间")
    private Date endAt;
}
