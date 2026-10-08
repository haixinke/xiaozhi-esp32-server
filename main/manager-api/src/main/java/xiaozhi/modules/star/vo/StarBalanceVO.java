package xiaozhi.modules.star.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "星星罐余额")
public class StarBalanceVO {

    @Schema(description = "星星余额")
    private Long balance;

    public static StarBalanceVO of(long balance) {
        StarBalanceVO vo = new StarBalanceVO();
        vo.setBalance(balance);
        return vo;
    }
}
