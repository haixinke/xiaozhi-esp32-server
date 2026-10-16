package xiaozhi.modules.game.vo;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 游戏配置 VO，用于管理端列表和详情展示。
 */
@Data
@Schema(description = "游戏配置VO")
public class GameVO implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "游戏类型")
    private String gameType;

    @Schema(description = "赚取星星数")
    private Integer earnStars;

    @Schema(description = "消耗星星数")
    private Integer consumeStars;

    @Schema(description = "每日次数上限")
    private Integer dailyLimit;

    @Schema(description = "图标 OSS URL")
    private String icon;

    @Schema(description = "游戏说明")
    private String description;

    @Schema(description = "游戏入口")
    private String entrances;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "创建者")
    private Long creator;

    @Schema(description = "创建时间")
    private Date createDate;

    @Schema(description = "更新者")
    private Long updater;

    @Schema(description = "更新时间")
    private Date updateDate;
}
